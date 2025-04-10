package org.multipaz.server

import org.multipaz.rpc.handler.RpcNotifications
import org.multipaz.rpc.backend.Configuration
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Resources
import org.multipaz.securearea.SecureAreaProvider
import org.multipaz.securearea.software.SoftwareSecureArea
import org.multipaz.storage.Storage
import org.multipaz.storage.jdbc.JdbcStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import kotlinx.coroutines.Dispatchers
import org.multipaz.rpc.handler.RpcAuthInspector
import org.multipaz.rpc.handler.RpcAuthInspectorAssertion
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.cast

internal class ServerEnvironment(
    servletConfiguration: Configuration,
    environmentInitializer: (env: BackendEnvironment) -> RpcNotifications? = { null }
) : BackendEnvironment {
    private val configuration: Configuration = object : Configuration {
        override fun getValue(key: String): String? {
            return servletConfiguration.getValue(key) ?: CommonConfiguration.getValue(key)
        }
    }
    private var notifications: RpcNotifications? = environmentInitializer(this)

    override fun <T : Any> getInterface(clazz: KClass<T>): T? {
        return clazz.cast(when(clazz) {
            Configuration::class -> configuration
            Resources::class -> ServerResources
            Storage::class -> storage
            RpcNotifications::class -> notifications
            HttpClient::class -> httpClient
            SecureAreaProvider::class -> secureAreaProvider
            RpcAuthInspector::class -> RpcAuthInspectorAssertion.Default
            else -> return null
        })
    }
}

private val storage = JdbcStorage(
    CommonConfiguration.getValue("databaseConnection") ?: defaultDatabase(),
    CommonConfiguration.getValue("databaseUser") ?: "",
    CommonConfiguration.getValue("databasePassword") ?: ""
)

fun defaultDatabase(): String {
    val dbFile = File("environment/db/db.hsqldb").absoluteFile
    if (!dbFile.canRead()) {
        val parent = File(dbFile.parent)
        if (!parent.exists()) {
            if (!parent.mkdirs()) {
                throw Exception("Cannot create database folder ${parent.absolutePath}")
            }
        }
    }
    return "jdbc:hsqldb:file:${dbFile.absolutePath}"
}

private val httpClient = HttpClient(Java) {
    followRedirects = false
}

private val secureAreaProvider = SecureAreaProvider(Dispatchers.Default) {
    SoftwareSecureArea.create(storage)
}

