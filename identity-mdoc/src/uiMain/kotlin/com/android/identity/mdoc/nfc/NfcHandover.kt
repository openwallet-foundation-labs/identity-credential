package com.android.identity.mdoc.nfc

import com.android.identity.cbor.CborArray
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.Simple
import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import com.android.identity.mdoc.connectionmethod.ConnectionMethodBle
import com.android.identity.mdoc.engagement.EngagementParser
import com.android.identity.mdoc.transport.MdocTransport
import com.android.identity.mdoc.transport.MdocTransportFactory
import com.android.identity.mdoc.transport.MdocTransportOptions
import com.android.identity.nfc.NdefMessage
import com.android.identity.nfc.NdefRecord
import com.android.identity.nfc.NfcIsoTag
import com.android.identity.nfc.NfcTagReader
import com.android.identity.nfc.NfcUtil
import com.android.identity.util.Logger
import com.android.identity.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
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
        val tagReader = NfcTagReader()
        tagReader.beginSession(
            showDialog = true,
            alertMessage = "Hold your phone near the presenter's phone to request credentials.",
            tagInteractionFunc = { tag ->
                nfcHandover(
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
                                    it.close()
                                }
                            }
                            transportsToClose.clear()
                            // Call the app with the created transport
                            onConnected(transport, encodedDeviceEngagement, handover, elapsedTime)
                        }
                    }
                )
            }
        )
    } finally {
        // Close listening transports. This is for the path where no tag was scanned (if a tag
        // was scanned, `tagInteractionFunc()` will have closed all transports except the
        // selected one below...
        transportsToClose.forEach { it.close() }
    }
}

/**
 * Perform NFC engagement as a reader.
 *
 * @param tag a [NfcIsoTag] obtained from a [com.android.identity.nfc.NfcTagReader].
 * @param option: Options used for
 */
suspend fun nfcHandover(
    tag: NfcIsoTag,
    options: MdocTransportOptions,
    negotiatedHandoverTransports: List<MdocTransport>,
    selectConnectionMethod: (connectionMethods: List<ConnectionMethod>) -> ConnectionMethod,
    onConnected: (
        transport: MdocTransport,
        encodedDeviceEngagement: ByteArray,
        handover: DataItem,
        elapsedTime: Duration
    ) -> Unit
) {
    Logger.i(TAG, "Starting NFC handover")
    try {
        performNfcHandover(
            tag,
            options,
            selectConnectionMethod,
            negotiatedHandoverTransports,
            { transport, encodedDeviceEngagement, handover, elapsedTime ->
                // Close all advertising transports except if it was picked by the remote peer.
                CoroutineScope(Dispatchers.IO).launch {
                    negotiatedHandoverTransports.forEach {
                        if (it != transport) {
                            Logger.i(TAG, "Closing connection with CM ${it.connectionMethod}")
                            it.close()
                        } else {
                            Logger.i(TAG, "Not closing CM ${it.connectionMethod}")
                        }
                    }
                }
                onConnected(transport, encodedDeviceEngagement, handover, elapsedTime)
            }
        )
    } catch (e: Throwable) {
        negotiatedHandoverTransports.forEach {
            Logger.i(TAG, "Closing connection with CM ${it.connectionMethod}")
            it.close()
        }
        throw e
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
    var ret: ByteArray?
    var apdu: ByteArray

    apdu = NfcUtil.createApduApplicationSelect(NfcUtil.AID_FOR_TYPE_4_TAG_NDEF_APPLICATION)
    ret = tag.transceive(apdu)
    if (!(ret contentEquals NfcUtil.STATUS_WORD_OK)) {
        Logger.eHex(TAG, "NDEF application selection failed, ret", ret)
        throw IllegalStateException("NDEF application selection failed")
    }
    apdu = NfcUtil.createApduSelectFile(NfcUtil.CAPABILITY_CONTAINER_FILE_ID)
    ret = tag.transceive(apdu)
    if (!(ret contentEquals NfcUtil.STATUS_WORD_OK)) {
        Logger.eHex(TAG, "Error selecting capability file, ret", ret)
        throw IllegalStateException("Error selecting capability file")
    }

    // CC file is 15 bytes long
    val ccFile = NfcUtil.readBinary(tag, 0, 15) ?: throw IllegalStateException("Error reading CC file")
    check(ccFile.size >= 15) { "CC file is ${ccFile.size} bytes, expected 15" }

    // TODO: look at mapping version in ccFile
    val ndefFileId = (ccFile[9].toInt() and 0xff) * 256 + (ccFile[10].toInt() and 0xff)
    Logger.d(TAG, "NDEF file id ${ndefFileId}")
    apdu = NfcUtil.createApduSelectFile(NfcUtil.NDEF_FILE_ID)
    ret = tag.transceive(apdu)
    if (!(ret contentEquals NfcUtil.STATUS_WORD_OK)) {
        Logger.eHex(TAG, "Error selecting NDEF file, ret", ret)
        throw IllegalStateException("Error selecting NDEF file")
    }

    // First see if we should use negotiated handover..
    val initialNdefMessage = NfcUtil.ndefReadMessage(tag, 1.0, 0)
    if (initialNdefMessage == null) {
        throw IllegalStateException("Error reading initial NDEF message")
    }
    val handoverServiceRecord =
        NfcUtil.findServiceParameterRecordWithName(
            initialNdefMessage,
            "urn:nfc:sn:handover"
        )
    if (handoverServiceRecord == null) {
        val elapsedTime = Clock.System.now() - timeBegin
        Logger.i(TAG,"Time spent in NFC static handover: $elapsedTime")
        Logger.d(TAG, "No urn:nfc:sn:handover record found - assuming NFC static handover")
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
            .add(initialNdefMessage) // Handover Select message
            .add(Simple.NULL)        // Handover Request message
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

    Logger.d(TAG, "Service Parameter for urn:nfc:sn:handover found - negotiated handover")
    val spr = NfcUtil.parseServiceParameterRecord(handoverServiceRecord)
    Logger.d(TAG, "tWait is ${spr.tWaitMillis}, nWait is ${spr.nWait}, maxNdefSize is ${spr.maxNdefSize}")

    // Select the service, the resulting NDEF message is specified in
    // in Tag NDEF Exchange Protocol Technical Specification Version 1.0
    // section 4.3 TNEP Status Message
    ret = NfcUtil.ndefTransact(tag,
        NfcUtil.createNdefMessageServiceSelect("urn:nfc:sn:handover"),
        spr.tWaitMillis, spr.nWait
    )
    checkNotNull(ret) { "Service selection: no response" }
    val tnepStatusRecord = NfcUtil.findTnepStatusRecord(ret)
        ?: throw IllegalArgumentException("Service selection: no TNEP status record")
    val tnepStatusPayload = tnepStatusRecord.payload
    require(!(tnepStatusPayload == null || tnepStatusPayload.size != 1)) {
        "Service selection: Malformed payload for TNEP status record"
    }
    val statusType = tnepStatusPayload[0].toInt() and 0x0ff
    // Status type is defined in 4.3.3 Status Type
    require(statusType == 0x00) {
        "Service selection: Unexpected status type $statusType"
    }

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
    Logger.dHex(TAG, "Handover Request sent", hrMessage)
    val hsMessage = NfcUtil.ndefTransact(tag, hrMessage, spr.tWaitMillis, spr.nWait)
        ?: throw IllegalStateException("Handover Request failed")
    Logger.dHex(TAG, "Handover Select received", hsMessage)

    val elapsedTime = Clock.System.now() - timeBegin
    Logger.i(TAG, "Time spent in NFC negotiated handover: $elapsedTime ms")
    var encodedDeviceEngagement: ByteArray? = null
    var parsedCms = mutableListOf<ConnectionMethod>()
    val ndefHsMessage = NdefMessage.fromEncoded(hsMessage)
    for (r in ndefHsMessage.records) {
        // DeviceEngagement record
        //
        if (r.tnf == NdefRecord.TNF_EXTERNAL_TYPE &&
            r.type contentEquals "iso.org:18013:deviceengagement".encodeToByteArray() &&
            r.id contentEquals "mdoc".encodeToByteArray()
        ) {
            encodedDeviceEngagement = r.payload
            Logger.dCbor(
                TAG,
                "Device Engagement from NFC negotiated handover",
                encodedDeviceEngagement
            )
        } else if (r.tnf == NdefRecord.TNF_MIME_MEDIA || r.tnf == NdefRecord.TNF_EXTERNAL_TYPE) {
            val cm = MdocNfcUtil.fromNdefRecord(r, true)
            if (cm != null) {
                parsedCms.add(cm)
                Logger.d(TAG, "CM: $cm")
            }
        }
    }
    checkNotNull(encodedDeviceEngagement) { "Device Engagement not found in HS message" }
    check(parsedCms.size >= 1) { "No Alternative Carriers in HS message" }

    // Now that we have DeviceEngagement, pass eDeviceKeyBytes to the transport
    // since it's needed for the Ident characteristic as per ISO/IEC 18013-5:2021
    // clause 8.3.3.1.1.3 Connection setup
    val engagement = EngagementParser(encodedDeviceEngagement).parse()

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
        .add(hsMessage) // Handover Select message
        .add(hrMessage) // Handover Request message
        .end()
        .build()

    onConnected(
        transport,
        encodedDeviceEngagement,
        handover,
        elapsedTime
    )
}