package org.multipaz.mdoc.connectionmethod

import org.multipaz.mdoc.transport.MdocTransport
import org.multipaz.nfc.NdefRecord
import org.multipaz.nfc.Nfc
import org.multipaz.util.Logger
import org.multipaz.util.UUID
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.buildByteString
import kotlinx.io.bytestring.encodeToByteString
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.addCborMap
import org.multipaz.cbor.buildCborArray
import org.multipaz.util.ByteDataReader
import org.multipaz.util.appendByteArray
import org.multipaz.util.appendUInt32
import org.multipaz.util.appendUInt64Le
import org.multipaz.util.appendUInt8

/**
 * Connection method for BLE.
 *
 * @param supportsPeripheralServerMode whether mdoc peripheral mode is supported.
 * @param supportsCentralClientMode    whether mdoc central client mode is supported.
 * @param peripheralServerModeUuid     the UUID to use for mdoc peripheral server mode.
 * @param centralClientModeUuid        the UUID to use for mdoc central client mode.
 */
class ConnectionMethodBle(
    val supportsPeripheralServerMode: Boolean,
    val supportsCentralClientMode: Boolean,
    val peripheralServerModeUuid: UUID?,
    val centralClientModeUuid: UUID?
) : ConnectionMethod() {

    /**
     * The L2CAP PSM, if set.
     *
     * This is currently not standardized so use at your own risk.
     */
    var peripheralServerModePsm: Int? = null


    /**
     * The peripheral MAC address, if set.
     */
    var peripheralServerModeMacAddress: ByteArray? = null
        set(macAddress) {
            require(macAddress == null || macAddress.size == 6) {                 
                "MAC address should be 6 bytes, got ${macAddress!!.size}"
            }
            field = macAddress
        }

    override fun equals(other: Any?): Boolean {
        return other is ConnectionMethodBle &&
                other.supportsPeripheralServerMode == supportsPeripheralServerMode &&
                other.supportsCentralClientMode == supportsCentralClientMode &&
                other.peripheralServerModeUuid == peripheralServerModeUuid &&
                other.centralClientModeUuid == centralClientModeUuid &&
                other.peripheralServerModePsm == peripheralServerModePsm &&
                other.peripheralServerModeMacAddress.contentEquals(peripheralServerModeMacAddress)
    }

    override fun toString(): String {
        val sb = StringBuilder("ble")
        if (supportsPeripheralServerMode) {
            sb.append(":peripheral_server_mode:uuid=$peripheralServerModeUuid")
        }
        if (supportsCentralClientMode) {
            sb.append(":central_client_mode:uuid=$centralClientModeUuid")
        }
        if (peripheralServerModePsm != null) {
            sb.append(":psm=" + peripheralServerModePsm.toString())
        }
        if (peripheralServerModeMacAddress != null) {
            sb.append(":mac=")
            for (n in 0..5) {
                if (n > 0) {
                    sb.append("-")
                }
                val value = peripheralServerModeMacAddress!![n]
                sb.append(HEX_DIGITS[value.toInt().and(0xff) shr 4])
                sb.append(HEX_DIGITS[value.toInt().and(0x0f)])
            }
        }
        return sb.toString()
    }

    override fun toDeviceEngagement(): ByteArray {
        return Cbor.encode(
            buildCborArray {
                add(METHOD_TYPE)
                add(METHOD_MAX_VERSION)
                addCborMap {
                    put(
                        OPTION_KEY_SUPPORTS_PERIPHERAL_SERVER_MODE,
                        supportsPeripheralServerMode
                    )
                    put(OPTION_KEY_SUPPORTS_CENTRAL_CLIENT_MODE, supportsCentralClientMode)
                    if (peripheralServerModeUuid != null) {
                        put(
                            OPTION_KEY_PERIPHERAL_SERVER_MODE_UUID,
                            peripheralServerModeUuid.toByteArray()
                        )
                    }
                    if (centralClientModeUuid != null) {
                        put(
                            OPTION_KEY_CENTRAL_CLIENT_MODE_UUID,
                            centralClientModeUuid.toByteArray()
                        )
                    }
                    if (peripheralServerModePsm != null) {
                        put(
                            OPTION_KEY_PERIPHERAL_SERVER_MODE_PSM,
                            peripheralServerModePsm as Int
                        )
                    }
                    if (peripheralServerModeMacAddress != null) {
                        put(
                            OPTION_KEY_PERIPHERAL_SERVER_MODE_BLE_DEVICE_ADDRESS,
                            peripheralServerModeMacAddress!!
                        )
                    }
                }
            }
        )
    }

    companion object {
        private const val TAG = "ConnectionOptionsBle"
        private val HEX_DIGITS = "0123456789abcdef".toCharArray()

        const val METHOD_TYPE = 2L
        const val METHOD_MAX_VERSION = 1L

        private const val OPTION_KEY_SUPPORTS_PERIPHERAL_SERVER_MODE = 0L
        private const val OPTION_KEY_SUPPORTS_CENTRAL_CLIENT_MODE = 1L
        private const val OPTION_KEY_PERIPHERAL_SERVER_MODE_UUID = 10L
        private const val OPTION_KEY_CENTRAL_CLIENT_MODE_UUID = 11L
        private const val OPTION_KEY_PERIPHERAL_SERVER_MODE_BLE_DEVICE_ADDRESS = 20L
        private const val OPTION_KEY_PERIPHERAL_SERVER_MODE_PSM = 21L // NOTE: as per drafts of 18013-5 Second Edition

        internal fun fromDeviceEngagement(encodedDeviceRetrievalMethod: ByteArray): ConnectionMethodBle? {
            val array = Cbor.decode(encodedDeviceRetrievalMethod)
            val type = array[0].asNumber
            val version = array[1].asNumber
            require(type == METHOD_TYPE)
            if (version > METHOD_MAX_VERSION) {
                return null
            }
            val map = array[2]
            val supportsPeripheralServerMode = map[OPTION_KEY_SUPPORTS_PERIPHERAL_SERVER_MODE].asBoolean
            val supportsCentralClientMode = map[OPTION_KEY_SUPPORTS_CENTRAL_CLIENT_MODE].asBoolean
            val peripheralServerModeUuidDi = map.getOrNull(OPTION_KEY_PERIPHERAL_SERVER_MODE_UUID)
            val centralClientModeUuidDi = map.getOrNull(OPTION_KEY_CENTRAL_CLIENT_MODE_UUID)
            var peripheralServerModeUuid: UUID? = null
            if (peripheralServerModeUuidDi != null) {
                peripheralServerModeUuid = UUID.fromByteArray(peripheralServerModeUuidDi.asBstr)
            }
            var centralClientModeUuid: UUID? = null
            if (centralClientModeUuidDi != null) {
                centralClientModeUuid = UUID.fromByteArray(centralClientModeUuidDi.asBstr)
            }
            val cm = ConnectionMethodBle(
                supportsPeripheralServerMode,
                supportsCentralClientMode,
                peripheralServerModeUuid,
                centralClientModeUuid
            )
            val psm = map.getOrNull(OPTION_KEY_PERIPHERAL_SERVER_MODE_PSM)
            if (psm != null) {
                cm.peripheralServerModePsm = psm.asNumber.toInt()
            }
            cm.peripheralServerModeMacAddress =
                map.getOrNull(OPTION_KEY_PERIPHERAL_SERVER_MODE_BLE_DEVICE_ADDRESS)?.asBstr
            return cm
        }

        // Constants related to Bluetooth
        //
        // Reference: https://www.bluetooth.com/specifications/assigned-numbers/
        //
        private const val BLE_LE_COMPLETE_LIST_OF_128_BIT_SERVICE_CLASS_UUIDS: UByte = 0x07u
        private const val BLE_LE_BLUETOOTH_MAC_ADDRESS: UByte = 0x1Bu
        private const val BLE_LE_ROLE: UByte = 0x1Cu
        private const val BLE_PSM_NOT_YET_ALLOCATED: UByte = 0x77u  // TODO: allocated this number (0x77) with Bluetooth SIG

        // Bluetooth LE role constants
        //
        // Reference: https://www.bluetooth.com/specifications/specs/core-specification-supplement-9/
        //
        // See section 1.17.2 for values.
        //
        private const val BLE_LE_ROLE_CENTRAL_CLIENT_ROLE_ONLY: UByte = 0x00u
        private const val BLE_LE_ROLE_PERIPHERAL_ROLE_ONLY: UByte = 0x01u
        private const val BLE_LE_ROLE_PERIPHERAL_CENTRAL_ROLES_PERIPHERAL_PREFERRED: UByte = 0x02u
        private const val BLE_LE_ROLE_PERIPHERAL_CENTRAL_ROLES_CENTRAL_PREFERRED: UByte = 0x03u

        internal fun fromNdefRecord(
            record: NdefRecord,
            role: MdocTransport.Role,
            uuidToReplace: UUID?
        ): ConnectionMethodBle? {
            var centralClient = false
            var peripheral = false
            var uuid: UUID? = uuidToReplace
            var gotLeRole = false
            var psm : Int? = null
            var macAddress: ByteString? = null

            // See createNdefRecords() method for how this data is encoded.
            //
            val reader = ByteDataReader(record.payload)
            while (!reader.exhausted()) {
                val len = reader.getUInt8()
                val type = reader.getUInt8()
                if (type == BLE_LE_ROLE && len == 2.toUByte()) {
                    gotLeRole = true
                    when (val value = reader.getUInt8()) {
                        BLE_LE_ROLE_CENTRAL_CLIENT_ROLE_ONLY -> {
                            if (role == MdocTransport.Role.MDOC) {
                                peripheral = true
                            } else {
                                centralClient = true
                            }
                        }
                        BLE_LE_ROLE_PERIPHERAL_ROLE_ONLY -> {
                            if (role == MdocTransport.Role.MDOC) {
                                centralClient = true
                            } else {
                                peripheral = true
                            }
                        }
                        BLE_LE_ROLE_PERIPHERAL_CENTRAL_ROLES_PERIPHERAL_PREFERRED,
                        BLE_LE_ROLE_PERIPHERAL_CENTRAL_ROLES_CENTRAL_PREFERRED -> {
                            centralClient = true
                            peripheral = true
                        }
                        else -> {
                            Logger.w(TAG, "Invalid value $value for LE role")
                            return null
                        }
                    }
                } else if (type == BLE_LE_COMPLETE_LIST_OF_128_BIT_SERVICE_CLASS_UUIDS) {
                    val uuidLen = len - 1u
                    if (uuidLen % 16u != 0u) {
                        Logger.w(TAG, "UUID len $uuidLen is not divisible by 16")
                        return null
                    }
                    // We only use the last UUID...
                    if (uuidLen > 16u) {
                        reader.skip((16u * (uuidLen / 16u - 1u)).toInt())
                    }
                    val lsb = reader.getUInt64Le()
                    val msb = reader.getUInt64Le()
                    uuid = UUID(msb, lsb)
                } else if (type == BLE_LE_BLUETOOTH_MAC_ADDRESS && len == 0x07.toUByte()) {
                    // MAC address
                    macAddress = reader.getByteString(6)
                } else if (type == BLE_PSM_NOT_YET_ALLOCATED && len == 0x05.toUByte()) {
                    // PSM
                    psm = reader.getInt32()
                } else {
                    Logger.d(TAG, "Skipping unknown type $type of length $len")
                    reader.skip((len - 1u).toInt())
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
            cm.peripheralServerModeMacAddress = macAddress?.toByteArray()
            return cm
        }
    }

    override fun toNdefRecord(
        auxiliaryReferences: List<String>,
        role: MdocTransport.Role,
        skipUuids: Boolean
    ): Pair<NdefRecord, NdefRecord> {
        // The OOB data is defined in "Supplement to the Bluetooth Core Specification".
        //
        // See section 1.17.2 for values
        //
        val (uuid, leRole) =
            if (supportsCentralClientMode && supportsPeripheralServerMode) {
                check(centralClientModeUuid == peripheralServerModeUuid) {
                    "UUIDs for both BLE modes must be the same"
                }
                Pair(centralClientModeUuid, BLE_LE_ROLE_PERIPHERAL_CENTRAL_ROLES_CENTRAL_PREFERRED)
            } else if (supportsCentralClientMode) {
                Pair(
                    centralClientModeUuid,
                    when (role) {
                        MdocTransport.Role.MDOC -> BLE_LE_ROLE_PERIPHERAL_ROLE_ONLY
                        MdocTransport.Role.MDOC_READER -> BLE_LE_ROLE_CENTRAL_CLIENT_ROLE_ONLY
                    }
                )
            } else if (supportsPeripheralServerMode) {
                Pair(
                    peripheralServerModeUuid,
                    when (role) {
                        MdocTransport.Role.MDOC -> BLE_LE_ROLE_CENTRAL_CLIENT_ROLE_ONLY
                        MdocTransport.Role.MDOC_READER -> BLE_LE_ROLE_PERIPHERAL_ROLE_ONLY
                    }
                )
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
        val oobData = buildByteString {
            appendUInt8(0x02)  // Length
            appendUInt8(BLE_LE_ROLE)
            appendUInt8(leRole)
            if (uuid != null && !skipUuids) {
                appendUInt8(0x11) // Length
                appendUInt8(BLE_LE_COMPLETE_LIST_OF_128_BIT_SERVICE_CLASS_UUIDS)
                appendUInt64Le(uuid.leastSignificantBits)
                appendUInt64Le(uuid.mostSignificantBits)
            }
            val macAddress = peripheralServerModeMacAddress
            if (macAddress != null) {
                require(macAddress.size == 6) {
                    "MAC address should be six bytes, found ${macAddress.size}"
                }
                appendUInt8(0x07)
                appendUInt8(BLE_LE_BLUETOOTH_MAC_ADDRESS)
                appendByteArray(macAddress)
            }
            val psm = peripheralServerModePsm
            if (psm != null) {
                appendUInt8(0x05) // Length
                appendUInt8(BLE_PSM_NOT_YET_ALLOCATED)
                appendUInt32(psm)
            }
        }

        val record = NdefRecord(
            NdefRecord.Tnf.MIME_MEDIA,
            Nfc.MIME_TYPE_CONNECTION_HANDOVER_BLE.encodeToByteString(),
            "0".encodeToByteString(),
            oobData
        )

        // From NFC Forum Connection Handover Technical Specification section 7.1 Alternative Carrier Record
        //
        check(auxiliaryReferences.size < 0x100)
        val acRecordPayload = buildByteString {
            appendUInt8(0x01) // CPS: active
            appendUInt8(0x01) // Length of carrier data reference ("0")
            appendUInt8('0'.code) // Carrier data reference
            appendUInt8(auxiliaryReferences.size) // Number of auxiliary references
            for (auxRef in auxiliaryReferences) {
                // Each auxiliary reference consists of a single byte for the length and then as
                // many bytes for the reference itself.
                val auxRefUtf8 = auxRef.encodeToByteArray()
                check(auxRefUtf8.size < 0x100)
                appendUInt8(auxRefUtf8.size)
                appendByteArray(auxRefUtf8)
            }
        }
        val acRecord = NdefRecord(
            tnf = NdefRecord.Tnf.WELL_KNOWN,
            type = Nfc.RTD_ALTERNATIVE_CARRIER,
            payload = acRecordPayload
        )
        return Pair(record, acRecord)
    }
}
