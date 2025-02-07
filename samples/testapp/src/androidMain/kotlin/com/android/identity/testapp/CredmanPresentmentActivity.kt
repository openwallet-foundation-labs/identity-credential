package com.android.identity.testapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import org.multipaz.compose.ui.AppTheme
import com.android.identity.appsupport.ui.digitalcredentials.lookupForCredmanId
import com.android.identity.appsupport.ui.presentment.DigitalCredentialsPresentmentMechanism
import org.multipaz.compose.ui.presentment.Presentment
import com.android.identity.appsupport.ui.presentment.PresentmentModel
import com.android.identity.util.AndroidContexts
import com.android.identity.util.Logger
import com.google.android.gms.identitycredentials.GetCredentialResponse
import com.google.android.gms.identitycredentials.IntentHelper
import identitycredential.samples.testapp.generated.resources.Res
import identitycredential.samples.testapp.generated.resources.app_icon
import identitycredential.samples.testapp.generated.resources.app_name
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.json.JSONObject

class CredmanPresentmentActivity: FragmentActivity() {
    companion object {
        private const val TAG = "CredmanPresentmentActivity"
    }

    private val presentmentModel = PresentmentModel()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        CoroutineScope(Dispatchers.Main).launch {
            startPresentment(App.getInstance())
        }
    }

    private suspend fun startPresentment(app: App) {
        try {
            val request = IntentHelper.extractGetCredentialRequest(intent)
                ?: throw IllegalStateException("Error extracting GetCredentialRequest")
            val credentialId = intent.getLongExtra(IntentHelper.EXTRA_CREDENTIAL_ID, -1).toInt()

            val callingAppInfo = IntentHelper.extractCallingAppInfo(intent)!!
            val callingPackageName = callingAppInfo.packageName
            val callingOrigin = callingAppInfo.origin

            val json = JSONObject(request.credentialOptions.get(0).requestMatcher)
            val provider = json.getJSONArray("providers").getJSONObject(0)

            val document = app.documentStore.lookupForCredmanId(credentialId.toLong())
                ?: throw Error("No registered document for ID $credentialId")

            val mechanism = object : DigitalCredentialsPresentmentMechanism(
                appId = callingPackageName,
                webOrigin = callingOrigin,
                protocol = provider.getString("protocol"),
                request = provider.getString("request"),
                document = document
            ) {
                override fun sendResponse(response: String) {
                    val bundle = Bundle()
                    bundle.putByteArray("identityToken", response.toByteArray())
                    val credentialResponse = com.google.android.gms.identitycredentials.Credential("type", bundle)

                    val resultData = Intent()
                    IntentHelper.setGetCredentialResponse(
                        resultData,
                        GetCredentialResponse(credentialResponse)
                    )
                    setResult(RESULT_OK, resultData)
                }

                override fun close() {
                    Logger.i(TAG, "close")
                }
            }

            presentmentModel.reset()
            presentmentModel.setConnecting()
            presentmentModel.setMechanism(mechanism)

        } catch (e: Throwable) {
            Logger.i(TAG, "Error processing request", e)
            e.printStackTrace()
            finish()
            return
        }

        setContent {
            AppTheme {
                Scaffold { innerPadding ->
                    Presentment(
                        presentmentModel = presentmentModel,
                        documentTypeRepository = app.documentTypeRepository,
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
