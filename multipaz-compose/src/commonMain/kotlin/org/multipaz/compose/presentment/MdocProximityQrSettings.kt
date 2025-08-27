package org.multipaz.compose.presentment

import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.transport.MdocTransportOptions

/**
 * Settings to use when doing presentment with QR engagement according to ISO/IEC 18013-5:2021.
 *
 * @property availableConnectionMethods the connection methods to advertise in the QR code.
 * @property createTransportOptions the [MdocTransportOptions] to use when creating a new [MdocTransport]
 */
data class MdocProximityQrSettings(
    val availableConnectionMethods: List<MdocConnectionMethod>,
    val createTransportOptions: MdocTransportOptions
)
