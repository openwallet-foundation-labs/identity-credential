package org.multipaz.verifier.server

import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import org.multipaz.server.ServerConfiguration
import org.multipaz.util.Logger

/**
 * Main entry point to launch the server.
 *
 * Build and start the server using
 *
 * ```./gradlew multipaz-verifier-server:run```
 */
class Main {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val configuration = ServerConfiguration(args)
            val jdbc = configuration.getValue("databaseConnection")
            if (jdbc != null) {
                if (jdbc.startsWith("jdbc:mysql:")) {
                    Logger.i("Main", "SQL driver: ${com.mysql.cj.jdbc.Driver()}")
                } else if (jdbc.startsWith("jdbc:postgresql:")) {
                    Logger.i("Main", "SQL driver: ${org.postgresql.Driver()}")
                }
            }
            val port = (configuration.getValue("serverPort") ?: "8006").toInt()
            val host = configuration.getValue("serverHost") ?: "0.0.0.0"
            embeddedServer(Netty, port = port, host = host, module = {
                install(CallLogging)
                configureRouting(configuration)
            }).start(wait = true)
        }
    }
}