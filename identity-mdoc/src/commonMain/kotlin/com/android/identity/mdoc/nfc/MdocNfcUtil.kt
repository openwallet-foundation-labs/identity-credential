package com.android.identity.mdoc.nfc

import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import com.android.identity.mdoc.connectionmethod.ConnectionMethodBle
import com.android.identity.mdoc.transport.MdocTransportOptions
import com.android.identity.nfc.NdefMessage
import com.android.identity.nfc.NdefRecord
import com.android.identity.util.Logger
import com.android.identity.util.UUID
import com.android.identity.util.toHex
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.readLongLe
import kotlinx.io.writeULongLe

object MdocNfcUtil {
    private const val TAG = "MdocNfcUtil"

    data class ParsedHandoverSelectMessage(
        val encodedDeviceEngagement: ByteArray,
        val connectionMethods: List<ConnectionMethod>
    )

    private fun calculateHandoverSelectPayload(alternativeCarrierRecords: List<ByteArray>): ByteArray {
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
        val buf = Buffer()
        buf.writeByte(0x15) // version 1.5
        val acRecords = mutableListOf<NdefRecord>()
        for (acRecordPayload in alternativeCarrierRecords) {
            acRecords.add(NdefRecord(
                NdefRecord.TNF_WELL_KNOWN,
                "ac".encodeToByteArray(),
                byteArrayOf(),
                acRecordPayload
            ))
        }
        val hsMessage = NdefMessage(acRecords)
        buf.write(hsMessage.encode())
        return buf.readByteArray()
    }

    private fun createNdefMessageHandoverSelectOrRequest(
        methods: List<ConnectionMethod>,
        encodedDeviceEngagement: ByteArray?,
        encodedReaderEngagement: ByteArray?,
        options: MdocTransportOptions?
    ): ByteArray {
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
        val alternativeCarrierRecords = mutableListOf<ByteArray>()

        // TODO: we actually need to do the reverse disambiguation to e.g. merge two
        //  disambiguated BLE ConnectionMethods...
        for (cm in methods) {
            val records = toNdefRecord(cm, auxiliaryReferences, isHandoverSelect)
            if (records != null) {
                if (Logger.isDebugEnabled) {
                    Logger.d(
                        TAG, "ConnectionMethod $cm: alternativeCarrierRecord: "
                                + "${records.second.toHex()} carrierConfigurationRecord: "
                                + "${records.first.payload?.toHex()}"
                    )
                }
                alternativeCarrierRecords.add(records.second)
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
                (if (isHandoverSelect) "Hs" else "Hr").encodeToByteArray(),
                byteArrayOf(),
                hsPayload
            )
        )
        if (encodedDeviceEngagement != null) {
            records.add(
                NdefRecord(
                    NdefRecord.TNF_EXTERNAL_TYPE,
                    "iso.org:18013:deviceengagement".encodeToByteArray(),
                    "mdoc".encodeToByteArray(),
                    encodedDeviceEngagement
                )
            )
        }
        if (encodedReaderEngagement != null) {
            records.add(
                NdefRecord(
                    NdefRecord.TNF_EXTERNAL_TYPE,
                    "iso.org:18013:readerengagement".encodeToByteArray(),
                    "mdocreader".encodeToByteArray(),
                    encodedReaderEngagement
                )
            )
        }
        for (record in carrierConfigurationRecords) {
            records.add(record)
        }
        val message = NdefMessage(records)
        return message.encode()
    }

    fun createNdefMessageHandoverSelect(
        methods: List<ConnectionMethod>,
        encodedDeviceEngagement: ByteArray,
        options: MdocTransportOptions?
    ): ByteArray {
        return createNdefMessageHandoverSelectOrRequest(
            methods,
            encodedDeviceEngagement,
            null,
            options
        )
    }

    fun createNdefMessageHandoverRequest(
        methods: List<ConnectionMethod>,
        encodedReaderEngagement: ByteArray?,
        options: MdocTransportOptions?
    ): ByteArray {
        return createNdefMessageHandoverSelectOrRequest(
            methods,
            null,
            encodedReaderEngagement,
            options
        )
    }

    // Returns null if parsing fails, otherwise returns a ParsedHandoverSelectMessage instance
    fun parseHandoverSelectMessage(ndefMessage: ByteArray): ParsedHandoverSelectMessage? {
        var m = try {
            NdefMessage.fromEncoded(ndefMessage)
        } catch (e: Throwable) {
            Logger.w(TAG, "Error parsing NDEF message", e)
            return null
        }
        var validHandoverSelectMessage = false

        var phsmEncodedDeviceEngagement: ByteArray? = null
        var phsmConnectionMethods = mutableListOf<ConnectionMethod>()
        for (r in m.records) {
            // Handle Handover Select record for NFC Forum Connection Handover specification
            // version 1.5 (encoded as 0x15 below).
            //
            Logger.i(TAG, "tnf=${r.tnf} type=${r.type.toHex()} Hs=${"Hs".encodeToByteArray().toHex()}")
            if (r.tnf == NdefRecord.TNF_WELL_KNOWN && r.type contentEquals "Hs".encodeToByteArray()) {
                Logger.i(TAG, "wootsie")
                val payload = r.payload
                if (payload != null && payload.size >= 1 && payload[0].toInt() == 0x15) {
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
            if (r.tnf == NdefRecord.TNF_EXTERNAL_TYPE &&
                r.type contentEquals "iso.org:18013:deviceengagement".encodeToByteArray() &&
                r.id contentEquals "mdoc".encodeToByteArray()) {
                phsmEncodedDeviceEngagement = r.payload
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

    fun fromNdefRecord(record: NdefRecord, isForHandoverSelect: Boolean): ConnectionMethod? {
        // BLE Carrier Configuration record
        //
        if (record.tnf == NdefRecord.TNF_MIME_MEDIA &&
            record.type contentEquals "application/vnd.bluetooth.le.oob".encodeToByteArray() &&
            record.id contentEquals "0".encodeToByteArray()) {
            return bleFromNdefRecord(record, isForHandoverSelect)
        }

        // Wifi Aware Carrier Configuration record
        //
        if (record.tnf == NdefRecord.TNF_MIME_MEDIA &&
            record.type contentEquals "application/vnd.wfa.nan".encodeToByteArray() &&
            record.id contentEquals "W".encodeToByteArray()) {
            TODO()
            /*
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                DataTransportWifiAware.fromNdefRecord(record, isForHandoverSelect)
            } else {
                Logger.w(
                    TAG, "Ignoring Wifi Aware Carrier Configuration since Wifi Aware "
                            + "is not available on this API level"
                )
                null
            }
             */
        }

        // NFC Carrier Configuration record
        //
        if (record.tnf == NdefRecord.TNF_EXTERNAL_TYPE &&
            record.type contentEquals "iso.org:18013:nfc".encodeToByteArray() &&
            record.id contentEquals "nfc".encodeToByteArray()) {
            TODO() //return DataTransportNfc.fromNdefRecord(record, isForHandoverSelect)
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
    fun toNdefRecord(
        connectionMethod: ConnectionMethod,
        auxiliaryReferences: List<String>,
        isForHandoverSelect: Boolean
    ): Pair<NdefRecord, ByteArray>? {
        return when (connectionMethod) {
            is ConnectionMethodBle -> bleToNdefRecord(connectionMethod, auxiliaryReferences, isForHandoverSelect)
            else -> {
                Logger.w(TAG, "toNdefRecord: Unsupported ConnectionMethod")
                null
            }
        }
    }

    private fun bleToNdefRecord(
        cm: ConnectionMethodBle,
        auxiliaryReferences: List<String>,
        isForHandoverSelect: Boolean
    ): Pair<NdefRecord, ByteArray> {
        // The OOB data is defined in "Supplement to the Bluetooth Core Specification".
        //
        // See section 1.17.2 for values
        //
        val uuid: UUID?
        val leRole: Int
        if (cm.supportsCentralClientMode && cm.supportsPeripheralServerMode) {
            // Peripheral and Central Role supported,
            // Central Role preferred for connection
            // establishment
            leRole = 0x03
            check(cm.centralClientModeUuid == cm.peripheralServerModeUuid) {
                "UUIDs for both BLE modes must be the same"
            }
            uuid = cm.centralClientModeUuid
        } else if (cm.supportsCentralClientMode) {
            leRole = if (isForHandoverSelect) {
                // Only Central Role supported
                0x01
            } else {
                // Only Peripheral Role supported
                0x00
            }
            uuid = cm.centralClientModeUuid
        } else if (cm.supportsPeripheralServerMode) {
            leRole = if (isForHandoverSelect) {
                // Only Peripheral Role supported
                0x00
            } else {
                // Only Central Role supported
                0x01
            }
            uuid = cm.peripheralServerModeUuid
        } else {
            throw IllegalStateException("At least one of the BLE modes must be set")
        }

        // See "3 Handover to a Bluetooth Carrier" of "Bluetooth® Secure Simple Pairing Using
        // NFC Application Document" Version 1.2. This says:
        //
        //   For Bluetooth LE OOB the name “application/vnd.bluetooth.le.oob” is used as the
        //   [NDEF] record type name. The payload of this type of record is then defined by the
        //   Advertising and Scan Response Data (AD) format that is specified in the Bluetooth Core
        //   Specification ([BLUETOOTH_CORE], Volume 3, Part C, Section 11).
        //
        // Looking that up it says it's just a sequence of {length, AD type, AD data} where each
        // AD is defined in the "Bluetooth Supplement to the Core Specification" document.
        //
        val buf = Buffer()
        buf.writeByte(0x02)
        buf.writeByte(0x1c) // LE Role
        buf.writeByte(leRole.toByte())
        if (uuid != null) {
            buf.writeByte(0x11) // Complete List of 128-bit Service UUID’s (0x07)
            buf.writeByte(0x07)
            buf.writeULongLe(uuid.leastSignificantBits)
            buf.writeULongLe(uuid.mostSignificantBits)
        }
        val macAddress = cm.peripheralServerModeMacAddress
        if (macAddress != null) {
            require(macAddress.size == 6) {
                "MAC address should be six bytes, found ${macAddress.size}"
            }
            buf.writeByte(0x07)
            buf.writeByte(0x1b) // MAC address
            buf.write(macAddress)
        }
        val psm = cm.peripheralServerModePsm
        if (psm != null) {
            // TODO: need to actually allocate this number (0x77)
            buf.writeByte(0x05) // PSM: 4 bytes
            buf.writeByte(0x77)
            buf.writeInt(psm)
        }
        val oobData = buf.readByteArray()
        val record = NdefRecord(
            NdefRecord.TNF_MIME_MEDIA,
            "application/vnd.bluetooth.le.oob".encodeToByteArray(),
            "0".encodeToByteArray(),
            oobData
        )

        // From 7.1 Alternative Carrier Record
        //
        val acrBuf = Buffer()
        acrBuf.writeByte(0x01) // CPS: active
        acrBuf.writeByte(0x01) // Length of carrier data reference ("0")
        acrBuf.writeByte('0'.code.and(0xff).toByte()) // Carrier data reference
        acrBuf.writeByte(auxiliaryReferences.size.toByte()) // Number of auxiliary references
        for (auxRef in auxiliaryReferences) {
            // Each auxiliary reference consists of a single byte for the length and then as
            // many bytes for the reference itself.
            val auxRefUtf8 = auxRef.encodeToByteArray()
            acrBuf.writeByte(auxRefUtf8.size.and(0xff).toByte())
            acrBuf.write(auxRefUtf8, 0, auxRefUtf8.size)
        }
        val acRecordPayload = acrBuf.readByteArray()
        return Pair(record, acRecordPayload)
    }

    private fun bleFromNdefRecord(
        record: NdefRecord,
        isForHandoverSelect: Boolean
    ): ConnectionMethodBle? {
        var centralClient = false
        var peripheral = false
        var uuid: UUID? = null
        var gotLeRole = false
        var gotUuid = false
        var psm : Int? = null
        var macAddress: ByteArray? = null

        // See createNdefRecords() method for how this data is encoded.
        //
        val payload = Buffer()
        payload.write(record.payload)
        Logger.iHex(TAG, "payload", record.payload)
        while (!payload.exhausted()) {
            val len = payload.readByte().toInt()
            val type = payload.readByte().toInt()
            Logger.i(TAG, "len $len type $type")
            if (type == 0x1c && len == 2) {
                gotLeRole = true
                val value = payload.readByte().toInt()
                if (value == 0x00) {
                    if (isForHandoverSelect) {
                        peripheral = true
                    } else {
                        centralClient = true
                    }
                } else if (value == 0x01) {
                    if (isForHandoverSelect) {
                        centralClient = true
                    } else {
                        peripheral = true
                    }
                } else if (value == 0x02 || value == 0x03) {
                    centralClient = true
                    peripheral = true
                } else {
                    Logger.w(TAG, "Invalid value $value for LE role")
                    return null
                }
            } else if (type == 0x07) {
                val uuidLen = len - 1
                if (uuidLen % 16 != 0) {
                    Logger.w(TAG, "UUID len $uuidLen is not divisible by 16")
                    return null
                }
                // We only use the last UUID...
                var n = 0
                while (n < uuidLen) {
                    val lsb = payload.readLongLe().toULong()
                    val msb = payload.readLongLe().toULong()
                    uuid = UUID(msb, lsb)
                    gotUuid = true
                    n += 16
                }
            } else if (type == 0x1b && len == 0x07) {
                // MAC address
                macAddress = payload.readByteArray(6)
            } else if (type == 0x77 && len == 0x05) {
                // PSM
                psm = payload.readInt()
            } else {
                Logger.d(TAG, "Skipping unknown type $type of length $len")
                payload.skip(len.toLong() - 1L)
            }
        }
        if (!gotLeRole) {
            Logger.w(TAG, "Did not find LE role")
            return null
        }

        // Note that UUID may _not_ be set.

        // Note that the UUID for both modes is the same if both peripheral and
        // central client mode is used!
        val cm = ConnectionMethodBle(
            peripheral,
            centralClient,
            if (peripheral) uuid else null,
            if (centralClient) uuid else null
        )
        cm.peripheralServerModePsm = psm
        cm.peripheralServerModeMacAddress = macAddress
        return cm
    }

}