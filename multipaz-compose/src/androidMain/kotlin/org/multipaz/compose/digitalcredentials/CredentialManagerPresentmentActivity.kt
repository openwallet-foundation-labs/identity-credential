package org.multipaz.compose.digitalcredentials

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.core.graphics.drawable.toDrawable
import androidx.credentials.DigitalCredential
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetDigitalCredentialOption
import androidx.credentials.provider.PendingIntentHandler
import androidx.credentials.registry.provider.selectedEntryId
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.multipaz.compose.presentment.Presentment
import org.multipaz.compose.prompt.PromptDialogs
import org.multipaz.context.initializeApplication
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.models.digitalcredentials.lookupForCredmanId
import org.multipaz.models.presentment.DigitalCredentialsPresentmentMechanism
import org.multipaz.models.presentment.PresentmentModel
import org.multipaz.models.presentment.PresentmentSource
import org.multipaz.prompt.PromptModel
import org.multipaz.util.Logger

/**
 * Base class for activity used for Android Credential Manager presentments using the W3C Digital Credentials API.
 *
 * Applications should subclass this and include the appropriate stanzas in its manifest
 *
 * See the [MpzCmpWallet](https://github.com/davidz25/MpzCmpWallet) sample for an example.
 */
abstract class CredentialManagerPresentmentActivity: FragmentActivity() {
    companion object {
        private const val TAG = "CredentialManagerPresentmentActivity"
    }

    /**
     * Must be implemented by the application to specify the application theme to use.
     *
     * @return the theme to use.
     */
    @Composable
    abstract fun ApplicationTheme(content: @Composable () -> Unit)

    /**
     * Settings provided by the application for specifying what to present.
     *
     * @property appName the application name.
     * @property appIcon the application icon.
     * @property promptModel the [PromptModel] to use.
     * @property documentTypeRepository a [DocumentTypeRepository]
     * @property presentmentSource the [PresentmentSource] to use as the source of truth for what to present.
     * @property privilegedAllowList a string containing JSON with an allow-list of privileged browsers/apps
     *   that the applications trusts to provide the correct origin. For the format of the JSON see
     *   [CallingAppInfo.getOrigin()](https://developer.android.com/reference/androidx/credentials/provider/CallingAppInfo#getOrigin(kotlin.String))
     *   in the Android Credential Manager APIs. For an example, see the
     *   [public list of browsers trusted by Google Password Manager](https://gstatic.com/gpm-passkeys-privileged-apps/apps.json).
     */
    data class Settings(
        val appName: String,
        val appIcon: DrawableResource,
        val promptModel: PromptModel,
        val documentTypeRepository: DocumentTypeRepository,
        val presentmentSource: PresentmentSource,
        val privilegedAllowList: String
    )

    /**
     * Must be implemented by the application to specify what to present.
     *
     * @return a [Settings] object.
     */
    abstract suspend fun getSettings(): Settings

    private val presentmentModel = PresentmentModel()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeApplication(this.applicationContext)
        enableEdgeToEdge()

        window.setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setTranslucent(true)
        }

        CoroutineScope(Dispatchers.Main).launch {
            startPresentment(getSettings())
        }
    }

    @OptIn(ExperimentalDigitalCredentialApi::class)
    private suspend fun startPresentment(settings: Settings) {
        presentmentModel.setPromptModel(settings.promptModel)
        try {
            val credentialRequest = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)!!

            val callingAppInfo = credentialRequest.callingAppInfo
            val callingPackageName = callingAppInfo.packageName
            val callingOrigin = callingAppInfo.getOrigin(settings.privilegedAllowList)
            val option = credentialRequest.credentialOptions[0] as GetDigitalCredentialOption
            val json = Json.parseToJsonElement(option.requestJson).jsonObject
            Logger.iJson(TAG, "Request Json", json)
            Logger.i(TAG, "selectedEntryId: ${credentialRequest.selectedEntryId!!}")
            // See Credential::addCredentialToPicker() for how we construct selectedEntryId
            // to contain the protocol and documentId
            val selectedEntryIdParts = credentialRequest.selectedEntryId!!.split(" ")
            require(selectedEntryIdParts.size == 2)
            val protocol = selectedEntryIdParts[0]
            val document = settings.presentmentSource.documentStore.lookupForCredmanId(selectedEntryIdParts[1])
                ?: throw Error("No registered document for ID ${selectedEntryIdParts[1]}")
            // Find request matching the protocol for the selected entry...
            val requestForSelectedEntry = json["requests"]!!.jsonArray.find {
                (it as JsonObject)["protocol"]!!.jsonPrimitive.content == protocol
            }!!.jsonObject
            val mechanism = object : DigitalCredentialsPresentmentMechanism(
                appId = callingPackageName,
                webOrigin = callingOrigin,
                protocol = requestForSelectedEntry["protocol"]!!.jsonPrimitive.content,
                data = requestForSelectedEntry["data"]!!.jsonObject,
                document = document
            ) {
                override fun sendResponse(
                    protocol: String,
                    data: JsonObject
                ) {
                    val resultData = Intent()
                    val json = Json.encodeToString(
                        buildJsonObject {
                            put("protocol", protocol)
                            put("data", data)
                        }
                    )
                    Logger.i(TAG, "Size of JSON response for protocol $protocol: ${json.length} bytes")
                    val response = GetCredentialResponse(DigitalCredential(json))
                    PendingIntentHandler.setGetCredentialResponse(
                        resultData,
                        response
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
            ApplicationTheme {
                PromptDialogs(settings.promptModel)
                Presentment(
                    appName = settings.appName,
                    appIconPainter = painterResource(settings.appIcon),
                    presentmentModel = presentmentModel,
                    presentmentSource = settings.presentmentSource,
                    documentTypeRepository = settings.documentTypeRepository,
                    onPresentmentComplete = { finish() },
                    onlyShowConsentPrompt = true,
                    showCancelAsBack = true
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
