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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper
import com.android.identity.android.mdoc.transport.DataTransport
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPublicKey
import com.android.identity.document.Document
import com.android.identity.document.DocumentRequest
import com.android.identity.mdoc.response.DeviceResponseGenerator
import com.android.identity.util.Constants
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.presentation.PresentmentFlow
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

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

    val presentmentFlow: PresentmentFlow by lazy {
        PresentmentFlow(walletApp, this)
    }

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
                    lifecycleScope.launch(
                        /**
                         * Define a custom exception handler to prevent exceptions from propagating
                         * upwards the coroutine and force close the app on uncaught exceptions.
                         */
                        CoroutineExceptionHandler { _, exception ->
                            Logger.e(
                                TAG,
                                "Exception during Presentment flow: $exception"
                            )
                        }
                    ) {
                        // get the response bytes containing 1 or more generated Documents to send
                        val responseBytes = presentmentFlow.showPresentmentFlow(
                            encodedDeviceRequest = deviceRequestByteArray!!,
                            encodedSessionTranscript = deviceRetrievalHelper!!.sessionTranscript
                        )

                        sendResponseToDevice(responseBytes)
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