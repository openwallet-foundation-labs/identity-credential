package org.multipaz.testapp

import android.content.ComponentName
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.coroutineScope
import org.multipaz.context.initializeApplication
import kotlinx.coroutines.launch
import org.multipaz.util.Logger

class MainActivity : FragmentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onResume() {
        super.onResume()
        NfcAdapter.getDefaultAdapter(this)?.let {
            val cardEmulation = CardEmulation.getInstance(it)
            if (!cardEmulation.setPreferredService(this, ComponentName(this, NdefService::class.java))) {
                Logger.w(TAG, "CardEmulation.setPreferredService() returned false")
            }
            if (!cardEmulation.categoryAllowsForegroundPreference(CardEmulation.CATEGORY_OTHER)) {
                Logger.w(TAG, "CardEmulation.categoryAllowsForegroundPreference(CATEGORY_OTHER) returned false")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        NfcAdapter.getDefaultAdapter(this)?.let {
            val cardEmulation = CardEmulation.getInstance(it)
            if (!cardEmulation.unsetPreferredService(this)) {
                Logger.w(TAG, "CardEmulation.unsetPreferredService() return false")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeApplication(this.applicationContext)
        enableEdgeToEdge()

        lifecycle.coroutineScope.launch {
            val app = App.getInstance(NdefService.promptModel)
            app.startExportDocumentsToDigitalCredentials()
            setContent {
                app.Content()
            }
        }
    }
}
