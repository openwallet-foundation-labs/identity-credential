package org.multipaz.backend.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

/**
 * Main entry point to launch the server.
 *
 * Build and start the server using
 *
 * ```./gradlew multipaz-backend-server:run```
 */
class Main {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val configuration = ServerConfiguration(args)
            val port = (configuration.getValue("serverPort") ?: "8008").toInt()
            val host = configuration.getValue("serverHost") ?: "0.0.0.0"
            embeddedServer(Netty, port = port, host = host, module = {
                configureRouting(configuration)
            }).start(wait = true)
        }
    }
}