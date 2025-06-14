package org.multipaz.testapp

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.android.identity.testapp.ui.AppTheme
import org.multipaz.context.initializeApplication
import org.multipaz.compose.presentment.Presentment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.multipaz.compose.prompt.PromptDialogs
import org.multipaz.models.presentment.PresentmentSource

class NfcPresentmentActivity : FragmentActivity() {
    companion object {
        private const val TAG = "NfcPresentmentActivity"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeApplication(this.applicationContext)
        enableEdgeToEdge()
        CoroutineScope(Dispatchers.Main).launch {
            val app = App.getInstance(NdefService.promptModel)
            app.init()
            startPresentment(app)
        }
    }

    private fun startPresentment(app: App) {
        setContent {
            AppTheme {
                Scaffold { innerPadding ->
                    PromptDialogs(app.promptModel)
                    Presentment(
                        presentmentModel = NdefService.presentmentModel,
                        documentTypeRepository = app.documentTypeRepository,
                        promptModel = app.promptModel,
                        source = app.getPresentmentSource(),
                        onPresentmentComplete = { finish() },
                        appName = platformAppName,
                        appIconPainter = painterResource(platformAppIcon),
                        modifier = Modifier.consumeWindowInsets(innerPadding),
                    )
                }
            }
        }
    }
}