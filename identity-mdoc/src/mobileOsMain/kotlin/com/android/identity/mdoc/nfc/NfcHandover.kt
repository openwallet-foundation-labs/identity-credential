package com.android.identity.mdoc.nfc

import com.android.identity.cbor.CborArray
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.Simple
import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import com.android.identity.mdoc.connectionmethod.ConnectionMethodBle
import com.android.identity.mdoc.transport.MdocTransport
import com.android.identity.mdoc.transport.MdocTransportFactory
import com.android.identity.mdoc.transport.MdocTransportOptions
import com.android.identity.nfc.NdefMessage
import com.android.identity.nfc.NdefRecord
import com.android.identity.nfc.Nfc
import com.android.identity.nfc.NfcIsoTag
import com.android.identity.nfc.scanNfcTag
import com.android.identity.util.Logger
import com.android.identity.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.io.bytestring.decodeToString
import kotlin.time.Duration

const private val TAG = "NfcHandover"

/**
 * Performs NFC engagement as a reader.
 *
 * @param options
 */
suspend fun startNfcEngagement(
    options: MdocTransportOptions,
    onConnected: (
        transport: MdocTransport,
        encodedDeviceEngagement: ByteArray,
        handover: DataItem,
        elapsedTime: Duration
    ) -> Unit
) {
    // Start creating transports for Negotiated Handover and start advertising these
    // immediately. This helps with connection time because the holder's device will
    // get a chance to opportunistically read the UUIDs which helps reduce scanning
    // time.
    //
    val negotiatedHandoverTransports = listOf(
        ConnectionMethodBle(
            supportsPeripheralServerMode = false,
            supportsCentralClientMode = true,
            peripheralServerModeUuid = null,
            centralClientModeUuid = UUID.randomUUID()
        )
    ).map {
        val transport = MdocTransportFactory.createTransport(
            it,
            MdocTransport.Role.MDOC_READER,
            options
        )
        transport.advertise()
        transport
    }
    // Make sure we don't leak connections...
    val transportsToClose = negotiatedHandoverTransports.toMutableList()

    try {
        scanNfcTag(
            message = "Hold your phone near the presenter's phone to request credentials.",
            tagInteractionFunc = { tag, updateMessage ->
                performNfcHandover(
                    tag = tag,
                    options = options,
                    negotiatedHandoverTransports = negotiatedHandoverTransports,
                    // TODO: for now we just pick the first transport
                    selectConnectionMethod = { connectionMethods ->
                        connectionMethods.first()
                    },
                    onConnected = { transport, encodedDeviceEngagement, handover, elapsedTime ->
                        CoroutineScope(Dispatchers.IO).launch {
                            // Now that we're connected, close remaining transports
                            transportsToClose.forEach {
                                if (it != transport) {
                                    Logger.i(TAG, "Closing connection with CM ${it.connectionMethod}")
                                    it.close()
                                }
                            }
                            transportsToClose.clear()
                            // Call the app with the created transport
                            onConnected(transport, encodedDeviceEngagement, handover, elapsedTime)
                        }
                    }
                )
                // Stop polling
                false
            }
        )
    } finally {
        // Close listening transports that went unused.
        transportsToClose.forEach {
            Logger.i(TAG, "Closing connection with CM ${it.connectionMethod}")
            it.close()
        }
    }
}

suspend fun performNfcHandover(
    tag: NfcIsoTag,
    options: MdocTransportOptions,
    selectConnectionMethod: (connectionMethods: List<ConnectionMethod>) -> ConnectionMethod,
    negotiatedHandoverTransports: List<MdocTransport>,
    onConnected: (
        transport: MdocTransport,
        encodedDeviceEngagement: ByteArray,
        handover: DataItem,
        elapsedTime: Duration
    ) -> Unit
) {
    val timeBegin = Clock.System.now()

    Logger.i(TAG, "Starting NFC handover")

    tag.selectApplication(Nfc.NDEF_APPLICATION_ID)
    tag.selectFile(Nfc.NDEF_CAPABILITY_CONTAINER_FILE_ID)
    // CC file is 15 bytes long
    val ccFile = tag.readBinary(0, 15)
    check(ccFile.size >= 15) { "CC file is ${ccFile.size} bytes, expected 15" }

    val ndefFileId = (ccFile[9].toInt() and 0xff) * 256 + (ccFile[10].toInt() and 0xff)
    Logger.d(TAG, "NDEF file id ${ndefFileId}")

    tag.selectFile(ndefFileId)

    val initialNdefMessage = tag.ndefReadMessage()

    // First see if we should use negotiated handover by looking for the Handover Service parameter record...
    val hspr = initialNdefMessage.records.mapNotNull {
        val parsed = Nfc.ServiceParameterRecord.parseRecord(it)
        if (parsed?.serviceNameUri == "urn:nfc:sn:handover") parsed else null
    }.firstOrNull()
    if (hspr == null) {
        val elapsedTime = Clock.System.now() - timeBegin
        Logger.i(TAG, "Time spent in NFC static handover: $elapsedTime")
        Logger.d(TAG, "No ${Nfc.SERVICE_NAME_CONNECTION_HANDOVER} record found - assuming NFC static handover")
        val hs = MdocNfcUtil.parseHandoverSelectMessage(initialNdefMessage)
            ?: throw IllegalStateException("Error parsing Handover Select message")
        check(!hs.connectionMethods.isEmpty()) { "No connection methods in Handover Select" }
        if (Logger.isDebugEnabled) {
            for (cm in hs.connectionMethods) {
                Logger.d(TAG, "Connection method from static handover: $cm")
            }
        }
        Logger.d(TAG, "Reporting Device Engagement through NFC")
        val readerHandover = CborArray.builder()
            .add(initialNdefMessage.encode()) // Handover Select message
            .add(Simple.NULL)                 // Handover Request message
            .end()
            .build()

        val transport = MdocTransportFactory.createTransport(
            selectConnectionMethod(hs.connectionMethods),
            MdocTransport.Role.MDOC_READER,
            options
        )

        onConnected(
            transport,
            hs.encodedDeviceEngagement,
            readerHandover,
            elapsedTime
        )
        return
    }

    Logger.d(TAG, "Service Parameter for ${Nfc.SERVICE_NAME_CONNECTION_HANDOVER} found - negotiated handover")
    Logger.d(TAG, "tWait is ${hspr.tWaitMillis}, nWait is ${hspr.nWait}, maxNdefSize is ${hspr.maxNdefSize}")

    // Select the service, the resulting NDEF message is specified in
    // in Tag NDEF Exchange Protocol Technical Specification Version 1.0
    // section 4.3 TNEP Status Message
    val serviceSelectionResponse = tag.ndefTransact(
        NdefMessage(listOf(NdefRecord(
            tnf = NdefRecord.Tnf.WELL_KNOWN,
            type = Nfc.RTD_SERVICE_SELECT,
            payload = Nfc.encodeServiceSelectPayload(Nfc.SERVICE_NAME_CONNECTION_HANDOVER)))
        ),
        hspr.tWaitMillis,
        hspr.nWait
    )

    val tnepStatusRecord = serviceSelectionResponse.records.find { it.type == Nfc.RTD_TNEP_STATUS }
        ?: throw IllegalArgumentException("Service selection: no TNEP status record")
    val tnepStatusPayload = tnepStatusRecord.payload
    require(tnepStatusPayload.size == 1) { "Service selection: Malformed payload for TNEP status record" }
    val statusType = tnepStatusPayload[0].toInt() and 0x0ff
    // Status type is defined in 4.3.3 Status Type
    require(statusType == 0x00) { "Service selection: Unexpected status type $statusType" }

    // Now send Handover Request, the resulting NDEF message is Handover Response..
    //
    val hrConnectionMethods = mutableListOf<ConnectionMethod>()
    for (t in negotiatedHandoverTransports) {
        hrConnectionMethods.add(t.connectionMethod)
    }
    val hrMessage = MdocNfcUtil.createNdefMessageHandoverRequest(
        hrConnectionMethods,
        null,
        options
    ) // TODO: pass ReaderEngagement message
    val hsMessage = tag.ndefTransact(hrMessage, hspr.tWaitMillis, hspr.nWait)

    val elapsedTime = Clock.System.now() - timeBegin
    Logger.i(TAG, "Time spent in NFC negotiated handover: $elapsedTime ms")
    var encodedDeviceEngagement: ByteArray? = null
    var parsedCms = mutableListOf<ConnectionMethod>()
    for (r in hsMessage.records) {
        // DeviceEngagement record
        //
        if (r.tnf == NdefRecord.Tnf.EXTERNAL_TYPE &&
            r.type.decodeToString() == "iso.org:18013:deviceengagement" &&
            r.id.decodeToString() == "mdoc"
        ) {
            encodedDeviceEngagement = r.payload.toByteArray()
            Logger.dCbor(
                TAG,
                "Device Engagement from NFC negotiated handover",
                encodedDeviceEngagement
            )
        } else if (r.tnf == NdefRecord.Tnf.MIME_MEDIA || r.tnf == NdefRecord.Tnf.EXTERNAL_TYPE) {
            val cm = MdocNfcUtil.connectionMethodFromNdefRecord(r, true)
            if (cm != null) {
                parsedCms.add(cm)
                Logger.d(TAG, "CM: $cm")
            }
        }
    }
    checkNotNull(encodedDeviceEngagement) { "Device Engagement not found in HS message" }
    check(parsedCms.size >= 1) { "No Alternative Carriers in HS message" }

    // TODO: use selected CMs to pick from the list we offered... why would we
    //  have to do this? Because some mDL / wallets don't return the UUID in
    //  the HS message.
    //  For now just assume we only offered a single CM and the other side accepted.
    //
    require (negotiatedHandoverTransports.size == 1) {
        "Only a single negotiated transport connectionMethod is currently supported"
    }
    val transport = negotiatedHandoverTransports[0]

    val handover = CborArray.builder()
        .add(hsMessage.encode()) // Handover Select message
        .add(hrMessage.encode()) // Handover Request message
        .end()
        .build()

    onConnected(
        transport,
        encodedDeviceEngagement,
        handover,
        elapsedTime
    )
}
