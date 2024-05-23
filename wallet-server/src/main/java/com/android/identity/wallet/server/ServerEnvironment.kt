package com.android.identity.wallet.server

import com.android.identity.flow.environment.Configuration
import com.android.identity.flow.environment.Resources
import com.android.identity.flow.environment.Storage
import com.android.identity.flow.environment.FlowEnvironment
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.cast

class ServerEnvironment(private val directory: String) : FlowEnvironment {
    private val configuration = ServerConfiguration("$directory/settings.json")
    private val resources = ServerResources("$directory/resources")
    private val storage = ServerStorage(
        configuration.getProperty("database.connection") ?: defaultDatabase(),
        configuration.getProperty("database.user") ?: "",
        configuration.getProperty("database.password") ?: "")

    override fun <T : Any> getInterface(clazz: KClass<T>): T? {
        return clazz.cast(when(clazz) {
            Configuration::class -> configuration
            Resources::class -> resources
            Storage::class -> storage
            else -> return null
        })
    }

    private fun defaultDatabase(): String {
        val dbFile = File("$directory/db/db.hsqldb").absoluteFile
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