package com.android.identity.mdoc.transport

import com.android.identity.mdoc.connectionmethod.ConnectionMethod

/**
 * A factory used to create [MdocTransport] instances.
 */
expect class MdocTransportFactory {
    companion object {
        /**
         * Creates a new [MdocTransport].
         *
         * @param connectionMethod the address to listen on or connect to.
         * @param role the role of the [MdocTransport].
         * @param options options for the [MdocTransport].
         * @return A [MdocTransport] instance, ready to be used.
         */
        fun createTransport(
            connectionMethod: ConnectionMethod,
            role: MdocTransport.Role,
            options: MdocTransportOptions = MdocTransportOptions()
        ): MdocTransport
    }
}