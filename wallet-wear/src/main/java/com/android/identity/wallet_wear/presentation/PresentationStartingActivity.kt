package com.android.identity.wallet_wear.presentation

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.credential.Credential
import com.android.identity.credential.CredentialStore
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.util.Logger
import com.android.identity.wallet_wear.presentation.theme.IdentityCredentialTheme
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import java.io.File


@OptIn(ExperimentalWearFoundationApi::class, ExperimentalHorologistApi::class)
class PresentationStartingActivity : ComponentActivity() {
    private val TAG = "PresentationStartingActivity"

    private lateinit var transferHelper: TransferHelper

    private var credentialBitmap: Bitmap? = null

    // If set, the credential to present (always set from QR engagement, possibly NFC engagement)
    private var credentialId: String? = null

    override fun onPause() {
        Logger.d(TAG, "onPause")
        super.onPause()
    }

    override fun onResume() {
        Logger.d(TAG, "onResume")
        super.onResume()
    }

    override fun onDestroy() {
        Logger.d(TAG, "onDestroy")
        super.onDestroy()
    }

    override fun onStop() {
        Logger.d(TAG, "onStop")
        super.onStop()
        if (transferHelper.getState().value != TransferHelper.State.REQUEST_AVAILABLE) {
            transferHelper.disconnect()
        }
    }

    override fun onRestart() {
        Logger.d(TAG, "onRestart")
        super.onRestart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Logger.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val extras = intent.extras
        if (extras != null) {
            credentialId = extras.getString("credentialId")
        }
        Logger.d(TAG, "credentialId: $credentialId")

        transferHelper = TransferHelper.getInstance(applicationContext)
        if (transferHelper.getState().value == TransferHelper.State.REQUEST_AVAILABLE) {
            switchToPresentationActivity()
        }

        val storageDir = File(applicationContext.noBackupFilesDir, "identity")
        val storageEngine = AndroidStorageEngine.Builder(applicationContext, storageDir).build()
        val secureAreaRepository = SecureAreaRepository();
        val androidKeystoreSecureArea = AndroidKeystoreSecureArea(applicationContext, storageEngine);
        secureAreaRepository.addImplementation(androidKeystoreSecureArea);
        val credentialStore = CredentialStore(storageEngine, secureAreaRepository)

        var credential: Credential? = null
        if (credentialId != null) {
            credential = credentialStore.lookupCredential(credentialId!!)
        }
        if (credential == null && credentialStore.listCredentials().size > 0) {
            credential = credentialStore.lookupCredential(credentialStore.listCredentials()[0])
        }
        if (credential != null) {
            val encodedArtwork = credential.applicationData.getData("artwork")
            val options = BitmapFactory.Options()
            options.inMutable = true
            credentialBitmap = BitmapFactory.decodeByteArray(encodedArtwork, 0, encodedArtwork.size, options)
        }

        setContent {
            IdentityCredentialTheme {
                transferHelper.getState().observe(this as LifecycleOwner) { state ->
                    when (state) {
                        TransferHelper.State.NOT_CONNECTED -> {
                            Logger.d(TAG, "State: Not Connected")
                            finish()
                        }
                        TransferHelper.State.CONNECTING -> Logger.d(TAG, "State: Connecting")
                        TransferHelper.State.CONNECTED -> Logger.d(TAG, "State: Connected")
                        TransferHelper.State.REQUEST_AVAILABLE -> {
                            Logger.d(TAG, "State: Request Available")
                            switchToPresentationActivity()
                        }
                        else -> {}
                    }
                }
                PresentationStartingPage()
            }
        }
    }

    private fun switchToPresentationActivity() {
        val launchAppIntent = Intent(applicationContext, PresentationActivity::class.java)
        launchAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NO_HISTORY or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        if (credentialId != null) {
            launchAppIntent.putExtra("credentialId", credentialId)
        }
        Logger.d(TAG, "Setting credentialId to $credentialId")
        applicationContext.startActivity(launchAppIntent)
    }

    @Composable
    private fun PresentationStartingPage() {
        Scaffold() {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                if (credentialBitmap != null) {
                    Image(
                        bitmap = credentialBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .width(96.dp)
                    )
                }

                Text(
                    text = "Connecting to Reader",
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                )

                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .size(16.dp)
                )
            }
        }
    }

}