package org.multipaz.mdoc.transport

/**
 * Options for using a [MdocTransport].
 *
 * @property bleUseL2CAP set to `true` to use L2CAP if available, `false` otherwise
 */
data class MdocTransportOptions(
    val bleUseL2CAP: Boolean = false
)