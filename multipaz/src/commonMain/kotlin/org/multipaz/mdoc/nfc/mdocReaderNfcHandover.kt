package org.multipaz.mdoc.nfc

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Simple
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodBle
import org.multipaz.nfc.HandoverRequestRecord
import org.multipaz.nfc.HandoverSelectRecord
import org.multipaz.nfc.NdefMessage
import org.multipaz.nfc.NdefRecord
import org.multipaz.nfc.Nfc
import org.multipaz.nfc.NfcCommandFailedException
import org.multipaz.nfc.NfcIsoTag
import org.multipaz.nfc.ServiceParameterRecord
import org.multipaz.nfc.ServiceSelectRecord
import org.multipaz.nfc.TnepStatusRecord
import org.multipaz.util.Logger
import org.multipaz.util.UUID
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.decodeToString
import kotlinx.io.bytestring.encodeToByteString
import org.multipaz.cbor.buildCborArray
import org.multipaz.cbor.buildCborMap
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.util.getUInt16

const private val TAG = "mdocReaderNfcHandover"

/**
 * The result of a successful NFC handover operation
 *
 * @property connectionMethods the possible connection methods for the mdoc reader to connect to.
 * @property encodedDeviceEngagement the bytes of DeviceEngagement.
 * @property handover the handover value.
 */
data class MdocReaderNfcHandoverResult(
    val connectionMethods: List<MdocConnectionMethod>,
    val encodedDeviceEngagement: ByteString,
    val handover: DataItem,
)

/**
 * Perform NFC Engagement as a mdoc reader.
 *
 * @param tag the [NfcIsoTag] representing a NFC connection to the mdoc.
 * @param negotiatedHandoverConnectionMethods the connection methods to offer if the remote mdoc is using NFC
 * negotiated handover.
 * @return a [MdocReaderNfcHandoverResult] if the handover was successful or `null` if the tag isn't an NDEF tag.
 * @throws Throwable if an error occurs during handover.
 */
suspend fun mdocReaderNfcHandover(
    tag: NfcIsoTag,
    negotiatedHandoverConnectionMethods: List<MdocConnectionMethod>,
): MdocReaderNfcHandoverResult? {
    try {
        tag.selectApplication(Nfc.NDEF_APPLICATION_ID)
    } catch (e: NfcCommandFailedException) {
        // This is returned by Android when locked phone is being tapped by an mdoc reader. Once unlocked the
        // user will be shown UI to convey another tap should happen. So since we're the mdoc reader, we
        // want to keep scanning...
        //
        if (e.status == Nfc.RESPONSE_STATUS_ERROR_FILE_OR_APPLICATION_NOT_FOUND) {
            Logger.i(TAG, "NDEF application not found, continuing scanning")
            return null
        }
    }
    tag.selectFile(Nfc.NDEF_CAPABILITY_CONTAINER_FILE_ID)
    // CC file is 15 bytes long
    val ccFile = tag.readBinary(0, 15)
    check(ccFile.size == 15) { "CC file is ${ccFile.size} bytes, expected 15" }

    val ndefFileId = ccFile.getUInt16(9).toInt()

    tag.selectFile(ndefFileId)

    val initialNdefMessage = tag.ndefReadMessage()

    // First see if we should use negotiated handover by looking for the Handover Service parameter record...
    val hspr = initialNdefMessage.records.mapNotNull {
        val parsed = ServiceParameterRecord.fromNdefRecord(it)
        if (parsed?.serviceNameUri == "urn:nfc:sn:handover") parsed else null
    }.firstOrNull()
    if (hspr == null) {
        val (encodedDeviceEngagement, connectionMethods) = parseHandoverSelectMessage(initialNdefMessage, null)
        check(!connectionMethods.isEmpty()) { "No connection methods in Handover Select" }
        val handover = buildCborArray {
            add(initialNdefMessage.encode()) // Handover Select message
            add(Simple.NULL)                 // Handover Request message
        }
        val disambiguatedConnectionMethods = MdocConnectionMethod.disambiguate(
            connectionMethods,
            MdocRole.MDOC_READER
        )
        return MdocReaderNfcHandoverResult(
            connectionMethods = disambiguatedConnectionMethods,
            encodedDeviceEngagement = ByteString(encodedDeviceEngagement),
            handover = handover,
        )
    }

    // Select the service, the resulting NDEF message is specified in
    // in Tag NDEF Exchange Protocol Technical Specification Version 1.0
    // section 4.3 TNEP Status Message
    val serviceSelectionResponse = tag.ndefTransact(
        NdefMessage(listOf(
            ServiceSelectRecord(Nfc.SERVICE_NAME_CONNECTION_HANDOVER).toNdefRecord()
        )),
        hspr.wtInt,
        hspr.nWait
    )

    val tnepStatusRecord = serviceSelectionResponse.records.find { TnepStatusRecord.fromNdefRecord(it) != null }
        ?: throw IllegalArgumentException("Service selection: no TNEP status record")
    val tnepStatus = TnepStatusRecord.fromNdefRecord(tnepStatusRecord)!!
    require(tnepStatus.status == 0x00) { "Service selection: Unexpected status ${tnepStatus.status}" }

    // Now send Handover Request message, the resulting NDEF message is Handover Response..
    //
    val combinedNegotiatedHandoverConnectionMethods = MdocConnectionMethod.combine(negotiatedHandoverConnectionMethods)
    val hrMessage = generateHandoverRequestMessage(combinedNegotiatedHandoverConnectionMethods)
    val hsMessage = tag.ndefTransact(hrMessage, hspr.wtInt, hspr.nWait)

    var bleUuid: UUID? = null
    for (cm in negotiatedHandoverConnectionMethods) {
        if (cm is MdocConnectionMethodBle) {
            if (cm.peripheralServerModeUuid != null) {
                bleUuid = cm.peripheralServerModeUuid
                break
            }
            if (cm.centralClientModeUuid != null) {
                bleUuid = cm.centralClientModeUuid
                break
            }
        }
    }
    Logger.i(TAG, "Supplementing with UUID $bleUuid")
    val (encodedDeviceEngagement, connectionMethods) = parseHandoverSelectMessage(hsMessage, bleUuid)
    check(connectionMethods.size >= 1) { "No Alternative Carriers in HS message" }

    val handover = buildCborArray {
        add(hsMessage.encode()) // Handover Select message
        add(hrMessage.encode()) // Handover Request message
    }

    return MdocReaderNfcHandoverResult(
        connectionMethods = MdocConnectionMethod.disambiguate(
            connectionMethods,
            MdocRole.MDOC_READER
        ),
        encodedDeviceEngagement = ByteString(encodedDeviceEngagement),
        handover = handover,
    )
}

private fun generateHandoverRequestMessage(
    methods: List<MdocConnectionMethod>,
): NdefMessage {
    val auxiliaryReferences = listOf<String>()
    val carrierConfigurationRecords = mutableListOf<NdefRecord>()
    val alternativeCarrierRecords = mutableListOf<NdefRecord>()
    for (method in methods) {
        val ndefRecordAndAlternativeCarrier = method.toNdefRecord(
            auxiliaryReferences = auxiliaryReferences,
            role = MdocRole.MDOC_READER,
            skipUuids = false
        )
        if (ndefRecordAndAlternativeCarrier != null) {
            carrierConfigurationRecords.add(ndefRecordAndAlternativeCarrier.first)
            alternativeCarrierRecords.add(ndefRecordAndAlternativeCarrier.second)
        }
    }
    val handoverRequestRecord = HandoverRequestRecord(
        version = 0x15,
        embeddedMessage = NdefMessage(alternativeCarrierRecords)
    )
    // TODO: make it possible for caller to specify readerEngagement
    val encodedReaderEngagement = Cbor.encode(
        buildCborMap {
            put(0L, "1.0")
        }
    )
    return NdefMessage(
        listOf(
            handoverRequestRecord.generateNdefRecord(),
            NdefRecord(
                tnf = NdefRecord.Tnf.EXTERNAL_TYPE,
                type = "iso.org:18013:readerengagement".encodeToByteString(),
                id = "mdocreader".encodeToByteString(),
                payload = ByteString(encodedReaderEngagement)
            )
        ) + carrierConfigurationRecords
    )
}

@OptIn(ExperimentalStdlibApi::class)
private fun parseHandoverSelectMessage(
    message: NdefMessage,
    uuid: UUID?,
): Pair<ByteArray, List<MdocConnectionMethod>> {
    var hasHandoverSelectRecord = false

    var encodedDeviceEngagement: ByteString? = null
    val connectionMethods = mutableListOf<MdocConnectionMethod>()
    for (r in message.records) {
        // Handle Handover Select record for NFC Forum Connection Handover specification
        // version 1.5 (encoded as 0x15 below).
        val hsRecord = HandoverSelectRecord.fromNdefRecord(r)
        if (hsRecord != null) {
            check(hsRecord.version == 0x15) { "Only Connection Handover version 1.5 is supported" }
            hasHandoverSelectRecord = true
        }
        if (r.tnf == NdefRecord.Tnf.EXTERNAL_TYPE &&
            r.type.decodeToString() == "iso.org:18013:deviceengagement" &&
            r.id.decodeToString() == "mdoc") {
            encodedDeviceEngagement = r.payload
        }

        if (r.tnf == NdefRecord.Tnf.MIME_MEDIA || r.tnf == NdefRecord.Tnf.EXTERNAL_TYPE) {
            val cm = MdocConnectionMethod.fromNdefRecord(r, MdocRole.MDOC, uuid)
            if (cm != null) {
                connectionMethods.add(cm)
            }
        }
    }
    if (!hasHandoverSelectRecord) {
        throw Error("Handover Select record not found")
    }
    if (encodedDeviceEngagement == null) {
        throw Error("DeviceEngagement record not found")
    }
    return Pair(encodedDeviceEngagement.toByteArray(), connectionMethods)
}
