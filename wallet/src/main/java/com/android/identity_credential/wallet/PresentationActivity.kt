/*
 * Copyright (C) 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.identity_credential.wallet

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper
import com.android.identity.android.mdoc.transport.DataTransport
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.javaX509Certificates
import com.android.identity.document.Document
import com.android.identity.document.DocumentRequest
import com.android.identity.issuance.CredentialFormat
import com.android.identity.issuance.DocumentExtensions.documentConfiguration
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.mdoc.request.DeviceRequestParser
import com.android.identity.mdoc.response.DeviceResponseGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.trustmanagement.TrustPoint
import com.android.identity.util.Constants
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.presentation.UserCanceledPromptException
import com.android.identity_credential.wallet.presentation.showPresentmentFlow
import com.android.identity_credential.wallet.ui.theme.IdentityCredentialTheme
import com.android.identity_credential.wallet.util.inverse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

// using FragmentActivity in order to support androidx.biometric.BiometricPrompt
class PresentationActivity : FragmentActivity() {
    companion object {
        private const val TAG = "PresentationActivity"
        private var transport: DataTransport?
        private var handover: ByteArray?
        private var eDeviceKey: EcPrivateKey?
        private var deviceEngagement: ByteArray?
        private var state = MutableLiveData<State>()

        init {
            state.value = State.NOT_CONNECTED
            transport = null
            handover = null
            eDeviceKey = null
            deviceEngagement = null
        }

        fun startPresentation(
            context: Context, transport: DataTransport, handover: ByteArray,
            eDeviceKey: EcPrivateKey, deviceEngagement: ByteArray
        ) {
            this.transport = transport
            this.handover = handover
            this.eDeviceKey = eDeviceKey
            this.deviceEngagement = deviceEngagement
            Logger.i(TAG, "engagement info set")

            launchPresentationActivity(context)
            state.value = State.CONNECTED
        }

        private fun launchPresentationActivity(context: Context) {
            val launchAppIntent = Intent(context, PresentationActivity::class.java)
            launchAppIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            context.startActivity(launchAppIntent)
        }

        fun isPresentationActive(): Boolean {
            return state.value != State.NOT_CONNECTED
        }
    }

    enum class State {
        NOT_CONNECTED,
        CONNECTED,
        REQUEST_AVAILABLE,
        RESPONSE_SENT,
    }

    // reference WalletApplication for obtaining dependencies
    private val walletApp: WalletApplication by lazy {
        application as WalletApplication
    }

    // device request bytes
    private var deviceRequestByteArray: ByteArray? = null
    private var deviceRetrievalHelper: DeviceRetrievalHelper? = null

    // TODO: add SD_JWT_VC support
    // the supported formats for the Credentials of a Document
    val supportedCredentialFormats = listOf(CredentialFormat.MDOC_MSO)

    // Listener for obtaining request bytes from NFC/QR presentation engagements
    val deviceRetrievalHelperListener = object : DeviceRetrievalHelper.Listener {

        override fun onEReaderKeyReceived(eReaderKey: EcPublicKey) {
            Logger.i(TAG, "onEReaderKeyReceived")
        }

        override fun onDeviceRequest(deviceRequestBytes: ByteArray) {
            Logger.i(TAG, "onDeviceRequest")

            deviceRequestByteArray = deviceRequestBytes
            state.value = State.REQUEST_AVAILABLE
        }

        override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {
            Logger.i(TAG, "onDeviceDisconnected $transportSpecificTermination")
            disconnect()
        }

        override fun onError(error: Throwable) {
            Logger.e(TAG, "onError", error)
            disconnect()
        }
    }

    /**
     * Enum class defining the types of result statuses that a Presentment can yield to.
     *
     * @param messageResourceId the message to show for a status
     * @param imageResourceId the image to show above the message
     */
    enum class ResultStatus(val messageResourceId: Int, val imageResourceId: Int) {
        // All prompts were successful
        SUCCESS(
            R.string.presentation_result_success_message,
            R.drawable.presentment_result_status_success
        ),

        // Something prevented success
        ERROR(
            R.string.presentation_result_error_message,
            R.drawable.presentment_result_status_error
        ),

        // User tapped on the cancel button or outside of prompt (if permissible)
        CANCEL(0, 0),

        // For init of Flow
        UNSET(0, 0)
    }

    private val _resultStatus = MutableStateFlow(ResultStatus.UNSET)
    private val resultStatus: StateFlow<ResultStatus> = _resultStatus


    /**
     * Composable view that is shown at the end of the Presentation result to briefly update the user
     * on the result of the Presentation Prompt - success, neutral, or error.
     * @param resultStatus the [ResultStatus] object containing the result status data to show.
     */
    @Composable
    fun ResultStatus(resultStatus: ResultStatus) {
        // show the result message if there's one to show
        if (resultStatus == ResultStatus.UNSET) return

        // used for finishing the Activity after briefly showing the result
        val activity = LocalContext.current as? Activity
        val coroutineScope = rememberCoroutineScope() // Get the coroutine scope
        // whether we're showing the result status box
        val resultIsVisible = remember { mutableStateOf(true) }
        AnimatedVisibility(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
                // more padding here shrinks the status result box below
                .padding(25.dp),
            visible = resultIsVisible.value,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Surface(
                modifier = Modifier.shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(10.dp),
                    // drop shadow color is inverse of colorScheme.surface
                    spotColor = MaterialTheme.colorScheme.surface.inverse()
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        modifier = Modifier.padding(
                            top = if (resultStatus == ResultStatus.ERROR) {
                                25.dp
                            } else {
                                0.dp
                            }
                        ),
                        painter = painterResource(id = resultStatus.imageResourceId),
                        contentDescription = null,
                        contentScale = ContentScale.None,
                    )
                    Text(
                        text = stringResource(id = resultStatus.messageResourceId),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = if (resultStatus == ResultStatus.ERROR) {
                                    20.dp
                                } else {
                                    0.dp
                                }, bottom = 40.dp
                            ),
                        fontSize = 36.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            LaunchedEffect(resultStatus) {
                // wait 1 sec so user can read the status message before starting the
                // fade out animation
                delay(1000)
                resultIsVisible.value = false
                coroutineScope.launch {
                    // delay before finishing this Activity
                    delay(500)
                    activity?.finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Logger.i(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        // ensure no external apps can take a peek of Presentment Prompts while viewing recent apps
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            IdentityCredentialTheme {
                val resultStatus = resultStatus.collectAsState().value
                if (resultStatus == ResultStatus.CANCEL) {
                    finish()
                } else {
                    // show the Result Status message 100 dp from top of screen
                    Column {
                        Spacer(modifier = Modifier.height(100.dp))
                        ResultStatus(resultStatus = resultStatus)
                    }
                }
            }
        }

        state.observe(this as LifecycleOwner) { state ->
            when (state!!) {
                State.NOT_CONNECTED -> {
                    Logger.i(TAG, "State: Not Connected")
                }

                State.CONNECTED -> {
                    Logger.i(TAG, "State: Connected")
                    // on a new connected client, create a new DeviceRetrievalHelper
                    deviceRetrievalHelper = DeviceRetrievalHelper
                        .Builder(
                            applicationContext,
                            deviceRetrievalHelperListener,
                            ContextCompat.getMainExecutor(applicationContext),
                            eDeviceKey!!
                        )
                        .useForwardEngagement(transport!!, deviceEngagement!!, handover!!)
                        .build()
                }

                State.REQUEST_AVAILABLE -> {
                    Logger.i(TAG, "State: Request Available")
                    /**
                     * Device request bytes have been transmitted via device retrieval helper listener,
                     * Start the Presentment Flow where a Presentment is shown for every
                     * [DocumentRequest] that has a suitable [Document].
                     */
                    lifecycleScope.launch {
                        try {
                            val deviceRequest = DeviceRequestParser(
                                deviceRequestByteArray!!,
                                deviceRetrievalHelper!!.sessionTranscript
                            ).parse()

                            // generates the DeviceResponse from all the [Document] CBOR bytes of docRequests
                            val deviceResponseGenerator =
                                DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)

                            deviceRequest.docRequests.forEach { docRequest ->
                                // find an MdocCredential for the docRequest or skip to next docRequest
                                val mdocCredential =
                                    findMdocCredentialForRequest(docRequest) ?: return@forEach

                                // See if we recognize the reader/verifier
                                var trustPoint: TrustPoint? = null
                                if (docRequest.readerAuthenticated) {
                                    val result = walletApp.trustManager.verify(
                                        docRequest.readerCertificateChain!!.javaX509Certificates,
                                        customValidators = emptyList()  // not needed for reader auth
                                    )
                                    if (result.isTrusted && result.trustPoints.isNotEmpty()) {
                                        trustPoint = result.trustPoints.first()
                                    } else if (result.error != null) {
                                        Logger.w(
                                            TAG,
                                            "Error finding TrustPoint for reader auth",
                                            result.error!!
                                        )
                                    }
                                }

                                // show the Presentation Flow for and get the response bytes for
                                // the generated Document
                                val documentCborBytes = showPresentmentFlow(
                                    activity = this@PresentationActivity,
                                    walletApp = walletApp,
                                    documentRequest = MdocUtil.generateDocumentRequest(docRequest),
                                    mdocCredential = mdocCredential,
                                    trustPoint = trustPoint,
                                    encodedSessionTranscript = deviceRetrievalHelper!!.sessionTranscript
                                )
                                deviceResponseGenerator.addDocument(documentCborBytes)
                            }
                            // send the response with all the Document CBOR bytes that succeeded Presentation Flows
                            sendResponseToDevice(deviceResponseGenerator.generate())
                            _resultStatus.value = ResultStatus.SUCCESS
                        } catch (e: Exception) {
                            if (e !is UserCanceledPromptException) {
                                _resultStatus.value = ResultStatus.ERROR
                                Logger.e(TAG, "Error while running the Presentment Flow", e)
                            } else {
                                _resultStatus.value = ResultStatus.CANCEL
                            }
                        }
                    }
                }

                State.RESPONSE_SENT -> {
                    Logger.i(TAG, "State: Response Sent")
                    // cleanup
                    disconnect()
                }
            }
        }
    }


    override fun onDestroy() {
        Logger.i(TAG, "onDestroy")
        disconnect()
        super.onDestroy()
    }

    /**
     * Find a suitable Document for the given [docRequest] else throw [IllegalStateException].
     * @param docRequest parsed from DeviceRequest CBOR.
     * @return a matching [MdocCredential] from either on-screen Document or [DocumentStore]
     *      or null if there are no matching MdocCredential
     */
    fun findMdocCredentialForRequest(docRequest: DeviceRequestParser.DocRequest): MdocCredential? {

        fun isDocumentSuitableForDocRequest(
            document: Document,
            docType: String,
            supportedCredentialFormats: List<CredentialFormat> = listOf(CredentialFormat.MDOC_MSO)
        ): Boolean {
            /**
             * Nested local function that returns whether the given [Document] matches the
             * requested [docType] that also has at least one [Credential] with a [CredentialFormat]
             * listed in the [supportedCredentialFormats]. This function is nested here for
             * maintaining high cohesion and grouping this reusable function near the location of
             * where it's used (directly underneath this function).
             *
             * @param document the [Document] being checked for suitability for a [DocumentRequest]
             * @param docType the mDoc Document Type that the [document] should have for a match
             * @param supportedCredentialFormats a list of [CredentialFormat]s that are supported
             *      for authentication - this applies Credentials of a [Document].
             * @return a Boolean, [true] if the [document] matches the requested [docType] and has
             * at least one Credential where its [CredentialFormat] is supported/listed in
             * param [supportedCredentialFormats].
             */
            // if the docType matches, proceed to iterate over the document's credentials
            if (document.documentConfiguration.mdocConfiguration?.docType == docType) {
                val documentInfo = walletApp.documentModel.getDocumentInfo(document.name)

                // return true if there's at least 1 credential with a supported format
                return documentInfo?.credentialInfos?.any {
                    supportedCredentialFormats.contains(it.format)
                }
                // else encountered null getting DocumentInfo or CredentialInfos
                    ?: throw IllegalStateException("Error validating suitability for Document ${document.name} having DocumentInfo $documentInfo and CredentialInfos ${documentInfo?.credentialInfos}")
            }
            // the specified Document does not have the requested docType
            return false
        }

        var document: Document? = null

        // prefer the document that is on-screen if possible
        walletApp.settingsModel.focusedCardId.value?.let onscreenloop@{ documentIdFromPager ->
            val pagerDocument = walletApp.documentStore.lookupDocument(documentIdFromPager)
            if (pagerDocument != null) {
                val suitable = isDocumentSuitableForDocRequest(
                    document = pagerDocument,
                    docType = docRequest.docType,
                    supportedCredentialFormats = supportedCredentialFormats
                )

                if (suitable) {
                    document = pagerDocument
                    return@onscreenloop
                }
            }
        }

        // no matches from above, check suitability with all Documents added to DocumentStore
        walletApp.documentStore.listDocuments().forEach storeloop@{ documentIdFromStore ->
            val storeDocument = walletApp.documentStore.lookupDocument(documentIdFromStore)!!
            val suitable = isDocumentSuitableForDocRequest(
                document = storeDocument,
                docType = docRequest.docType,
                supportedCredentialFormats = supportedCredentialFormats
            )
            if (suitable) {
                document = storeDocument
                return@storeloop
            }
        }

        if (document == null) {
            return null
        }

        return document!!.findCredential(
            WalletApplication.CREDENTIAL_DOMAIN_MDOC,
            Clock.System.now()
        ) as MdocCredential
    }

    /**
     * Perform disconnect (via DeviceRetrievalHelper) and cleanup operations (nullifying vars) and
     * a call to finish(). A new Engagement will result in a new Activity instance.
     */
    private fun disconnect() {
        Logger.i(TAG, "disconnect")
        if (deviceRetrievalHelper == null) {
            Logger.i(TAG, "already closed")
            return
        }
        if (state.value == State.REQUEST_AVAILABLE) {
            val deviceResponseGenerator =
                DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_GENERAL_ERROR)
            sendResponseToDevice(deviceResponseGenerator.generate())
            _resultStatus.value = ResultStatus.ERROR
        }
        state.value = State.NOT_CONNECTED
        deviceRetrievalHelper?.disconnect()
        deviceRetrievalHelper = null
        transport = null
        handover = null
    }

    /**
     * Send response bytes to requesting party and updates state to [State.RESPONSE_SENT]
     * @param deviceResponseBytes response bytes that may or may not have been processed (such as
     * when sending an error)
     */
    private fun sendResponseToDevice(deviceResponseBytes: ByteArray) =
        deviceRetrievalHelper?.run {
            sendDeviceResponse(
                deviceResponseBytes,
                Constants.SESSION_DATA_STATUS_SESSION_TERMINATION
            )
            state.value = State.RESPONSE_SENT
        }
            ?: throw IllegalStateException("Unable to send response bytes, deviceRetrievalHelper is [null].")
}