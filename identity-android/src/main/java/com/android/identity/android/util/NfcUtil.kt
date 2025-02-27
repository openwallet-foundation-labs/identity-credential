package com.android.identity.android.util

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.os.Build
import android.util.Pair
import com.android.identity.android.mdoc.transport.ConnectionMethodTcp
import com.android.identity.android.mdoc.transport.ConnectionMethodUdp
import com.android.identity.android.mdoc.transport.DataTransportBle
import com.android.identity.android.mdoc.transport.DataTransportNfc
import com.android.identity.android.mdoc.transport.DataTransportOptions
import com.android.identity.android.mdoc.transport.DataTransportTcp
import com.android.identity.android.mdoc.transport.DataTransportUdp
import com.android.identity.android.mdoc.transport.DataTransportWifiAware
import com.android.identity.cbor.Cbor
import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import com.android.identity.mdoc.connectionmethod.ConnectionMethodBle
import com.android.identity.mdoc.connectionmethod.ConnectionMethodNfc
import com.android.identity.mdoc.connectionmethod.ConnectionMethodWifiAware
import com.android.identity.util.Logger
import com.android.identity.util.appendUInt16
import com.android.identity.util.appendUInt32
import com.android.identity.util.appendUInt8
import com.android.identity.util.fromHex
import com.android.identity.util.getUInt8
import com.android.identity.util.toHex
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.append
import kotlinx.io.bytestring.buildByteString
import java.io.IOException
import java.util.Arrays

object NfcUtil {
    private const val TAG = "NfcUtil"

    // Defined by NFC Forum
    @JvmField
    val AID_FOR_TYPE_4_TAG_NDEF_APPLICATION = ByteString("D2760000850101".fromHex())

    // Defined by 18013-5 Section 8.3.3.1.2 Data retrieval using near field communication (NFC)
    val AID_FOR_MDL_DATA_TRANSFER = ByteString("A0000002480400".fromHex())

    const val COMMAND_TYPE_OTHER = 0
    const val COMMAND_TYPE_SELECT_BY_AID = 1
    const val COMMAND_TYPE_SELECT_FILE = 2
    const val COMMAND_TYPE_READ_BINARY = 3
    const val COMMAND_TYPE_UPDATE_BINARY = 4
    const val COMMAND_TYPE_ENVELOPE = 5
    const val COMMAND_TYPE_RESPONSE = 6
    const val CAPABILITY_CONTAINER_FILE_ID = 0xe103
    const val NDEF_FILE_ID = 0xe104

    @JvmField
    val STATUS_WORD_INSTRUCTION_NOT_SUPPORTED = buildByteString { appendUInt16(0x6d00) }
    @JvmField
    val STATUS_WORD_OK = buildByteString { appendUInt16(0x9000) }
    @JvmField
    val STATUS_WORD_FILE_NOT_FOUND = buildByteString { appendUInt16(0x6a82) }
    @JvmField
    val STATUS_WORD_END_OF_FILE_REACHED = buildByteString { appendUInt16(0x6282) }
    @JvmField
    val STATUS_WORD_WRONG_PARAMETERS = buildByteString { appendUInt16(0x6b00) }
    @JvmField
    val STATUS_WORD_WRONG_LENGTH = buildByteString { appendUInt16(0x6700) }

    @JvmStatic
    fun nfcGetCommandType(apdu: ByteString): Int {
        if (apdu.size < 3) {
            return COMMAND_TYPE_OTHER
        }
        val ins = apdu.getUInt8(1).toInt()
        val p1 = apdu.getUInt8(2).toInt()
        return when (ins) {
            0xA4 ->
                when(p1) {
                    0x04 -> COMMAND_TYPE_SELECT_BY_AID
                    0x00 -> COMMAND_TYPE_SELECT_FILE
                    else -> COMMAND_TYPE_OTHER
                }
            0xb0 -> COMMAND_TYPE_READ_BINARY
            0xd6 -> COMMAND_TYPE_UPDATE_BINARY
            0xc0 -> COMMAND_TYPE_RESPONSE
            0xc3 -> COMMAND_TYPE_ENVELOPE
            else -> COMMAND_TYPE_OTHER
        }
    }

    @JvmStatic
    fun createApduApplicationSelect(aid: ByteString): ByteString {
        return buildByteString {
            appendUInt32(0x00a40400)
            appendUInt8(aid.size)
            try {
                append(aid)
            } catch (e: IOException) {
                throw IllegalStateException(e)
            }
        }
    }

    @JvmStatic
    fun createApduSelectFile(fileId: Int): ByteString {
        return buildByteString {
            appendUInt32(0x00a4000c02u)
            appendUInt16(fileId)
        }
    }

    @JvmStatic
    fun createApduReadBinary(offset: Int, length: Int): ByteString {
        require(length != 0) { "Length cannot be zero" }
        return buildByteString {
            appendUInt16(0x00b0)
            appendUInt16(offset)
            if (length < 0x100) {
                appendUInt8(length)
            } else {
                appendUInt8(0u)
                appendUInt16(length)
            }
        }
    }

    @JvmStatic
    fun createApduUpdateBinary(offset: Int, data: ByteString): ByteString {
        require(data.size < 0x100) { "Data must be shorter than 0x100 bytes" }
        return buildByteString {
            appendUInt16(0x00d6)
            appendUInt16(offset)
            appendUInt8(data.size)
            try {
                append(data)
            } catch (e: IOException) {
                throw IllegalArgumentException(e)
            }
        }
    }

    @JvmStatic
    fun createNdefMessageServiceSelect(serviceName: String): ByteString {
        // [TNEP] section 4.2.2 Service Select Record
        val payload = " $serviceName".toByteArray()
        payload[0] = (payload.size - 1).toByte()
        val record = NdefRecord(
            NdefRecord.TNF_WELL_KNOWN,
            "Ts".toByteArray(),
            null,
            payload
        )
        return ByteString(NdefMessage(arrayOf(record)).toByteArray())
    }

    private fun calculateHandoverSelectPayload(alternativeCarrierRecords: List<ByteString>): ByteString {
        // 6.2 Handover Select Record
        //
        // The NDEF payload of the Handover Select Record SHALL consist of a single octet that
        // contains the MAJOR_VERSION and MINOR_VERSION numbers, optionally followed by an embedded
        // NDEF message.
        //
        // If present, the NDEF message SHALL consist of one of the following options:
        // - One or more ALTERNATIVE_CARRIER_RECORDs
        // - One or more ALTERNATIVE_CARRIER_RECORDs followed by an ERROR_RECORD
        // - An ERROR_RECORD.
        //

        val acRecords = arrayOfNulls<NdefRecord>(alternativeCarrierRecords.size)
        for (n in alternativeCarrierRecords.indices) {
            val acRecordPayload = alternativeCarrierRecords[n]
            acRecords[n] = NdefRecord(
                0x01.toShort(),
                "ac".toByteArray(),
                null,
                acRecordPayload.toByteArray()
            )
        }
        val hsMessage = NdefMessage(acRecords)
        return buildByteString {
            appendUInt8(0x15u)
            append(hsMessage.toByteArray())
        }
    }

    private fun createNdefMessageHandoverSelectOrRequest(
        methods: List<ConnectionMethod>,
        encodedDeviceEngagement: ByteString?,
        encodedReaderEngagement: ByteString?,
        options: DataTransportOptions?
    ): ByteString {
        var isHandoverSelect = false
        if (encodedDeviceEngagement != null) {
            isHandoverSelect = true
            require(encodedReaderEngagement == null) { "Cannot have readerEngagement in Handover Select" }
        }
        val auxiliaryReferences = mutableListOf<String>()
        if (isHandoverSelect) {
            auxiliaryReferences.add("mdoc")
        }
        val carrierConfigurationRecords = mutableListOf<NdefRecord>()
        val alternativeCarrierRecords = mutableListOf<ByteString>()

        // TODO: we actually need to do the reverse disambiguation to e.g. merge two
        //  disambiguated BLE ConnectionMethods...
        for (cm in methods) {
            val records = toNdefRecord(cm, auxiliaryReferences, isHandoverSelect)
            if (records != null) {
                if (Logger.isDebugEnabled) {
                    Logger.d(
                        TAG, "ConnectionMethod $cm: alternativeCarrierRecord: "
                                + "${records.second.toHex()} carrierConfigurationRecord: "
                                + records.first.payload.toHex()
                    )
                }
                alternativeCarrierRecords.add(ByteString(records.second))
                carrierConfigurationRecords.add(records.first)
            } else {
                Logger.w(TAG, "Ignoring address $cm which yielded no NDEF records")
            }
        }
        val records = mutableListOf<NdefRecord>()
        val hsPayload = calculateHandoverSelectPayload(alternativeCarrierRecords)
        records.add(
            NdefRecord(
                NdefRecord.TNF_WELL_KNOWN,
                (if (isHandoverSelect) "Hs" else "Hr").toByteArray(),
                null,
                hsPayload.toByteArray()
            )
        )
        if (encodedDeviceEngagement != null) {
            records.add(
                NdefRecord(
                    NdefRecord.TNF_EXTERNAL_TYPE,
                    "iso.org:18013:deviceengagement".toByteArray(),
                    "mdoc".toByteArray(),
                    encodedDeviceEngagement.toByteArray()
                )
            )
        }
        if (encodedReaderEngagement != null) {
            records.add(
                NdefRecord(
                    NdefRecord.TNF_EXTERNAL_TYPE,
                    "iso.org:18013:readerengagement".toByteArray(),
                    "mdocreader".toByteArray(),
                    encodedReaderEngagement.toByteArray()
                )
            )
        }
        for (record in carrierConfigurationRecords) {
            records.add(record)
        }
        val message = NdefMessage(records.toTypedArray<NdefRecord>())
        return ByteString(message.toByteArray())
    }

    @JvmStatic
    fun createNdefMessageHandoverSelect(
        methods: List<ConnectionMethod>,
        encodedDeviceEngagement: ByteString,
        options: DataTransportOptions?
    ): ByteString {
        return createNdefMessageHandoverSelectOrRequest(
            methods,
            encodedDeviceEngagement,
            null,
            options
        )
    }

    @JvmStatic
    fun createNdefMessageHandoverRequest(
        methods: List<ConnectionMethod>,
        encodedReaderEngagement: ByteString?,
        options: DataTransportOptions?
    ): ByteString {
        return createNdefMessageHandoverSelectOrRequest(
            methods,
            null,
            encodedReaderEngagement,
            options
        )
    }

    // Returns null if parsing fails, otherwise returns a ParsedHandoverSelectMessage instance
    @JvmStatic
    fun parseHandoverSelectMessage(ndefMessage: ByteString): ParsedHandoverSelectMessage? {
        val m = try {
            NdefMessage(ndefMessage.toByteArray())
        } catch (e: FormatException) {
            Logger.w(TAG, "Error parsing NDEF message", e)
            return null
        }
        var validHandoverSelectMessage = false

        var phsmEncodedDeviceEngagement: ByteString? = null
        val phsmConnectionMethods = mutableListOf<ConnectionMethod>()
        for (r in m.records) {
            // Handle Handover Select record for NFC Forum Connection Handover specification
            // version 1.5 (encoded as 0x15 below).
            //
            if (r.tnf == NdefRecord.TNF_WELL_KNOWN
                && r.type.contentEquals("Hs".toByteArray())
            ) {
                val payload = r.payload
                if (payload.isNotEmpty() && payload[0].toInt() == 0x15) {
                    // The NDEF payload of the Handover Select Record SHALL consist of a single
                    // octet that contains the MAJOR_VERSION and MINOR_VERSION numbers,
                    // optionally followed by an embedded NDEF message.
                    //
                    // If present, the NDEF message SHALL consist of one of the following options:
                    // - One or more ALTERNATIVE_CARRIER_RECORDs
                    // - One or more ALTERNATIVE_CARRIER_RECORDs followed by an ERROR_RECORD
                    // - An ERROR_RECORD.
                    //
                    //byte[] ndefMessage = Arrays.copyOfRange(payload, 1, payload.length);
                    // TODO: check that the ALTERNATIVE_CARRIER_RECORD matches
                    //   the ALTERNATIVE_CARRIER_CONFIGURATION record retrieved below.
                    validHandoverSelectMessage = true
                }
            }

            // DeviceEngagement record
            //
            if (r.tnf == NdefRecord.TNF_EXTERNAL_TYPE && Arrays.equals(
                    r.type,
                    "iso.org:18013:deviceengagement".toByteArray()
                )
                && Arrays.equals(r.id, "mdoc".toByteArray())
            ) {
                phsmEncodedDeviceEngagement = ByteString(r.payload)
            }

            // This parses the various carrier specific NDEF records, see
            // DataTransport.parseNdefRecord() for details.
            //
            if (r.tnf == NdefRecord.TNF_MIME_MEDIA || r.tnf == NdefRecord.TNF_EXTERNAL_TYPE) {
                val cm = fromNdefRecord(r, true)
                if (cm != null) {
                    phsmConnectionMethods.add(cm)
                }
            }
        }
        if (!validHandoverSelectMessage) {
            Logger.w(TAG, "Hs record not found")
            return null
        }
        if (phsmEncodedDeviceEngagement == null) {
            Logger.w(TAG, "DeviceEngagement record not found")
            return null
        }
        return ParsedHandoverSelectMessage(phsmEncodedDeviceEngagement, phsmConnectionMethods)
    }

    @JvmStatic
    fun findServiceParameterRecordWithName(
        ndefMessage: ByteString,
        serviceName: String
    ): NdefRecord? {
        val m = try {
            NdefMessage(ndefMessage.toByteArray())
        } catch (e: FormatException) {
            throw IllegalArgumentException("Error parsing NDEF message", e)
        }
        val snUtf8 = serviceName.toByteArray()
        for (r in m.records) {
            val p = r.payload
            if (r.tnf == NdefRecord.TNF_WELL_KNOWN &&
                "Tp".toByteArray().contentEquals(r.type) &&
                p != null &&
                p.size > snUtf8.size + 2 &&
                p[0].toInt() == 0x10 &&
                p[1].toInt() == snUtf8.size &&
                snUtf8.contentEquals(Arrays.copyOfRange(p, 2, 2 + snUtf8.size))
            ) {
                return r
            }
        }
        return null
    }

    @JvmStatic
    fun parseServiceParameterRecord(serviceParameterRecord: NdefRecord): ParsedServiceParameterRecord {
        require(serviceParameterRecord.tnf == NdefRecord.TNF_WELL_KNOWN) { "Record is not well known" }
        require(
            Arrays.equals(
                "Tp".toByteArray(),
                serviceParameterRecord.type
            )
        ) { "Expected type Tp" }

        // See [TNEP] 4.1.2 Service Parameter Record for the payload
        val p = serviceParameterRecord.payload
        require(p.size >= 1) { "Unexpected length of Service Parameter Record" }
        val serviceNameLen = p[1].toInt()
        require(p.size == serviceNameLen + 7) { "Unexpected length of body in Service Parameter Record" }

        val psprTnepVersion: Int
        val psprServiceNameUri: String
        val psprTnepCommunicationMode: Int
        val psprTWaitMillis: Double
        val psprNWait: Int
        val psprMaxNdefSize: Int

        psprTnepVersion = p[0].toInt() and 0xff
        psprServiceNameUri = String(p, 2, serviceNameLen)
        psprTnepCommunicationMode = p[2 + serviceNameLen].toInt() and 0xff
        val wt_int = p[3 + serviceNameLen].toInt() and 0xff
        psprTWaitMillis = Math.pow(2.0, (wt_int / 4 - 1).toDouble())
        psprNWait = p[4 + serviceNameLen].toInt() and 0xff
        psprMaxNdefSize =
            (p[5 + serviceNameLen].toInt() and 0xff) * 0x100 + (p[6 + serviceNameLen].toInt() and 0xff)
        return ParsedServiceParameterRecord(
            psprTnepVersion,
            psprServiceNameUri,
            psprTnepCommunicationMode,
            psprTWaitMillis,
            psprNWait,
            psprMaxNdefSize
        )
    }

    @JvmStatic
    fun findTnepStatusRecord(ndefMessage: ByteString): NdefRecord? {
        val m = try {
            NdefMessage(ndefMessage.toByteArray())
        } catch (e: FormatException) {
            throw IllegalArgumentException("Error parsing NDEF message", e)
        }
        for (r in m.records) {
            if (r.tnf == NdefRecord.TNF_WELL_KNOWN
                && Arrays.equals("Te".toByteArray(), r.type)
            ) {
                return r
            }
        }
        return null
    }

    private fun getConnectionMethodFromDeviceEngagement(encodedDeviceRetrievalMethod: ByteString): ConnectionMethod? {
        val items = Cbor.decode(encodedDeviceRetrievalMethod)
        val type = items[0].asNumber
        when (type) {
            ConnectionMethodTcp.METHOD_TYPE -> return ConnectionMethodTcp.fromDeviceEngagementTcp(
                encodedDeviceRetrievalMethod
            )

            ConnectionMethodUdp.METHOD_TYPE -> return ConnectionMethodUdp.fromDeviceEngagementUdp(
                encodedDeviceRetrievalMethod
            )
        }
        Logger.w(TAG, "Unsupported ConnectionMethod type $type in DeviceEngagement")
        return null
    }

    @JvmStatic
    fun fromNdefRecord(record: NdefRecord, isForHandoverSelect: Boolean): ConnectionMethod? {
        // BLE Carrier Configuration record
        //
        if (record.tnf == NdefRecord.TNF_MIME_MEDIA && Arrays.equals(
                record.type,
                "application/vnd.bluetooth.le.oob".toByteArray()
            )
            && Arrays.equals(record.id, "0".toByteArray())
        ) {
            return DataTransportBle.fromNdefRecord(record, isForHandoverSelect)
        }

        // Wifi Aware Carrier Configuration record
        //
        if (record.tnf == NdefRecord.TNF_MIME_MEDIA && Arrays.equals(
                record.type,
                "application/vnd.wfa.nan".toByteArray()
            )
            && Arrays.equals(record.id, "W".toByteArray())
        ) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                DataTransportWifiAware.fromNdefRecord(record, isForHandoverSelect)
            } else {
                Logger.w(
                    TAG, "Ignoring Wifi Aware Carrier Configuration since Wifi Aware "
                            + "is not available on this API level"
                )
                null
            }
        }

        // NFC Carrier Configuration record
        //
        if (record.tnf == NdefRecord.TNF_EXTERNAL_TYPE && Arrays.equals(
                record.type,
                "iso.org:18013:nfc".toByteArray()
            )
            && Arrays.equals(record.id, "nfc".toByteArray())
        ) {
            return DataTransportNfc.fromNdefRecord(record, isForHandoverSelect)
        }

        // Generic Carrier Configuration record containing DeviceEngagement
        //
        if (record.tnf == NdefRecord.TNF_MIME_MEDIA
            && Arrays.equals(
                record.type,
                "application/vnd.android.ic.dmr".toByteArray()
            )
        ) {
            val deviceRetrievalMethod = ByteString(record.payload)
            return getConnectionMethodFromDeviceEngagement(deviceRetrievalMethod)
        }
        Logger.d(TAG, "Unknown NDEF record $record")
        return null
    }

    /**
     * Creates Carrier Reference and Auxiliary Data Reference records.
     *
     *
     * If this is to be included in a Handover Select method, pass `{"mdoc"}`
     * for `auxiliaryReferences`.
     *
     * @param auxiliaryReferences A list of references to include in the Alternative Carrier Record
     * @param isForHandoverSelect Set to `true` if this is for a Handover Select method,
     * and `false` if for Handover Request record.
     * @return `null` if the connection method doesn't support NFC handover, otherwise
     * the NDEF record and the Alternative Carrier record.
     */
    @JvmStatic
    fun toNdefRecord(
        connectionMethod: ConnectionMethod,
        auxiliaryReferences: List<String>,
        isForHandoverSelect: Boolean
    ): Pair<NdefRecord, ByteArray>? {
        if (connectionMethod is ConnectionMethodBle) {
            return DataTransportBle.toNdefRecord(
                connectionMethod,
                auxiliaryReferences,
                isForHandoverSelect
            )
        } else if (connectionMethod is ConnectionMethodNfc) {
            return DataTransportNfc.toNdefRecord(
                connectionMethod,
                auxiliaryReferences,
                isForHandoverSelect
            )
        } else if (connectionMethod is ConnectionMethodWifiAware) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return DataTransportWifiAware.toNdefRecord(
                    connectionMethod,
                    auxiliaryReferences,
                    isForHandoverSelect
                )
            }
        } else if (connectionMethod is ConnectionMethodTcp) {
            return DataTransportTcp.toNdefRecord(
                connectionMethod,
                auxiliaryReferences,
                isForHandoverSelect
            )
        } else if (connectionMethod is ConnectionMethodUdp) {
            return DataTransportUdp.toNdefRecord(
                connectionMethod,
                auxiliaryReferences,
                isForHandoverSelect
            )
        }
        Logger.w(TAG, "toNdefRecord: Unsupported ConnectionMethod")
        return null
    }

    data class ParsedHandoverSelectMessage(
        @JvmField
        val encodedDeviceEngagement: ByteString,
        @JvmField
        val connectionMethods: List<ConnectionMethod>
    )

    data class ParsedServiceParameterRecord(
        @JvmField
        val tnepVersion: Int,
        @JvmField
        val serviceNameUri: String,
        @JvmField
        val tnepCommunicationMode: Int,
        @JvmField
        val tWaitMillis: Double,
        @JvmField
        val nWait: Int,
        @JvmField
        val maxNdefSize: Int
    )
}
