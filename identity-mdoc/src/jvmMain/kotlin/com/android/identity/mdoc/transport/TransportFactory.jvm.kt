package com.android.identity.mdoc.transport

import com.android.identity.mdoc.connectionmethod.ConnectionMethod

actual class MdocTransportFactory {
    actual companion object {
        actual fun createTransport(
            connectionMethod: ConnectionMethod,
            role: MdocTransport.Role,
            options: MdocTransportOptions,
        ): MdocTransport {
            throw NotImplementedError("MdocTransportFactory is not available for JVM")
        }
    }
}