package com.android.identity.mdoc.transport

import com.android.identity.mdoc.connectionmethod.ConnectionMethod

actual fun defaultMdocTransportFactoryCreateTransport(
    connectionMethod: ConnectionMethod,
    role: MdocTransport.Role,
    options: MdocTransportOptions
): MdocTransport {
    throw NotImplementedError("MdocTransportFactory is not available for JVM")
}
