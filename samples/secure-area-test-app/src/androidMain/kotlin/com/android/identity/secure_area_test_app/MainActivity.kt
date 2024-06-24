package com.android.identity.secure_area_test_app

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.FragmentActivity
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class MainActivity : FragmentActivity() {

    companion object {
        lateinit var appContext: Context
            private set

        lateinit var instance: MainActivity
            private set
    }

    private val app = App()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        instance = this
        appContext = applicationContext

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