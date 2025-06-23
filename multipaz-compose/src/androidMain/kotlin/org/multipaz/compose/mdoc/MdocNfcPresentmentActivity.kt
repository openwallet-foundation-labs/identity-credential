package org.multipaz.compose.mdoc

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.multipaz.compose.mdoc.MdocNdefService.Companion.presentmentModel
import org.multipaz.compose.presentment.Presentment
import org.multipaz.compose.prompt.PromptDialogs
import org.multipaz.context.initializeApplication
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.models.presentment.PresentmentSource
import org.multipaz.prompt.PromptModel

/**
 * Base class for activity used for ISO/IEC 18013-5:2021 presentment when using NFC engagement.
 *
 * Applications should subclass this and include the appropriate stanzas in its manifest.
 *
 * See the [MpzCmpWallet](https://github.com/davidz25/MpzCmpWallet) sample for an example.
 */
abstract class MdocNfcPresentmentActivity : FragmentActivity() {

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
        val presentmentSource: PresentmentSource
    )

    /**
     * Must be implemented by the application to specify what to present.
     *
     * @return a [Settings] object.
     */
    abstract suspend fun getSettings(): Settings

    /**
     * Must be implemented by the application to specify the application theme to use.
     *
     * @return the theme to use.
     */
    @Composable
    abstract fun ApplicationTheme(content: @Composable () -> Unit)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeApplication(this.applicationContext)
        enableEdgeToEdge()
        CoroutineScope(Dispatchers.Main).launch {
            startPresentment(getSettings())
        }
    }

    private fun startPresentment(settings: Settings) {
        presentmentModel.setPromptModel(settings.promptModel)
        setContent {
            ApplicationTheme {
                Scaffold { innerPadding ->
                    PromptDialogs(settings.promptModel)
                    Presentment(
                        modifier = Modifier.consumeWindowInsets(innerPadding),
                        appName = settings.appName,
                        appIconPainter = painterResource(settings.appIcon),
                        presentmentModel = presentmentModel,
                        presentmentSource = settings.presentmentSource,
                        documentTypeRepository = settings.documentTypeRepository,
                        onPresentmentComplete = { finish() },
                    )
                }
            }
        }
    }
}