package com.android.identity.testapp

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.FragmentActivity
import com.android.identity.util.AndroidInitializer
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class MainActivity : FragmentActivity() {

    private val app = App()

    private var fragmentActivity: FragmentActivity? = null

    override fun onResume() {
        super.onResume()
        fragmentActivity = this
    }

    override fun onPause() {
        super.onPause()
        fragmentActivity = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fragmentActivity = this
        AndroidInitializer.initialize(applicationContext, { fragmentActivity })

        // This is needed to prefer BouncyCastle bundled with the app instead of the Conscrypt
        // based implementation included in the OS itself.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())

        setContent {
            app.Content()
        }
    }
}

private val previewApp: App by lazy { App() }

@Preview
@Composable
fun AppAndroidPreview() {
    previewApp.Content()
}