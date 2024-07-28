package com.android.identity.wallet.server

import com.android.identity.flow.handler.FlowNotifications
import com.android.identity.flow.server.Configuration
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.flow.server.Resources
import com.android.identity.flow.server.Storage
import com.android.identity.issuance.WalletServerSettings
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.software.SoftwareSecureArea
import com.android.identity.storage.StorageEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.java.Java
import java.io.File
import jakarta.servlet.ServletConfig
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString
import kotlin.reflect.KClass
import kotlin.reflect.cast

class ServerEnvironment(
    servletConfig: ServletConfig,
) : FlowEnvironment {
    private val configuration = ServerConfiguration(servletConfig)
    private val settings = WalletServerSettings(configuration)
    private val resources = ServerResources()
    private val storage = ServerStorage(
        settings.databaseConnection ?: defaultDatabase(),
        settings.databaseUser ?: "",
        settings.databasePassword ?: "")
    private val httpClient = HttpClient(Java) {
        followRedirects = false
    }
    private val secureArea = SoftwareSecureArea(StorageAdapter(storage, "ServerKeys"))
    internal var notifications: FlowNotifications? = null

    override fun <T : Any> getInterface(clazz: KClass<T>): T? {
        return clazz.cast(when(clazz) {
            Configuration::class -> configuration
            Resources::class -> resources
            Storage::class -> storage
            FlowNotifications::class -> notifications
            HttpClient::class -> httpClient
            SecureArea::class -> secureArea
            else -> return null
        })
    }

    private fun defaultDatabase(): String {
        val dbFile = File("environment/db/db.hsqldb").absoluteFile
        if (!dbFile.canRead()) {
            val parent = File(dbFile.parent)
            if (!parent.exists()) {
                if  (!parent.mkdirs()) {
                    throw Exception("Cannot create database folder ${parent.absolutePath}")
                }
            }
        }
        return "jdbc:hsqldb:file:${dbFile.absolutePath}"
    }

    class StorageAdapter(private val storage: Storage, private val table: String) : StorageEngine {
        override fun get(key: String): ByteArray? {
            return runBlocking {
                storage.get(table, "", key)?.toByteArray()
            }
        }

        override fun put(key: String, data: ByteArray) {
            runBlocking {
                val dataBytes = ByteString(data)
                if (storage.get(table, "", key) == null) {
                    storage.insert(table, "", dataBytes, key)
                } else {
                    storage.update(table, "", key, dataBytes)
                }
            }
        }

        override fun delete(key: String) {
            runBlocking {
                storage.delete(table, "", key)
            }
        }

        override fun deleteAll() {
            runBlocking {
                for (key in storage.enumerate(table, "")) {
                    storage.delete(table, "", key)
                }
            }
        }

        override fun enumerate(): Collection<String> {
            return runBlocking {
                storage.enumerate(table, "")
            }
        }
    }
}