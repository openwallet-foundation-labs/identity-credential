package org.multipaz.testapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.credentials.DigitalCredential
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetDigitalCredentialOption
import androidx.credentials.provider.PendingIntentHandler
import androidx.credentials.registry.provider.selectedEntryId
import androidx.fragment.app.FragmentActivity
import org.multipaz.compose.AppTheme
import org.multipaz.models.digitalcredentials.lookupForCredmanId
import org.multipaz.models.presentment.DigitalCredentialsPresentmentMechanism
import org.multipaz.compose.presentment.Presentment
import org.multipaz.models.presentment.PresentmentModel
import org.multipaz.context.initializeApplication
import org.multipaz.util.Logger
import multipazproject.samples.testapp.generated.resources.Res
import multipazproject.samples.testapp.generated.resources.app_icon
import multipazproject.samples.testapp.generated.resources.app_name
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.json.JSONObject
import org.multipaz.compose.prompt.PromptDialogs

class CredmanPresentmentActivity: FragmentActivity() {
    companion object {
        private const val TAG = "CredmanPresentmentActivity"
    }

    private val presentmentModel = PresentmentModel()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeApplication(this.applicationContext)
        enableEdgeToEdge()

        CoroutineScope(Dispatchers.Main).launch {
            presentmentModel.setPromptModel(NdefService.promptModel)
            startPresentment(App.getInstance(NdefService.promptModel))
        }
    }

    @OptIn(ExperimentalDigitalCredentialApi::class)
    private suspend fun startPresentment(app: App) {
        try {
            val request = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
            val credentialId = request!!.selectedEntryId!!

            val stream = assets.open("privilegedUserAgents.json")
            val data = ByteArray(stream.available())
            stream.read(data)
            stream.close()
            val privilegedUserAgents = data.decodeToString()

            val callingAppInfo = request.callingAppInfo
            val callingPackageName = callingAppInfo.packageName
            val callingOrigin = callingAppInfo.getOrigin(privilegedUserAgents)
            val option = request.credentialOptions[0] as GetDigitalCredentialOption
            val json = JSONObject(option.requestJson)
            val provider = json.getJSONArray("providers").getJSONObject(0)

            val document = app.documentStore.lookupForCredmanId(credentialId)
                ?: throw Error("No registered document for ID $credentialId")

            val mechanism = object : DigitalCredentialsPresentmentMechanism(
                appId = callingPackageName,
                webOrigin = callingOrigin,
                protocol = provider.getString("protocol"),
                request = provider.getString("request"),
                document = document
            ) {
                override fun sendResponse(response: String) {
                    val resultData = Intent()
                    PendingIntentHandler.setGetCredentialResponse(
                        resultData,
                        GetCredentialResponse(
                            DigitalCredential(response)
                        )
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
                    PromptDialogs(app.promptModel)
                    Presentment(
                        presentmentModel = presentmentModel,
                        promptModel = app.promptModel,
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

    override fun onDestroy() {
        super.onDestroy()
    }
}
