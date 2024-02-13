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
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper
import com.android.identity.android.mdoc.transport.DataTransport
import com.android.identity.credential.AuthenticationKey
import com.android.identity.credential.Credential
import com.android.identity.credential.NameSpacedData
import com.android.identity.internal.Util
import com.android.identity.issuance.CredentialExtensions.credentialConfiguration
import com.android.identity.issuance.CredentialExtensions.issuingAuthorityIdentifier
import com.android.identity.issuance.CredentialPresentationFormat
import com.android.identity.mdoc.mso.MobileSecurityObjectParser
import com.android.identity.mdoc.mso.StaticAuthDataParser
import com.android.identity.mdoc.request.DeviceRequestParser
import com.android.identity.mdoc.response.DeviceResponseGenerator
import com.android.identity.mdoc.response.DocumentGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.securearea.Algorithm
import com.android.identity.securearea.EcCurve
import com.android.identity.securearea.SecureArea
import com.android.identity.util.Constants
import com.android.identity.util.Logger
import com.android.identity.util.Timestamp
import com.android.identity_credential.wallet.ui.theme.IdentityCredentialTheme
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.security.KeyPair
import java.security.PublicKey
import java.util.OptionalLong

class PresentationActivity : ComponentActivity() {
    companion object {
        private const val TAG = "PresentationActivity"
        private var transport: DataTransport?
        private var handover: ByteArray?
        private var eDeviceKeyCurve: EcCurve?
        private var eDeviceKeyPair: KeyPair?
        private var deviceEngagement: ByteArray?
        private var state = MutableLiveData<State>()

        init {
            state.value = State.NOT_CONNECTED
            transport = null
            handover = null
            eDeviceKeyCurve = null
            eDeviceKeyPair = null
            deviceEngagement = null
        }

        fun startPresentation(context: Context, transport: DataTransport, handover: ByteArray,
                              eDeviceKeyCurve: EcCurve, eDeviceKeyPair: KeyPair, deviceEngagement:
                              ByteArray) {
            this.transport = transport
            this.handover = handover
            this.eDeviceKeyCurve = eDeviceKeyCurve
            this.eDeviceKeyPair = eDeviceKeyPair
            this.deviceEngagement = deviceEngagement
            Logger.i(TAG, "engagement info set")

            val launchAppIntent = Intent(context, PresentationActivity::class.java)
            launchAppIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            context.startActivity(launchAppIntent)
            state.value = State.CONNECTED
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

    private var deviceRequest: ByteArray? = null
    private var deviceRetrievalHelper: DeviceRetrievalHelper? = null
    private lateinit var sharedPreferences: SharedPreferences

    override fun onDestroy() {
        Logger.i(TAG, "onDestroy")
        disconnect()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Logger.i(TAG, "onCreate")
        super.onCreate(savedInstanceState)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        setContent {
            IdentityCredentialTheme {

                val stateDisplay = remember { mutableStateOf("Idle") }

                state.observe(this as LifecycleOwner) { state ->
                    when (state) {
                        State.NOT_CONNECTED -> {
                            stateDisplay.value = "Not Connected"
                            Logger.i(TAG, "State: Not Connected")
                        }
                        State.CONNECTED -> {
                            makeDeviceRetrievalHelper()
                            stateDisplay.value = "Connected"
                            Logger.i(TAG, "State: Connected")
                        }
                        State.REQUEST_AVAILABLE -> {
                            stateDisplay.value = "Request Available"
                            Logger.i(TAG, "State: Request Available")
                            processRequest()
                        }
                        State.RESPONSE_SENT -> {
                            stateDisplay.value = "Response Sent"
                            Logger.i(TAG, "State: Response Sent")
                        }
                        else -> {}
                    }
                }

                ScreenWithAppBar(title = "Presenting", navigationIcon = { }) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Sending mDL to reader.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "TODO: finalize UI",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Divider()
                        Text(
                            text = "State: ${stateDisplay.value}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Divider()
                        Button(onClick = { finish() }) {
                            Text("Close")
                        }
                    }
                }
            }
        }

    }

    private fun makeDeviceRetrievalHelper() {
        Logger.i(TAG, "making DeviceRetrievalHelper")
        deviceRetrievalHelper = DeviceRetrievalHelper.Builder(
            applicationContext,
            object : DeviceRetrievalHelper.Listener {
                override fun onEReaderKeyReceived(eReaderKey: PublicKey) {
                    Logger.i(TAG, "onEReaderKeyReceived")
                }

                override fun onDeviceRequest(deviceRequestBytes: ByteArray) {
                    Logger.i(TAG, "onDeviceRequest")
                    deviceRequest = deviceRequestBytes
                    state.value = State.REQUEST_AVAILABLE
                }

                override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {
                    Logger.i(TAG, "onDeviceDisconnected $transportSpecificTermination")
                    deviceRetrievalHelper?.disconnect()
                    deviceRetrievalHelper = null
                    state.value = State.NOT_CONNECTED
                }

                override fun onError(error: Throwable) {
                    Logger.i(TAG, "onError", error)
                    deviceRetrievalHelper?.disconnect()
                    deviceRetrievalHelper = null
                    state.value = State.NOT_CONNECTED
                }

            },
            ContextCompat.getMainExecutor(applicationContext),
            eDeviceKeyPair!!,
            eDeviceKeyCurve!!)
            .useForwardEngagement(transport!!, deviceEngagement!!, handover!!)
            .build()
    }

    private fun disconnect() {
        Logger.i(TAG, "disconnect")
        if (deviceRetrievalHelper == null) {
            Logger.i(TAG, "already closed")
            return
        }
        if (state.value == State.REQUEST_AVAILABLE) {
            val deviceResponseGenerator = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_GENERAL_ERROR)
            sendResponse(deviceResponseGenerator.generate())
        }
        deviceRetrievalHelper?.disconnect()
        deviceRetrievalHelper = null
        transport = null
        handover = null
        state.value = State.NOT_CONNECTED
    }

    private fun error(msg: String) {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        this.onDestroy()
    }

    private fun getDeviceRequest(): ByteArray {
        check(state.value == State.REQUEST_AVAILABLE) { "Not in REQUEST_AVAILABLE state"}
        check(deviceRequest != null) { "No request available "}
        return deviceRequest as ByteArray
    }

    private fun sendResponse(deviceResponseBytes: ByteArray) {
        check(state.value == State.REQUEST_AVAILABLE) { "Not in REQUEST_AVAILABLE state"}
        deviceRetrievalHelper!!.sendDeviceResponse(deviceResponseBytes, OptionalLong.of(Constants.SESSION_DATA_STATUS_SESSION_TERMINATION))
        state.value = State.RESPONSE_SENT
    }

    private fun processRequest() {
        // TODO support more formats
        val credentialPresentationFormat: CredentialPresentationFormat = CredentialPresentationFormat.MDOC_MSO

        // TODO when selecting a matching credential of the MDOC_MSO format, also use docRequest.docType
        //     to select a credential of the right doctype
        val credentialId: String? = firstMatchingCredentialID(credentialPresentationFormat)
        if (credentialId == null) {
            error("No matching credentials in wallet")
            return
        }

        val request = DeviceRequestParser()
            .setDeviceRequest(getDeviceRequest())
            .setSessionTranscript(deviceRetrievalHelper!!.sessionTranscript)
            .parse()
        val docRequest = request.documentRequests[0]
        val requestedDocType: String = docRequest.docType
        val encodedDeviceResponse: ByteArray

        if (requestedDocType == SelfSignedMdlIssuingAuthority.MDL_DOCTYPE) {
            val application: WalletApplication = application as WalletApplication
            val credential = application.credentialStore.lookupCredential(credentialId)!!
            val credentialConfiguration = credential.credentialConfiguration
            val now = Timestamp.now()

            val authKey: AuthenticationKey
            try {
                authKey = credential.findAuthenticationKey(WalletApplication.AUTH_KEY_DOMAIN, now)!!
            } catch (e: Exception) {
                error("No valid auth keys, please request more")
                return
            }

            val staticAuthData = StaticAuthDataParser(authKey.issuerProvidedData).parse()
            val encodedMsoBytes = Util.cborDecode(Util.coseSign1GetData(Util.cborDecode(staticAuthData.issuerAuth)))
            val encodedMso = Util.cborEncode(Util.cborExtractTaggedAndEncodedCbor(encodedMsoBytes))
            val mso = MobileSecurityObjectParser().setMobileSecurityObject(encodedMso).parse()

            val credentialRequest = MdocUtil.generateCredentialRequest(docRequest!!)
            val mergedIssuerNamespaces = MdocUtil.mergeIssuerNamesSpaces(
                credentialRequest,
                credentialConfiguration.staticData,
                staticAuthData
            )

            val deviceResponseGenerator = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)
            deviceResponseGenerator.addDocument(
                DocumentGenerator(mso.docType,
                    staticAuthData.issuerAuth, deviceRetrievalHelper!!.sessionTranscript)
                    .setIssuerNamespaces(mergedIssuerNamespaces)
                    .setDeviceNamespacesSignature(
                        NameSpacedData.Builder().build(),
                        authKey.secureArea,
                        authKey.alias,
                        null,
                        Algorithm.ES256
                    )
                    .generate()
            )
            encodedDeviceResponse = deviceResponseGenerator.generate()
        } else {
            error("$requestedDocType not available")
            return
        }

        sendResponse(encodedDeviceResponse)
    }

    private fun firstMatchingCredentialID(
        credentialPresentationFormat: CredentialPresentationFormat): String? {
        // prefer the credential which is on-screen if possible
        val credentialIdFromPager: String? = sharedPreferences.getString(WalletApplication.PREFERENCE_CURRENT_CREDENTIAL_ID, null)
        if (credentialIdFromPager != null) {
            if (isCredentialValid(credentialIdFromPager, credentialPresentationFormat)) {
                return credentialIdFromPager
            }
        }

        val application: WalletApplication = application as WalletApplication
        val credentialIds = application.credentialStore.listCredentials()
        for (credentialId in credentialIds) {
            if (isCredentialValid(credentialId, credentialPresentationFormat)) {
                return credentialIdFromPager
            }
        }

        return null
    }

    private fun isCredentialValid(credentialId: String,
                                  credentialPresentationFormat: CredentialPresentationFormat): Boolean {
        val application: WalletApplication = application as WalletApplication
        val credential = application.credentialStore.lookupCredential(credentialId)!!
        val issuingAuthorityIdentifier = credential.issuingAuthorityIdentifier
        val issuer = application.issuingAuthorityRepository.lookupIssuingAuthority(issuingAuthorityIdentifier)
            ?: throw IllegalArgumentException("No issuer with id $issuingAuthorityIdentifier")
        val credentialFormats = issuer.configuration.credentialFormats
        return credentialFormats.contains(credentialPresentationFormat)
    }
}
