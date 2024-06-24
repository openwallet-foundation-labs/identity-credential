package com.android.identity.mdoc.connectionmethod

import com.android.identity.cbor.Cbor.decode
import com.android.identity.util.Logger
import com.android.identity.util.UUID

/**
 * A class representing the ConnectionMethod structure exchanged between mdoc and mdoc reader.
 *
 * This is an abstract class - applications are expected to interact with concrete
 * implementations, for example [ConnectionMethodBle] or [ConnectionMethodNfc].
 */
abstract class ConnectionMethod {
    /**
     * Generates `DeviceRetrievalMethod` CBOR for the given [ConnectionMethod].
     *
     * See ISO/IEC 18013-5:2021 section 8.2.1.1 Device engagement structure for where
     * `DeviceRetrievalMethod` CBOR is defined.
     *
     * This is the reverse operation of [.fromDeviceEngagement].
     */
    abstract fun toDeviceEngagement(): ByteArray

    companion object {
        private const val TAG = "ConnectionMethod"

        /**
         * Constructs a new [ConnectionMethod] from `DeviceRetrievalMethod` CBOR.
         *
         *
         * See ISO/IEC 18013-5:2021 section 8.2.1.1 Device engagement structure for where
         * `DeviceRetrievalMethod` CBOR is defined.
         *
         *
         * This is the reverse operation of [.toDeviceEngagement].
         *
         * @param encodedDeviceRetrievalMethod the bytes of `DeviceRetrievalMethod` CBOR.
         * @return A [ConnectionMethod]-derived instance or `null` if the method
         * isn't supported.
         * @throws IllegalArgumentException if the given CBOR is malformed.
         */
        fun fromDeviceEngagement(encodedDeviceRetrievalMethod: ByteArray): ConnectionMethod? {
            val array = decode(encodedDeviceRetrievalMethod)
            val type = array[0].asNumber
            when (type) {
                ConnectionMethodNfc.METHOD_TYPE -> return ConnectionMethodNfc.fromDeviceEngagementNfc(
                    encodedDeviceRetrievalMethod
                )

                ConnectionMethodBle.METHOD_TYPE -> return ConnectionMethodBle.fromDeviceEngagementBle(
                    encodedDeviceRetrievalMethod
                )

                ConnectionMethodWifiAware.METHOD_TYPE -> return ConnectionMethodWifiAware.fromDeviceEngagementWifiAware(
                    encodedDeviceRetrievalMethod
                )

                ConnectionMethodHttp.METHOD_TYPE -> return ConnectionMethodHttp.fromDeviceEngagementHttp(
                    encodedDeviceRetrievalMethod
                )
            }
            Logger.w(TAG, "Unsupported ConnectionMethod type $type in DeviceEngagement")
            return null
        }

        /**
         * Combines connection methods.
         *
         * Given a list of connection methods, produce a list where similar methods are combined
         * into a single one. This is currently only applicable for BLE and one requirement is that
         * all method instances share the same UUID. If this is not the case,
         * [IllegalArgumentException] is thrown.
         *
         * This is the reverse of [.disambiguate].
         *
         * @param connectionMethods a list of connection methods.
         * @return the given list of connection methods where similar methods are combined into one.
         * @throws IllegalArgumentException if some methods couldn't be combined
         */
        fun combine(connectionMethods: List<ConnectionMethod>): List<ConnectionMethod> {
            val result = mutableListOf<ConnectionMethod>()

            // Don't change the order if there is nothing to combine.
            var numBleMethods = 0
            for (cm in connectionMethods) {
                if (cm is ConnectionMethodBle) {
                    numBleMethods += 1
                }
            }
            if (numBleMethods <= 1) {
                result.addAll(connectionMethods)
                return result
            }
            val bleMethods: MutableList<ConnectionMethodBle> = ArrayList()
            for (cm in connectionMethods) {
                if (cm is ConnectionMethodBle) {
                    bleMethods.add(cm)
                } else {
                    result.add(cm)
                }
            }
            if (bleMethods.size > 0) {
                var supportsPeripheralServerMode = false
                var supportsCentralClientMode = false
                var uuid: UUID? = null
                for (ble in bleMethods) {
                    if (ble.supportsPeripheralServerMode) {
                        supportsPeripheralServerMode = true
                    }
                    if (ble.supportsCentralClientMode) {
                        supportsCentralClientMode = true
                    }
                    val c = ble.centralClientModeUuid
                    val p = ble.peripheralServerModeUuid
                    if (uuid == null) {
                        if (c != null) {
                            uuid = c
                        } else if (p != null) {
                            uuid = p
                        }
                    } else {
                        require(!(c != null && uuid != c)) { "UUIDs for both BLE modes are not the same" }
                        require(!(p != null && uuid != p)) { "UUIDs for both BLE modes are not the same" }
                    }
                }
                result.add(
                    ConnectionMethodBle(
                        supportsPeripheralServerMode,
                        supportsCentralClientMode,
                        if (supportsPeripheralServerMode) uuid else null,
                        if (supportsCentralClientMode) uuid else null
                    )
                )
            }
            return result
        }

        /**
         * Disambiguate a list of connection methods.
         *
         * Given a list of connection methods, produce a list where each method represents exactly
         * one connectable endpoint. For example, for BLE if both central client mode and peripheral
         * server mode is set, replaces this with two connection methods so it's clear which one is
         * which.
         *
         * This is the reverse of [.combine].
         *
         * @param connectionMethods a list of connection methods.
         * @return the given list of connection methods where each instance is unambiguously refers
         * to one and only one connectable endpoint.
         */
        fun disambiguate(connectionMethods: List<ConnectionMethod>): List<ConnectionMethod> {
            val result = mutableListOf<ConnectionMethod>()
            for (cm in connectionMethods) {
                // Only BLE needs disambiguation
                if (cm is ConnectionMethodBle) {
                    val cmBle = cm
                    if (cmBle.supportsCentralClientMode && cmBle.supportsPeripheralServerMode) {
                        result.add(
                            ConnectionMethodBle(
                                false,
                                true,
                                null,
                                cmBle.centralClientModeUuid
                            )
                        )
                        result.add(
                            ConnectionMethodBle(
                                true,
                                false,
                                cmBle.peripheralServerModeUuid,
                                null
                            )
                        )
                        continue
                    }
                }
                result.add(cm)
            }
            return result
        }
    }
}
