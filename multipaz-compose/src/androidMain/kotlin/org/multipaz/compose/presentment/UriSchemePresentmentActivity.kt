package org.multipaz.compose.presentment

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.FragmentActivity
import io.ktor.client.engine.HttpClientEngineFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.multipaz.compose.prompt.PromptDialogs
import org.multipaz.context.initializeApplication
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.models.presentment.PresentmentModel
import org.multipaz.models.presentment.PresentmentSource
import org.multipaz.models.presentment.UriSchemePresentmentMechanism
import org.multipaz.prompt.PromptModel
import org.multipaz.util.Logger
import java.net.URL

/**
 * Base class for activity used for credential presentments using URI schemes.
 *
 * Applications should subclass this and include the appropriate stanzas in its manifest
 *
 * See the [MpzCmpWallet](https://github.com/davidz25/MpzCmpWallet) sample for an example.
 */
abstract class UriSchemePresentmentActivity: FragmentActivity() {
    companion object {
        private const val TAG = "UriSchemePresentmentActivity"
    }

    /**
     * Must be implemented by the application to specify the application theme to use.
     *
     * @return the theme to use.
     */
    @Composable
    abstract fun ApplicationTheme(content: @Composable () -> Unit)

    /**
     * Settings provided by the application for specifying what to present.
     *
     * @property appName the application name.
     * @property appIcon the application icon.
     * @property promptModel the [PromptModel] to use.
     * @property documentTypeRepository a [DocumentTypeRepository]
     * @property presentmentSource the [PresentmentSource] to use as the source of truth for what to present.
     * @property httpClientEngineFactory the factory for creating the Ktor HTTP client engine (e.g. CIO).
     */
    data class Settings(
        val appName: String,
        val appIcon: DrawableResource,
        val promptModel: PromptModel,
        val documentTypeRepository: DocumentTypeRepository,
        val presentmentSource: PresentmentSource,
        val httpClientEngineFactory: HttpClientEngineFactory<*>
    )

    /**
     * Must be implemented by the application to specify what to present.
     *
     * @return a [Settings] object.
     */
    abstract suspend fun getSettings(): Settings

    private val presentmentModel = PresentmentModel()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeApplication(this.applicationContext)
        enableEdgeToEdge()

        window.setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setTranslucent(true)
        }

        if (intent.action == Intent.ACTION_VIEW) {
            val url = intent.dataString
            // This may or may not be set. For example in Chrome it only works
            // if the website is using Referrer-Policy: unsafe-url
            //
            // Reference: https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Referrer-Policy
            //
            @Suppress("DEPRECATION")
            var referrerUrl: String? = intent.extras?.get(Intent.EXTRA_REFERRER).toString()
            if (referrerUrl == "null") {
                referrerUrl = null
            }
            if (url != null) {
                CoroutineScope(Dispatchers.Main).launch {
                    startPresentment(url, referrerUrl, getSettings())
                }
            }
        }
    }

    private suspend fun startPresentment(
        url: String,
        referrerUrl: String?,
        settings: Settings
    ) {
        val origin = referrerUrl?.let {
            val url = URL(it)
            "${url.protocol}://${url.host}${if (url.port != -1) ":${url.port}" else ""}"
        }
        presentmentModel.setPromptModel(settings.promptModel)
        try {
            val mechanism = object : UriSchemePresentmentMechanism(
                uri = url,
                origin = origin,
                httpClientEngineFactory = settings.httpClientEngineFactory
            ) {
                override fun openUriInBrowser(uri: String) {
                    // TODO: maybe defer this until the presentment is over...
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(uri)
                        )
                    )
                }

                override fun close() {
                    Logger.i(TAG, "closing..")
                    finish()
                }
            }
            presentmentModel.reset()
            presentmentModel.setConnecting()
            presentmentModel.setMechanism(mechanism)
        } catch (e: Throwable) {
            Logger.i(TAG, "Error processing request", e)
            e.printStackTrace()
            finish()
            return
        }

        setContent {
            ApplicationTheme {
                PromptDialogs(settings.promptModel)
                Presentment(
                    appName = settings.appName,
                    appIconPainter = painterResource(settings.appIcon),
                    presentmentModel = presentmentModel,
                    presentmentSource = settings.presentmentSource,
                    documentTypeRepository = settings.documentTypeRepository,
                    onPresentmentComplete = { finish() },
                    onlyShowConsentPrompt = true,
                    showCancelAsBack = true
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
