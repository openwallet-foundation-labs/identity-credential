package com.android.identity.issuance.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.RawRes
import com.android.identity.flow.environment.Configuration
import com.android.identity.flow.environment.Resources
import com.android.identity.flow.environment.Storage
import com.android.identity.flow.environment.FlowEnvironment
import com.android.identity.flow.environment.Notifications
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Timer
import kotlin.concurrent.schedule
import kotlin.reflect.KClass
import kotlin.reflect.cast


/**
 * This implementation of [FlowEnvironment] can be used to run wallet server locally in the app,
 * which is useful for development, but should never be done in production.
 */
class LocalDevelopmentEnvironment(
    context: Context,
    walletServerProvider: WalletServerProvider
) : FlowEnvironment {
    private val configuration = ConfigurationImpl(context)
    private val storage = StorageImpl(context, "dev_local_data")
    private val resources = ResourcesImpl(context)
    private val notifications = NotificationsImpl(walletServerProvider)

    override fun <T : Any> getInterface(clazz: KClass<T>): T? {
        return clazz.cast(when(clazz) {
            Configuration::class -> configuration
            Resources::class -> resources
            Storage::class -> storage
            Notifications::class -> notifications
            else -> return null
        })
    }

    class ConfigurationImpl(val context: Context): Configuration {
        override fun getProperty(key: String): String? {
            return when (key) {
                "developerMode" -> "true"
                "android.requireGmsAttestation" -> "false"
                "android.requireVerifiedBootGreen" -> "false"
                "android.requireAppSignatureCertificateDigests" -> "[]"
                "issuing_authority_list" -> "[\"utopia_local\"]"
                "issuing_authorities.utopia_local.name" -> "Utopia DMV (Local)"
                "issuing_authorities.utopia_local.description" -> "Utopia Driver's License (Local)"
                "issuing_authorities.utopia_local.logo" -> "utopia_local/logo.png"
                "issuing_authorities.utopia_local.card_art" -> "utopia_local/card_art.png"
                "issuing_authorities.utopia_local.require_user_authentication_to_view_document" -> "false"
                else -> null
            }
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
                "utopia_local/logo.png" ->
                    bitmapData(
                        R.drawable.utopia_dmv_issuing_authority_logo,
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

    class NotificationsImpl(
        private val walletServerProvider: WalletServerProvider
    ): Notifications {
        override suspend fun emitNotification(
            clientId: String,
            issuingAuthorityIdentifier: String,
            documentIdentifier: String
        ) {
            Timer().schedule(1L) {
                CoroutineScope(Dispatchers.IO).launch {
                    Logger.w(
                        TAG, "emitNotification - " +
                                "clientId:$clientId " +
                                "issuingAuthorityIdentifier:$issuingAuthorityIdentifier " +
                                "documentIdentifier:$documentIdentifier"
                    )
                    walletServerProvider._eventFlow.emit(
                        Pair(
                            issuingAuthorityIdentifier,
                            documentIdentifier
                        )
                    )
                }
            }
        }

        companion object {
            private const val TAG = "NotificationsImpl"
        }
    }
}