package org.multipaz.mdoc.transport

import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.role.MdocRole

/**
 * An interface used to create [MdocTransport] instances.
 */
interface MdocTransportFactory {
    /**
     * Creates a new [MdocTransport].
     *
     * @param connectionMethod the address to listen on or connect to.
     * @param role the role of the [MdocTransport].
     * @param options options for the [MdocTransport].
     * @return A [MdocTransport] instance, ready to be used.
     */
    fun createTransport(
        connectionMethod: MdocConnectionMethod,
        role: MdocRole,
        options: MdocTransportOptions = MdocTransportOptions()
    ): MdocTransport

    /**
     * The default factory for the platform.
     */
    object Default : MdocTransportFactory {
        override fun createTransport(
            connectionMethod: MdocConnectionMethod,
            role: MdocRole,
            options: MdocTransportOptions
        ): MdocTransport = defaultMdocTransportFactoryCreateTransport(connectionMethod, role, options)
    }
}

internal expect fun defaultMdocTransportFactoryCreateTransport(
    connectionMethod: MdocConnectionMethod,
    role: MdocRole,
    options: MdocTransportOptions
): MdocTransport