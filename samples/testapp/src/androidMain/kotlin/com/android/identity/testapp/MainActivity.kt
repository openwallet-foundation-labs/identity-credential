package org.multipaz.testapp

import android.content.ComponentName
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import org.multipaz.context.initializeApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
            val result = cardEmulation.setPreferredService(
                this,
                ComponentName(this, NdefService::class::class.java)
            )
            Logger.i(TAG, "CardEmulation.setPreferredService() -> $result")
            val prefResult = cardEmulation.categoryAllowsForegroundPreference(CardEmulation.CATEGORY_PAYMENT)
            Logger.i(TAG, "CardEmulation.categoryAllowsForegroundPreference(CATEGORY_PAYMENT) -> $prefResult")
        }
    }

    override fun onPause() {
        super.onPause()
        NfcAdapter.getDefaultAdapter(this)?.let {
            val cardEmulation = CardEmulation.getInstance(it)
            val result = cardEmulation.unsetPreferredService(this)
            Logger.i(TAG, "CardEmulation.unsetPreferredService() -> $result")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeApplication(this.applicationContext)
        enableEdgeToEdge()

        CoroutineScope(Dispatchers.Main).launch {
            val app = App.getInstance(NdefService.promptModel)
            app.startExportDocumentsToDigitalCredentials()
            setContent {
                app.Content()
            }
        }
    }
}
