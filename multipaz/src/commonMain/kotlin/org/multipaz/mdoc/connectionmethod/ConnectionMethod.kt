package org.multipaz.mdoc.connectionmethod

import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Cbor.decode
import org.multipaz.mdoc.transport.MdocTransport
import org.multipaz.nfc.NdefRecord
import org.multipaz.nfc.Nfc
import org.multipaz.util.Logger
import org.multipaz.util.UUID
import kotlinx.io.bytestring.decodeToString

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

    /**
     * Creates a NDEF Connection Handover Carrier Reference record and Auxiliary Data Reference records.
     *
     * @param auxiliaryReferences A list of references to include in the Alternative Carrier Record
     * @param role If [MdocTransport.Role.MDOC] the generated records will be for the Handover Select message, otherwise
     * for Handover  Request.
     * @param skipUuids if `true`, UUIDs will not be included in the record
     * @return The NDEF record and the Alternative Carrier record or null if NFC handover is not supported.
     */
    abstract fun toNdefRecord(
        auxiliaryReferences: List<String>,
        role: MdocTransport.Role,
        skipUuids: Boolean
    ): Pair<NdefRecord, NdefRecord>?

    companion object {
        private const val TAG = "ConnectionMethod"

        /**
         * Constructs a new [ConnectionMethod] from `DeviceRetrievalMethod` CBOR.
         *
         * See ISO/IEC 18013-5:2021 section 8.2.1.1 Device engagement structure for where
         * `DeviceRetrievalMethod` CBOR is defined.
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
                ConnectionMethodNfc.METHOD_TYPE -> return ConnectionMethodNfc.fromDeviceEngagement(
                    encodedDeviceRetrievalMethod
                )

                ConnectionMethodBle.METHOD_TYPE -> return ConnectionMethodBle.fromDeviceEngagement(
                    encodedDeviceRetrievalMethod
                )

                ConnectionMethodWifiAware.METHOD_TYPE -> return ConnectionMethodWifiAware.fromDeviceEngagement(
                    encodedDeviceRetrievalMethod
                )

                ConnectionMethodHttp.METHOD_TYPE -> return ConnectionMethodHttp.fromDeviceEngagement(
                    encodedDeviceRetrievalMethod
                )
            }
            Logger.w(TAG, "Unsupported ConnectionMethod type $type in DeviceEngagement")
            return null
        }

        /**
         * Constructs a new [ConnectionMethod] from a NFC record.
         *
         * @param role If [MdocTransport.Role.MDOC] the record is from a Handover Select message,
         * [MdocTransport.Role.MDOC_READER] if from a for Handover Request message.
         * @param uuid If a UUID in a record isn't available, use this instead.
         * @returns the decoded method or `null` if the method isn't supported.
         */
        fun fromNdefRecord(
            record: NdefRecord,
            role: MdocTransport.Role,
            uuid: UUID?
        ): ConnectionMethod? {
            if (record.tnf == NdefRecord.Tnf.MIME_MEDIA &&
                record.type.decodeToString() == Nfc.MIME_TYPE_CONNECTION_HANDOVER_BLE &&
                record.id.decodeToString() == "0") {
                return ConnectionMethodBle.fromNdefRecord(record, role, uuid)
            } else if (record.tnf == NdefRecord.Tnf.EXTERNAL_TYPE &&
                record.type.decodeToString() == Nfc.EXTERNAL_TYPE_ISO_18013_5_NFC &&
                record.id.decodeToString() == "nfc") {
                return ConnectionMethodNfc.fromNdefRecord(record, role)
            }
            // TODO: add support for Wifi Aware and others.
            Logger.w(TAG, "No support for NDEF record $record")
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
            if (bleMethods.size == 0) {
                return result
            }
            var supportsPeripheralServerMode = false
            var supportsCentralClientMode = false
            var uuid: UUID? = null
            var mac: ByteString? = null
            var psm: Int? = null
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
                if (mac == null && ble.peripheralServerModeMacAddress != null) {
                    mac = ble.peripheralServerModeMacAddress
                }
                if (psm == null && ble.peripheralServerModePsm != null) {
                    psm = ble.peripheralServerModePsm
                }
            }
            val combined =
                ConnectionMethodBle(
                    supportsPeripheralServerMode,
                    supportsCentralClientMode,
                    if (supportsPeripheralServerMode) uuid else null,
                    if (supportsCentralClientMode) uuid else null
                )
            combined.peripheralServerModeMacAddress = mac
            combined.peripheralServerModePsm = psm
            return listOf(combined) + result
        }

        /**
         * Disambiguate a list of connection methods.
         *
         * Given a list of connection methods, produce a list where each method represents exactly
         * one connectable endpoint. For example, for BLE if both central client mode and peripheral
         * server mode is set, replaces this with two connection methods so it's clear which one is
         * which.
         *
         * The [ConnectionMethodBle.peripheralServerModePsm] and [ConnectionMethodBle.peripheralServerModeMacAddress]
         * properties are duplicated in each of the resulting [ConnectionMethodBle] instances. The PSM
         * and MAC address is only conveyed on one of the instances, see [role] argument for which one.
         *
         * This is the reverse of [.combine].
         *
         * @param connectionMethods a list of connection methods.
         * @param role If [MdocTransport.Role.MDOC] the PSM and MAC address will appear only in the resulting
         *   [ConnectionMethodBle] objects for central client mode, otherwise they only appear in the object for
         *   peripheral server mode.
         * @return the given list of connection methods where each instance is unambiguously refers
         * to one and only one connectable endpoint.
         */
        fun disambiguate(
            connectionMethods: List<ConnectionMethod>,
            role: MdocTransport.Role,
        ): List<ConnectionMethod> {
            val result = mutableListOf<ConnectionMethod>()
            for (cm in connectionMethods) {
                // Only BLE needs disambiguation
                if (cm is ConnectionMethodBle) {
                    val cmBle = cm
                    if (cmBle.supportsCentralClientMode && cmBle.supportsPeripheralServerMode) {
                        val centralClientMode =
                            ConnectionMethodBle(
                                false,
                                true,
                                null,
                                cmBle.centralClientModeUuid
                            )
                        if (role == MdocTransport.Role.MDOC) {
                            centralClientMode.peripheralServerModeMacAddress = cmBle.peripheralServerModeMacAddress
                            centralClientMode.peripheralServerModePsm = cmBle.peripheralServerModePsm
                        }
                        result.add(centralClientMode)
                        val peripheralServerMode =
                            ConnectionMethodBle(
                                true,
                                false,
                                cmBle.peripheralServerModeUuid,
                                null
                            )
                        if (role == MdocTransport.Role.MDOC_READER) {
                            peripheralServerMode.peripheralServerModeMacAddress = cmBle.peripheralServerModeMacAddress
                            peripheralServerMode.peripheralServerModePsm = cmBle.peripheralServerModePsm
                        }
                        result.add(peripheralServerMode)
                        continue
                    }
                }
                result.add(cm)
            }
            return result
        }
    }
}