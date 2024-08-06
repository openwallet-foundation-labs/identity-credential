package com.android.identity.issuance.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.RawRes
import com.android.identity.flow.server.Configuration
import com.android.identity.flow.server.Resources
import com.android.identity.flow.server.Storage
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.flow.handler.FlowNotifications
import com.android.identity.securearea.SecureArea
import com.android.identity_credential.wallet.R
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import kotlinx.io.bytestring.ByteString
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlin.reflect.KClass
import kotlin.reflect.cast


/**
 * This implementation of [FlowEnvironment] can be used to run wallet server locally in the app,
 * which is useful for development, but should never be done in production.
 */
class LocalDevelopmentEnvironment(
    context: Context,
    private val secureArea: SecureArea,
    private val notifications: FlowNotifications
) : FlowEnvironment {
    private val configuration = ConfigurationImpl(context)
    private val storage = StorageImpl(context, "dev_local_data")
    private val resources = ResourcesImpl(context)
    private val httpClient = HttpClient(Android)

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

    class ConfigurationImpl(val context: Context): Configuration {
        override fun getValue(key: String): String? {
            val value = when (key) {
                "developerMode" -> "true"
                "waitForNotificationSupported" -> "false"
                "androidRequireGmsAttestation" -> "false"
                "androidRequireVerifiedBootGreen" -> "false"
                "androidRequireAppSignatureCertificateDigests" -> ""
                "issuingAuthorityList" -> "utopia_local utopia_local_pid"
                "issuingAuthority.utopia_local.name" -> "Utopia DMV (Local)"
                "issuingAuthority.utopia_local.type" -> "DrivingLicense"
                "issuingAuthority.utopia_local.description" -> "Utopia Driver's License (Local)"
                "issuingAuthority.utopia_local.logo" -> "utopia_local/logo.png"
                "issuingAuthority.utopia_local.cardArt" -> "utopia_local/card_art.png"
                "issuingAuthority.utopia_local.requireUserAuthenticationToViewDocument" -> "false"
                "issuingAuthority.utopia_local_pid.name" -> "Utopia Gov (Local)"
                "issuingAuthority.utopia_local_pid.type" -> "EuPid"
                "issuingAuthority.utopia_local_pid.description" -> "Utopia Personal ID (Local)"
                "issuingAuthority.utopia_local_pid.logo" -> "utopia_local_pid/logo.png"
                "issuingAuthority.utopia_local_pid.cardArt" -> "utopia_local_pid/card_art.png"
                "issuingAuthority.utopia_local_pid.requireUserAuthenticationToViewDocument" -> "false"
                else -> null
            }
            return value
        }

    }

    class ResourcesImpl(val context: Context): Resources {
        override fun getRawResource(name: String): ByteString? {
            return when(name) {
                "experiment_icon.svg" ->
                    ByteString(getRawResourceAsBytes(R.raw.experiment_icon))
                "utopia_local/card_art.png" ->
                    bitmapData(
                        R.drawable.utopia_driving_license_card_art,
                        Bitmap.CompressFormat.PNG
                    )
                "utopia_local_pid/card_art.png" ->
                    bitmapData(
                        R.drawable.utopia_pid_card_art,
                        Bitmap.CompressFormat.PNG
                    )
                "funke/card_art.png"  ->
                    bitmapData(
                        R.drawable.funke_card_art,
                        Bitmap.CompressFormat.PNG
                    )
                "utopia_local/logo.png" ->
                    bitmapData(
                        R.drawable.utopia_dmv_issuing_authority_logo,
                        Bitmap.CompressFormat.PNG
                    )
                "utopia_local_pid/logo.png" ->
                    bitmapData(
                        R.drawable.utopia_pid_issuing_authority_logo,
                        Bitmap.CompressFormat.PNG
                    )
                "funke/logo.png" -> bitmapData(
                    R.drawable.funke_logo,
                    Bitmap.CompressFormat.PNG
                )
                "img_erika_portrait.jpf" ->
                    ByteString(getRawResourceAsBytes(R.raw.img_erika_portrait))
                "img_erika_signature.jpf" ->
                    ByteString(getRawResourceAsBytes(R.raw.img_erika_signature))
                "img_erika_portrait.jpg" ->
                    bitmapData(
                        R.drawable.img_erika_portrait,
                        Bitmap.CompressFormat.JPEG
                    )
                "img_erika_signature.jpg" ->
                    bitmapData(
                        R.drawable.img_erika_signature,
                        Bitmap.CompressFormat.JPEG
                    )
                else -> null
            }
        }

        override fun getStringResource(name: String): String? {
            return when(name) {
                "ds_private_key.pem" -> getRawResourceAsString(R.raw.ds_private_key)
                "ds_certificate.pem" -> getRawResourceAsString(R.raw.ds_certificate)
                "utopia_local/tos.html" ->
                    context.resources.getString(R.string.utopia_local_issuing_authority_tos)
                "utopia_local_pid/tos.html" ->
                    context.resources.getString(R.string.utopia_local_issuing_authority_pid_tos)
                else -> null
            }
        }

        private fun getRawResourceAsString(@RawRes resourceId: Int): String {
            return String(getRawResourceAsBytes(resourceId), StandardCharsets.UTF_8)
        }

        private fun getRawResourceAsBytes(@RawRes resourceId: Int): ByteArray {
            return context.resources.openRawResource(resourceId).readBytes()
        }

        private fun bitmapData(resourceId: Int, format: Bitmap.CompressFormat): ByteString {
            val baos = ByteArrayOutputStream()
            BitmapFactory.decodeResource(context.resources, resourceId)
                .compress(format, 90, baos)
            return ByteString(baos.toByteArray())
        }
    }
}