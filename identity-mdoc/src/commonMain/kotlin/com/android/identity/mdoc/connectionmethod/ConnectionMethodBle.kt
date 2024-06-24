package com.android.identity.mdoc.connectionmethod

import com.android.identity.cbor.Cbor.decode
import com.android.identity.cbor.Cbor.encode
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborMap
import com.android.identity.util.UUID

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
        val builder = CborMap.builder()
        builder.put(OPTION_KEY_SUPPORTS_PERIPHERAL_SERVER_MODE, supportsPeripheralServerMode)
        builder.put(OPTION_KEY_SUPPORTS_CENTRAL_CLIENT_MODE, supportsCentralClientMode)
        if (peripheralServerModeUuid != null) {
            builder.put(
                OPTION_KEY_PERIPHERAL_SERVER_MODE_UUID,
                peripheralServerModeUuid.toByteArray()
            )
        }
        if (centralClientModeUuid != null) {
            builder.put(
                OPTION_KEY_CENTRAL_CLIENT_MODE_UUID,
                centralClientModeUuid.toByteArray()
            )
        }
        if (peripheralServerModePsm != null) {
            builder.put(OPTION_KEY_PERIPHERAL_SERVER_MODE_PSM, peripheralServerModePsm as Int)
        }
        if (peripheralServerModeMacAddress != null) {
            builder.put(
                OPTION_KEY_PERIPHERAL_SERVER_MODE_BLE_DEVICE_ADDRESS,
                peripheralServerModeMacAddress!!
            )
        }
        return encode(
            CborArray.builder()
                .add(METHOD_TYPE)
                .add(METHOD_MAX_VERSION)
                .add(builder.end().build())
                .end()
                .build()
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
        private const val OPTION_KEY_PERIPHERAL_SERVER_MODE_PSM = 2023L // NOTE: not yet standardized

        fun fromDeviceEngagementBle(encodedDeviceRetrievalMethod: ByteArray): ConnectionMethodBle? {
            val array = decode(encodedDeviceRetrievalMethod)
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
    }
}
