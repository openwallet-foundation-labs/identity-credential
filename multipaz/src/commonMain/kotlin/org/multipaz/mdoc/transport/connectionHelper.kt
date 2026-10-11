package org.multipaz.mdoc.transport

import org.multipaz.crypto.EcPublicKey
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.role.MdocRole

/**
 * A helper for advertising a number of connections to a remote peer.
 *
 * For each [MdocConnectionMethod] this creates a [MdocTransport] which is advertised and opened.
 *
 * @param role the role to use when creating connections.
 * @param transportFactory the [MdocTransportFactory] used to create [MdocTransport] instances.
 * @param options the [MdocTransportOptions] to use when creating [MdocTransport] instances.
 * @return a list of [MdocTransport] methods that are being advertised.
 */
suspend fun List<MdocConnectionMethod>.advertise(
    role: MdocRole,
    transportFactory: MdocTransportFactory,
    options: MdocTransportOptions
): List<MdocTransport> {
    val transports = mutableListOf<MdocTransport>()
    for (connectionMethod in this) {
        val transport = transportFactory.createTransport(
            connectionMethod,
            role,
            options
        )
        transport.advertise()
        transports.add(transport)
    }
    return transports
}
