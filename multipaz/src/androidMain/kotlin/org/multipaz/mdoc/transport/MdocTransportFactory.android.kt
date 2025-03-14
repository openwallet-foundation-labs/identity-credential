package org.multipaz.mdoc.transport

import org.multipaz.mdoc.connectionmethod.ConnectionMethod
import org.multipaz.mdoc.connectionmethod.ConnectionMethodBle
import org.multipaz.mdoc.connectionmethod.ConnectionMethodNfc

internal actual fun defaultMdocTransportFactoryCreateTransport(
    connectionMethod: ConnectionMethod,
    role: MdocTransport.Role,
    options: MdocTransportOptions
): MdocTransport {
    when (connectionMethod) {
        is ConnectionMethodBle -> {
            if (connectionMethod.supportsCentralClientMode &&
                connectionMethod.supportsPeripheralServerMode) {
                throw IllegalArgumentException(
                    "Only Central Client or Peripheral Server mode is supported at one time, not both"
                )
            } else if (connectionMethod.supportsCentralClientMode) {
                return when (role) {
                    MdocTransport.Role.MDOC -> {
                        BleTransportCentralMdoc(
                            role,
                            options,
                            BleCentralManagerAndroid(),
                            connectionMethod.centralClientModeUuid!!,
                            connectionMethod.peripheralServerModePsm
                        )
                    }
                    MdocTransport.Role.MDOC_READER -> {
                        BleTransportCentralMdocReader(
                            role,
                            options,
                            BlePeripheralManagerAndroid(),
                            connectionMethod.centralClientModeUuid!!
                        )
                    }
                }
            } else {
                return when (role) {
                    MdocTransport.Role.MDOC -> {
                        BleTransportPeripheralMdoc(
                            role,
                            options,
                            BlePeripheralManagerAndroid(),
                            connectionMethod.peripheralServerModeUuid!!
                        )
                    }
                    MdocTransport.Role.MDOC_READER -> {
                        BleTransportPeripheralMdocReader(
                            role,
                            options,
                            BleCentralManagerAndroid(),
                            connectionMethod.peripheralServerModeUuid!!,
                            connectionMethod.peripheralServerModePsm,
                        )
                    }
                }
            }
        }
        is ConnectionMethodNfc -> {
            return when (role) {
                MdocTransport.Role.MDOC -> {
                    NfcTransportMdoc(
                        role,
                        options,
                        connectionMethod
                    )
                }
                MdocTransport.Role.MDOC_READER -> {
                    NfcTransportMdocReader(
                        role,
                        options,
                        connectionMethod
                    )
                }
            }
        }
        else -> {
            throw IllegalArgumentException("$connectionMethod is not supported")
        }
    }
}
