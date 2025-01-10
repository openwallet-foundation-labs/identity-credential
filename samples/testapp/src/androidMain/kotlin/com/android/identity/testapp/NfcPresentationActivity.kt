package com.android.identity.testapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.android.identity.appsupport.ui.AppTheme
import com.android.identity.testapp.presentation.Presentation
import com.android.identity.util.AndroidContexts
import com.android.identity.util.Logger

class NfcPresentationActivity : FragmentActivity() {
    companion object {
        private const val TAG = "NfcPresentationActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidContexts.setCurrentActivity(this)

        setContent {
            AppTheme {
                Presentation(
                    documentStore = TestAppUtils.documentStore,
                    documentTypeRepository = TestAppUtils.documentTypeRepository,
                    readerTrustManager = TestAppUtils.readerTrustManager,
                    allowMultipleRequests = false,
                    showToast = { message -> Toast.makeText(this, message, Toast.LENGTH_LONG).show() },
                    onPresentationComplete = { finish() }
                )
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