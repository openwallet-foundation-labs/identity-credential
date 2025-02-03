package com.android.identity.testapp

import android.content.ComponentName
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.android.identity.util.AndroidContexts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onResume() {
        super.onResume()
        AndroidContexts.setCurrentActivity(this)
        NfcAdapter.getDefaultAdapter(this)?.let {
            CardEmulation.getInstance(it)?.setPreferredService(this, ComponentName(this, NdefService::class::class.java))
        }
    }

    override fun onPause() {
        super.onPause()
        AndroidContexts.setCurrentActivity(null)
        NfcAdapter.getDefaultAdapter(this)?.let {
            CardEmulation.getInstance(it)?.unsetPreferredService(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        CoroutineScope(Dispatchers.Main).launch {
            val app = App.getInstance()
            app.startExportDocumentsToDigitalCredentials()
            setContent {
                app.Content()
            }
        }
    }
}
