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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper
import com.android.identity.android.mdoc.transport.DataTransport
import com.android.identity.android.securearea.AndroidKeystoreKeyInfo
import com.android.identity.android.securearea.AndroidKeystoreKeyUnlockData
import com.android.identity.android.securearea.UserAuthenticationType
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.javaX509Certificates
import com.android.identity.issuance.DocumentExtensions.documentConfiguration
import com.android.identity.mdoc.request.DeviceRequestParser
import com.android.identity.mdoc.response.DeviceResponseGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.securearea.KeyUnlockData
import com.android.identity.trustmanagement.TrustPoint
import com.android.identity.util.Constants
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.presentation.PresentationFlowActivity
import com.android.identity_credential.wallet.presentation.PresentationRequestData
import com.android.identity_credential.wallet.ui.ScreenWithAppBar
import com.android.identity_credential.wallet.ui.prompt.biometric.showBiometricPrompt
import com.android.identity_credential.wallet.ui.prompt.consent.ConsentPromptEntryField
import com.android.identity_credential.wallet.ui.prompt.consent.ConsentPromptEntryFieldData
import com.android.identity_credential.wallet.ui.theme.IdentityCredentialTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// using FragmentActivity in order to support androidx.biometric.BiometricPrompt
class PresentationActivity : PresentationFlowActivity() {
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

    override fun onDestroy() {
        Logger.i(TAG, "onDestroy")
        disconnect()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Logger.i(TAG, "onCreate")
        super.onCreate(savedInstanceState)

        setContent {
            IdentityCredentialTheme {

                val parsedPresentationRequestData =
                    remember { mutableStateOf<PresentationRequestData?>(null) }
                /**
                 * Handle Presentation Activity State changes so that on
                 * [State.CONNECTED] - instantiates a new [DeviceRetrievalHelper] and its listener
                 * [DeviceRetrievalHelper.Listener],
                 *
                 * [State.REQUEST_AVAILABLE] - parses the request [ByteArray] and produces a
                 * [PresentationRequestData] and Presentation Flow is shown.
                 *
                 * [State.RESPONSE_SENT] - cleanup
                 *
                 * Produces a [PresentationRequestData] that notifies UI composition to show the flow.
                 * Exceptions are
                 */
                handleActivityStateChanges { presentationRequestData ->
                    // notify that we are ready to show the Presentation Flow
                    parsedPresentationRequestData.value = presentationRequestData
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    // if Presentation data is parsed and ready to use, show the Presentation Flow
                    parsedPresentationRequestData.value?.let { presentationRequestData ->
                        // launched effect [key1] does not change for presentations,
                        // either the flow succeeds or Activity is finish()'ed
                        LaunchedEffect(key1 = "MDL_Presentation") {

                            try {
                                val responseBytes = showPresentationFlow(presentationRequestData)
                                sendResponseToDevice(responseBytes)
                            } catch (exception: Exception) {
                                Logger.e(TAG, "Error finishing the Presentation flow: $exception")
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Handle the different Presentation Activity State changes, such as instantiating a new
     * [DeviceRetrievalHelper] when state is [State.CONNECTED], parse the request bytes when state
     * is [State.REQUEST_AVAILABLE] that initiates showing the Presentation flow upon successfully
     * generating [PresentationRequestData], or perform cleanup operations.
     *
     * @param onPresentationRequestData callback that RETURNS the generated [PresentationRequestData]
     *                                  parsed from the request bytes.
     */
    private fun handleActivityStateChanges(onPresentationRequestData: (PresentationRequestData) -> Unit) {
        state.observe(this as LifecycleOwner) { state ->
            when (state) {
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
                     * parse the bytes and generate a [PresentationRequestData] that is used throughout
                     * all the prompts.
                     *
                     * Return this generated data through the callback [onPresentationRequestData],
                     * or an Exception is thrown.
                     */
                    try {
                        val presentationRequestData = parseDeviceRequestBytes(getDeviceRequest())
                        onPresentationRequestData(presentationRequestData)
                    } catch (exception: Exception) {
                        Logger.e(TAG, "Unable to parse device Request Bytes: $exception")
                    }
                }

                State.RESPONSE_SENT -> {
                    Logger.i(TAG, "State: Response Sent")
                    // cleanup
                    disconnect()
                }

                else -> {}
            }
        }
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
        }
        deviceRetrievalHelper?.disconnect()
        deviceRetrievalHelper = null
        transport = null
        handover = null
        finish()
    }

    /**
     * Parses the device request bytes returns a [PresentationRequestData] object that is passed to
     * all prompts that need the data.
     *
     * If it's unable to find a matching document credential it returns an [IllegalStateException]
     * amongst any other Exceptions thrown (such as a null pointer when calling lookupDocument())
     *
     * @param deviceRequest the (request) bytes provided from [DeviceRetrievalHelper.Listener].
     * @return the generated [PresentationRequestData] containing the request document and data.
     */
    private fun parseDeviceRequestBytes(deviceRequest: ByteArray): PresentationRequestData {
        val request =
            DeviceRequestParser(deviceRequest, deviceRetrievalHelper!!.sessionTranscript).parse()

        lateinit var docRequestToUse: DeviceRequestParser.DocRequest
        var credentialId: String? = null
        request.docRequests.forEach { docRequest ->
            // TODO when selecting a matching credential of the MDOC_MSO format, also use docRequest.docType
            //     to select a credential of the right doctype
            // TODO confirm if TODO ABOVE is still valid --- (see canDocumentSatisfyRequest())
            findFirstDocumentSatisfyingRequest(
                walletApp.settingsModel, docRequest
            )?.let { credId ->
                // we found a valid credential id
                credentialId = credId
                docRequestToUse = docRequest
            }
        }
        check(credentialId != null) { "No matching credentials could be found" }

        val validCredentialId = credentialId!!
        val credentialDocument = walletApp.documentStore.lookupDocument(validCredentialId)!!
        var trustPoint: TrustPoint? = null
        if (docRequestToUse.readerAuthenticated) {
            val result = walletApp.trustManager.verify(
                docRequestToUse.readerCertificateChain!!.javaX509Certificates,
                customValidators = emptyList()  // not needed for reader auth
            )
            if (result.isTrusted && result.trustPoints.isNotEmpty()) {
                trustPoint = result.trustPoints.first()
            } else if (result.error != null) {
                Logger.w(TAG, "Error finding TrustPoint for reader auth", result.error!!)
            }
        }

        val credentialDocumentRequest = MdocUtil.generateDocumentRequest(docRequestToUse)
        return PresentationRequestData(
            document = credentialDocument,
            documentRequest = credentialDocumentRequest,
            docType = docRequestToUse.docType,
            trustPoint = trustPoint,
            sessionTranscript = deviceRetrievalHelper!!.sessionTranscript
        )
    }


    /**
     * Return a credential identifier which can satisfy the request.
     *
     * If multiple credentials can satisfy the request, preference is given to the currently
     * focused credential in the main pager.
     *
     * @param settingsModel app-wide settings
     * @param docRequest the parsed [DeviceRequestParser.DocRequest]
     * @return credential identifier if found, otherwise null.
     */
    private fun findFirstDocumentSatisfyingRequest(
        settingsModel: SettingsModel,
        docRequest: DeviceRequestParser.DocRequest,
    ): String? {
        // prefer the credential which is on-screen if possible
        val credentialIdFromPager: String? = settingsModel.focusedCardId.value
        if (credentialIdFromPager != null
            && canDocumentSatisfyRequest(credentialIdFromPager, docRequest)
        ) {
            return credentialIdFromPager
        }

        return walletApp.documentStore.listDocuments().firstOrNull { credentialId ->
            canDocumentSatisfyRequest(credentialId, docRequest)
        }
    }

    /**
     * Return whether the passed credential id can satisfy the request
     *
     * @param credentialId the id of the credential to check for satisfaction.
     * @param docRequest the DocRequest with its DocType.
     * @return whether the specified credential id can satisfy the request.
     */
    private fun canDocumentSatisfyRequest(
        credentialId: String,
        docRequest: DeviceRequestParser.DocRequest
    ): Boolean {
        val credential = walletApp.documentStore.lookupDocument(credentialId)!!
        return credential.documentConfiguration.mdocConfiguration?.docType == docRequest.docType
    }

    /**
     * Send response bytes to requesting party and updates state to [State.RESPONSE_SENT]
     * @param deviceResponseBytes response bytes that may or may not have been processed (such as
     * when sending an error)
     */
    private fun sendResponseToDevice(deviceResponseBytes: ByteArray) {
        deviceRetrievalHelper?.sendDeviceResponse(
            deviceResponseBytes,
            Constants.SESSION_DATA_STATUS_SESSION_TERMINATION
        )
        state.value = State.RESPONSE_SENT
    }


    private fun getDeviceRequest(): ByteArray {
        check(state.value == State.REQUEST_AVAILABLE) { "Not in REQUEST_AVAILABLE state" }
        check(deviceRequestByteArray != null) { "No request available " }
        return deviceRequestByteArray as ByteArray
    }
}
