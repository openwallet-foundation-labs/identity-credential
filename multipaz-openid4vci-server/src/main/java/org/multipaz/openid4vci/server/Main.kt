package org.multipaz.openid4vci.server

import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import org.multipaz.server.ServerConfiguration
import org.multipaz.server.serverHost
import org.multipaz.server.serverPort

/**
 * Main entry point to launch the server.
 *
 * Build and start the server using
 *
 * ```./gradlew multipaz-openid4vci-server:run```
 */
class Main {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val configuration = ServerConfiguration(args)
            val host = configuration.serverHost ?: "0.0.0.0"
            embeddedServer(Netty, port = configuration.serverPort, host = host, module = {
                install(CallLogging)
                traceCalls(configuration)
                configureRouting(configuration)
            }).start(wait = true)
        }
    }
}