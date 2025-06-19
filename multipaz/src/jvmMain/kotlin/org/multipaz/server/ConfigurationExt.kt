package org.multipaz.server

import org.multipaz.rpc.backend.Configuration

val Configuration.serverHost: String? get() {
    return getValue("server_host")
}

// must be set in default configuration
val Configuration.serverPort: Int get() =
    getValue("server_port")!!.toInt()

// derived from serverPort and serverHost if not set
val Configuration.baseUrl: String get() = getValue("base_url")
        ?: ("http://" + (serverHost ?: "localhost") + ":" + serverPort)

