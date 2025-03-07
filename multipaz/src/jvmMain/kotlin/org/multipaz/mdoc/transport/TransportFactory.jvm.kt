package org.multipaz.mdoc.transport

import org.multipaz.mdoc.connectionmethod.ConnectionMethod

actual fun defaultMdocTransportFactoryCreateTransport(
    connectionMethod: ConnectionMethod,
    role: MdocTransport.Role,
    options: MdocTransportOptions
): MdocTransport {
    throw NotImplementedError("MdocTransportFactory is not available for JVM")
}
