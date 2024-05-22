package com.android.identity.issuance.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.RawRes
import com.android.identity.flow.environment.Resources
import com.android.identity.flow.environment.Storage
import com.android.identity.flow.environment.FlowEnvironment
import com.android.identity_credential.wallet.R
import kotlinx.io.bytestring.ByteString
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlin.reflect.KClass
import kotlin.reflect.cast


/**
 * This implementation of [FlowEnvironment] can be used to run wallet server locally in the app,
 * which is useful for development, but should never be done in production.
 */
class LocalDevelopmentEnvironment(context: Context) : FlowEnvironment {
    private val storage = StorageImpl(context, "dev_local_data")
    private val resources = ResourcesImpl(context)

    override fun <T : Any> getInterface(clazz: KClass<T>): T? {
        return clazz.cast(when(clazz) {
            //Configuration::class -> configuration
            Resources::class -> resources
            Storage::class -> storage
            else -> return null
        })
    }

    class ResourcesImpl(val context: Context): Resources {
        override fun getRawResource(name: String): ByteString? {
            return when(name) {
                "default/card_art.png" ->
                    bitmapData(
                        R.drawable.utopia_driving_license_card_art,
                        Bitmap.CompressFormat.PNG
                    )
                "default/logo.png" ->
                    bitmapData(
                        R.drawable.utopia_dmv_issuing_authority_logo,
                        Bitmap.CompressFormat.PNG
                    )
                "img_erika_portrait.jpf" ->
                    ByteString(getRawResourceAsBytes(R.raw.img_erika_portrait))
                "img_erika_signature.jpf" ->
                    ByteString(getRawResourceAsBytes(R.raw.img_erika_signature))
                else -> null
            }
        }

        override fun getStringResource(name: String): String? {
            return when(name) {
                "ds_private_key.pem" -> getRawResourceAsString(R.raw.ds_private_key)
                "ds_certificate.pem" -> getRawResourceAsString(R.raw.ds_certificate)
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