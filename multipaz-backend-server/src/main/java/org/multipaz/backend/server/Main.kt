package org.multipaz.backend.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.multipaz.server.ServerConfiguration
import org.multipaz.server.serverHost
import org.multipaz.server.serverPort

/**
 * Main entry point to launch the Multipaz back-end server.
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
            val host = configuration.serverHost ?: "0.0.0.0"
            embeddedServer(Netty, port = configuration.serverPort, host = host, module = {
                configureRouting(configuration)
            }).start(wait = true)
        }
    }
}