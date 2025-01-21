package com.android.identity.testapp

import android.os.Bundle
import android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.android.identity.appsupport.ui.AppTheme
import com.android.identity.appsupport.ui.presentment.Presentment
import com.android.identity.util.AndroidContexts
import identitycredential.samples.testapp.generated.resources.Res
import identitycredential.samples.testapp.generated.resources.app_icon
import identitycredential.samples.testapp.generated.resources.app_name
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

class NfcPresentmentActivity : FragmentActivity() {
    companion object {
        private const val TAG = "NfcPresentmentActivity"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                Scaffold { innerPadding ->
                    Presentment(
                        presentmentModel = NdefService.presentmentModel,
                        documentTypeRepository = TestAppUtils.documentTypeRepository,
                        source = TestAppPresentmentSource(App.settingsModel),
                        onPresentmentComplete = { finish() },
                        appName = stringResource(Res.string.app_name),
                        appIconPainter = painterResource(Res.drawable.app_icon),
                        modifier = Modifier.consumeWindowInsets(innerPadding),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AndroidContexts.setCurrentActivity(this)
    }

    override fun onPause() {
        super.onPause()
        AndroidContexts.setCurrentActivity(null)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}