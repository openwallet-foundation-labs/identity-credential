package com.android.identity.testapp.provisioning.backend

import io.ktor.client.HttpClient
import io.ktor.client.engine.config
import kotlinx.io.bytestring.ByteString
import org.multipaz.device.DeviceAssertionMaker
import org.multipaz.provisioning.ApplicationSupport
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Configuration
import org.multipaz.rpc.backend.Resources
import org.multipaz.rpc.handler.NoopCipher
import org.multipaz.rpc.handler.RpcAuthInspector
import org.multipaz.rpc.handler.RpcAuthInspectorAssertion
import org.multipaz.rpc.handler.RpcNotifications
import org.multipaz.rpc.handler.RpcNotificationsLocal
import org.multipaz.rpc.handler.RpcNotifier
import org.multipaz.securearea.SecureAreaProvider
import org.multipaz.storage.Storage
import org.multipaz.testapp.platformHttpClientEngineFactory
import org.multipaz.testapp.platformSecureAreaProvider
import org.multipaz.testapp.platformStorage
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * [BackendEnvironment] implementation for running provisioning back-end locally in-app.
 */
class BackendEnvironmentLocal(
    applicationSupportProvider: () -> ApplicationSupportLocal,
    private val deviceAssertionMaker: DeviceAssertionMaker
): BackendEnvironment {
    private var configuration = ConfigurationImpl()
    private val storage = platformStorage()
    private val resources = ResourcesImpl()
    private val notificationsLocal = RpcNotificationsLocal(NoopCipher)
    private val httpClient = HttpClient(platformHttpClientEngineFactory()) {
        followRedirects = false
    }
    private val applicationSupportLocal by lazy(applicationSupportProvider)

    override fun <T : Any> getInterface(clazz: KClass<T>): T? {
        return clazz.cast(when(clazz) {
            Configuration::class -> configuration
            Resources::class -> resources
            Storage::class -> storage
            RpcNotifications::class -> notificationsLocal
            RpcNotifier::class -> notificationsLocal
            HttpClient::class -> httpClient
            SecureAreaProvider::class -> platformSecureAreaProvider()
            DeviceAssertionMaker::class -> deviceAssertionMaker
            ApplicationSupport::class -> applicationSupportLocal
            RpcAuthInspector::class -> RpcAuthInspectorAssertion.Default
            else -> return null
        })
    }

    // TODO: this should interface with the testapp settings
    class ConfigurationImpl: Configuration {
        override fun getValue(key: String): String? {
            val value = when (key) {
                "developerMode" -> "true"
                "waitForNotificationSupported" -> "false"
                "androidRequireGmsAttestation" -> "false"
                "androidRequireVerifiedBootGreen" -> "false"
                "androidRequireAppSignatureCertificateDigests" -> ""
                "cloudSecureAreaUrl" -> "http://localhost:8080/server/csa"
                else -> null
            }
            return value
        }

    }

    class ResourcesImpl: Resources {
        override fun getRawResource(name: String): ByteString? {
            return null
        }

        override fun getStringResource(name: String): String? {
            return when (name) {
                "generic/tos.html" ->
                    """
                        <h2>Provisioning ${'$'}ID_NAME credential</h2>
                        <p>In the following screens, information will be collected
                          to provision <b>${'$'}ID_NAME</b> from <b>${'$'}ISSUER_NAME</b>.</p>
                        <p>The created <b>${'$'}ID_NAME</b> will be bound to this device.</p>
                    """.trimIndent()
                else -> null
            }
        }
    }
}