package org.multipaz.testapp

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
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.multipaz.compose.presentment.Presentment
import org.multipaz.compose.prompt.PromptDialogs
import org.multipaz.context.initializeApplication
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.models.presentment.PresentmentModel
import org.multipaz.models.presentment.PresentmentSource
import org.multipaz.models.presentment.UriSchemePresentmentMechanism
import org.multipaz.prompt.PromptModel
import org.multipaz.testapp.ui.AppTheme
import org.multipaz.util.Logger

class TestAppUriSchemePresentmentActivity: UriSchemePresentmentActivity() {
    @Composable
    override fun ApplicationTheme(content: @Composable (() -> Unit)) {
        AppTheme(content)
    }

    override suspend fun getSettings(): Settings {
        val app = App.Companion.getInstance()
        app.init()
        return Settings(
            appName = platformAppName,
            appIcon = platformAppIcon,
            promptModel = app.promptModel,
            documentTypeRepository = app.documentTypeRepository,
            presentmentSource = app.getPresentmentSource(),
        )
    }
}

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
     */
    data class Settings(
        val appName: String,
        val appIcon: DrawableResource,
        val promptModel: PromptModel,
        val documentTypeRepository: DocumentTypeRepository,
        val presentmentSource: PresentmentSource,
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
            println("referrer: $referrer")
            @Suppress("DEPRECATION")
            val referrer2 = intent.extras?.get(Intent.EXTRA_REFERRER).toString()
            println("referrer2: $referrer2")
            if (url != null) {
                CoroutineScope(Dispatchers.Main).launch {
                    startPresentment(url, getSettings())
                }
            }
        }
    }

    private suspend fun startPresentment(url: String, settings: Settings) {
        presentmentModel.setPromptModel(settings.promptModel)
        try {
            val mechanism = object : UriSchemePresentmentMechanism(
                uri = url,
                httpClientEngineFactory = platformHttpClientEngineFactory()
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
