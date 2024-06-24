package com.android.identity.wallet.server

import com.android.identity.flow.handler.FlowNotifications
import com.android.identity.flow.server.Configuration
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.flow.server.Resources
import com.android.identity.flow.server.Storage
import com.android.identity.issuance.WalletServerSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.java.Java
import java.io.File
import jakarta.servlet.ServletConfig
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
    private val httpClient = HttpClient(Java)
    internal var notifications: FlowNotifications? = null

    override fun <T : Any> getInterface(clazz: KClass<T>): T? {
        return clazz.cast(when(clazz) {
            Configuration::class -> configuration
            Resources::class -> resources
            Storage::class -> storage
            FlowNotifications::class -> notifications
            HttpClient::class -> httpClient
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
}