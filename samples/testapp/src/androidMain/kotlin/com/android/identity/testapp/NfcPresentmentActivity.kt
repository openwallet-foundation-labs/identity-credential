package org.multipaz.testapp

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import org.multipaz.context.initializeApplication
import org.multipaz.compose.AppTheme
import org.multipaz.compose.presentment.Presentment
import multipazproject.samples.testapp.generated.resources.Res
import multipazproject.samples.testapp.generated.resources.app_icon
import multipazproject.samples.testapp.generated.resources.app_name
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.multipaz.compose.prompt.PromptDialogs

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
            startPresentment(App.getInstance(NdefService.promptModel))
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
                        source = TestAppPresentmentSource(app),
                        onPresentmentComplete = { finish() },
                        appName = stringResource(Res.string.app_name),
                        appIconPainter = painterResource(Res.drawable.app_icon),
                        modifier = Modifier.consumeWindowInsets(innerPadding),
                    )
                }
            }
        }
    }
}