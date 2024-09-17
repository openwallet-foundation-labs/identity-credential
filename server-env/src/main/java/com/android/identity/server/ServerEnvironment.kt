package com.android.identity.server

import com.android.identity.flow.handler.FlowNotifications
import com.android.identity.flow.server.Configuration
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.flow.server.Resources
import com.android.identity.flow.server.Storage
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.software.SoftwareSecureArea
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import kotlin.reflect.KClass
import kotlin.reflect.cast

internal class ServerEnvironment(
    servletConfiguration: Configuration,
    environmentInitializer: (env: FlowEnvironment) -> FlowNotifications? = { null }
) : FlowEnvironment {
    private val configuration: Configuration = object : Configuration {
        override fun getValue(key: String): String? {
            return servletConfiguration.getValue(key) ?: CommonConfiguration.getValue(key)
        }
    }
    private var notifications: FlowNotifications? = environmentInitializer(this)

    override fun <T : Any> getInterface(clazz: KClass<T>): T? {
        return clazz.cast(when(clazz) {
            Configuration::class -> configuration
            Resources::class -> ServerResources
            Storage::class -> storage
            FlowNotifications::class -> notifications
            HttpClient::class -> httpClient
            SecureArea::class -> secureArea
            else -> return null
        })
    }
}

private val storage = ServerStorage(
    CommonConfiguration.getValue("databaseConnection") ?: ServerStorage.defaultDatabase(),
    CommonConfiguration.getValue("databaseUser") ?: "",
    CommonConfiguration.getValue("databasePassword") ?: ""
)

private val httpClient = HttpClient(Java) {
    followRedirects = false
}

private val secureArea = SoftwareSecureArea(SecureAreaStorageAdapter(storage))

