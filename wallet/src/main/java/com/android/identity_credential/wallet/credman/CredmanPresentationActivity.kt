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

package com.android.identity_credential.wallet.credman

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import androidx.fragment.app.FragmentActivity
import com.android.identity.android.mdoc.util.CredmanUtil
import com.android.identity.android.securearea.AndroidKeystoreKeyUnlockData
import com.android.identity.android.securearea.UserAuthenticationType
import com.android.identity.cbor.Cbor
import com.android.identity.credential.Credential
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.document.DocumentRequest
import com.android.identity.document.NameSpacedData
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPublicKeyDoubleCoordinate
import com.android.identity.issuance.DocumentExtensions.documentConfiguration
import com.android.identity.mdoc.mso.MobileSecurityObjectParser
import com.android.identity.mdoc.mso.StaticAuthDataParser
import com.android.identity.mdoc.response.DeviceResponseGenerator
import com.android.identity.mdoc.response.DocumentGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.securearea.KeyLockedException
import com.android.identity.securearea.KeyUnlockData
import com.android.identity.util.Constants
import com.android.identity.util.Logger
import com.android.identity.util.Timestamp
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.WalletApplication
import com.android.identity_credential.wallet.showBiometricPrompt
import org.json.JSONObject

import com.google.android.gms.identitycredentials.GetCredentialResponse
import com.google.android.gms.identitycredentials.IntentHelper
import java.util.StringTokenizer


// using FragmentActivity in order to support androidx.biometric.BiometricPrompt
class CredmanPresentationActivity : FragmentActivity() {
    companion object {
        private const val TAG = "CredmanPresentationActivity"
    }

    // reference WalletApplication for obtaining dependencies
    private val walletApp: WalletApplication by lazy {
        application as WalletApplication
    }

    private fun addDeviceNamespaces(
        documentGenerator: DocumentGenerator,
        credential: MdocCredential,
        unlockData: KeyUnlockData?
    ) {
        documentGenerator.setDeviceNamespacesSignature(
            NameSpacedData.Builder().build(),
            credential.secureArea,
            credential.alias,
            unlockData,
            Algorithm.ES256)
    }

    private fun createMDocDeviceResponse(
        credentialId: Int,
        dataElements: List<DocumentRequest.DataElement>,
        encodedSessionTranscript: ByteArray,
        onComplete: (deviceResponse: ByteArray, credential: Credential) -> Unit
    ) {
        val documentRequest = DocumentRequest(dataElements)

        val credentialStore = walletApp.documentStore
        val credentialName = credentialStore.listDocuments().get(credentialId.toInt())
        val document = credentialStore.lookupDocument(credentialName)
        val credConf = document!!.documentConfiguration

        val credential = document.findCredential(
            WalletApplication.CREDENTIAL_DOMAIN_MDOC,
            Timestamp.now()
        ) as MdocCredential?
        if (credential == null) {
            throw IllegalStateException("No credential")
        }
        val staticAuthData = StaticAuthDataParser(credential.issuerProvidedData).parse()
        val mergedIssuerNamespaces = MdocUtil.mergeIssuerNamesSpaces(
            documentRequest, credConf.mdocConfiguration!!.staticData, staticAuthData
        )

        val issuerAuthCoseSign1 = Cbor.decode(staticAuthData.issuerAuth).asCoseSign1
        val encodedMsoBytes = Cbor.decode(issuerAuthCoseSign1.payload!!)
        val encodedMso = Cbor.encode(encodedMsoBytes.asTaggedEncodedCbor)
        val mso = MobileSecurityObjectParser(encodedMso).parse()

        val deviceResponseGenerator = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)
        var documentGenerator = DocumentGenerator(
            mso.docType,
            staticAuthData.issuerAuth,
            encodedSessionTranscript
        )
        documentGenerator.setIssuerNamespaces(mergedIssuerNamespaces)

        try {
            addDeviceNamespaces(documentGenerator, credential, null)
            deviceResponseGenerator.addDocument(documentGenerator.generate())
            onComplete(deviceResponseGenerator.generate(), credential)
        } catch (e: KeyLockedException) {
            val keyUnlockData = AndroidKeystoreKeyUnlockData(credential.alias)
            showBiometricPrompt(
                this,
                title = applicationContext.resources.getString(R.string.presentation_biometric_prompt_title),
                subtitle = applicationContext.resources.getString(R.string.presentation_biometric_prompt_subtitle),
                keyUnlockData.getCryptoObjectForSigning(Algorithm.ES256),
                setOf(UserAuthenticationType.BIOMETRIC, UserAuthenticationType.LSKF),
                false,
                onSuccess = {
                    addDeviceNamespaces(documentGenerator, credential, keyUnlockData)
                    deviceResponseGenerator.addDocument(documentGenerator.generate())
                    onComplete(deviceResponseGenerator.generate(), credential)
                },
                onCanceled = {},
                onError = {
                    Logger.i(TAG, "Biometric auth failed", e)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {


            val cmrequest = IntentHelper.extractGetCredentialRequest(intent)
            val credentialId = intent.getLongExtra(IntentHelper.EXTRA_CREDENTIAL_ID, -1).toInt()

            // This call is currently broken, have to extract this info manually for now
            //val callingAppInfo = extractCallingAppInfo(intent)
            val callingPackageName =
                intent.getStringExtra(IntentHelper.EXTRA_CALLING_PACKAGE_NAME)!!
            val callingOrigin = intent.getStringExtra(IntentHelper.EXTRA_ORIGIN)

            Logger.i(TAG, "CredId: $credentialId ${cmrequest!!.credentialOptions.get(0).requestMatcher}")
            Logger.i(TAG, "Calling app $callingPackageName $callingOrigin")

            val dataElements = mutableListOf<DocumentRequest.DataElement>()

            val json = JSONObject(cmrequest!!.credentialOptions.get(0).requestMatcher)
            val provider = json.getJSONArray("providers").getJSONObject(0)

            val protocol = provider.getString("protocol")
            Logger.i(TAG, "Request protocol: $protocol")
            val request = provider.getString("request")
            Logger.i(TAG, "Request: $request")
            if (protocol == "preview") {
                // Extract params from the preview protocol request
                val previewRequest = JSONObject(request)
                val selector = previewRequest.getJSONObject("selector")
                val nonceBase64 = previewRequest.getString("nonce")
                val readerPublicKeyBase64 = previewRequest.getString("readerPublicKey")
                val docType = selector.getString("doctype")
                Logger.i(TAG, "DocType: $docType")
                Logger.i(TAG, "nonce: $nonceBase64")
                Logger.i(TAG, "readerPublicKey: $readerPublicKeyBase64")

                // Covert nonce and publicKey
                val nonce = Base64.decode(nonceBase64, Base64.NO_WRAP or Base64.URL_SAFE)
                val readerPublicKey = EcPublicKeyDoubleCoordinate.fromUncompressedPointEncoding(
                    EcCurve.P256,
                    Base64.decode(readerPublicKeyBase64, Base64.NO_WRAP or Base64.URL_SAFE)
                )

                // Match all the requested fields
                val fields = selector.getJSONArray("fields")
                for (n in 0 until fields.length()) {
                    val field = fields.getJSONObject(n)
                    val name = field.getString("name")
                    val namespace = field.getString("namespace")
                    val intentToRetain = field.getBoolean("intentToRetain")
                    Logger.i(TAG, "Field $namespace $name $intentToRetain")
                    dataElements.add(
                        DocumentRequest.DataElement(
                            namespace,
                            name,
                            intentToRetain
                        )
                    )
                }

                // Generate the Session Transcript
                val encodedSessionTranscript = if (callingOrigin == null) {
                    CredmanUtil.generateAndroidSessionTranscript(
                        nonce,
                        callingPackageName,
                        Crypto.digest(Algorithm.SHA256, readerPublicKey.asUncompressedPointEncoding)
                    )
                } else {
                    CredmanUtil.generateBrowserSessionTranscript(
                        nonce,
                        callingOrigin,
                        Crypto.digest(Algorithm.SHA256, readerPublicKey.asUncompressedPointEncoding)
                    )
                }
                // Create ISO DeviceResponse
                createMDocDeviceResponse(
                    credentialId,
                    dataElements,
                    encodedSessionTranscript,
                    onComplete = { deviceResponse, credential ->
                        credential.increaseUsageCount()
                        // The Preview protocol HPKE encrypts the response.
                        val (cipherText, encapsulatedPublicKey) = Crypto.hpkeEncrypt(
                            Algorithm.HPKE_BASE_P256_SHA256_AES128GCM,
                            readerPublicKey,
                            deviceResponse,
                            encodedSessionTranscript
                        )
                        val encodedCredentialDocument =
                            CredmanUtil.generateCredentialDocument(cipherText, encapsulatedPublicKey)

                        // Create the preview response
                        val responseJson = JSONObject()
                        responseJson.put(
                            "token",
                            Base64.encodeToString(
                                encodedCredentialDocument,
                                Base64.NO_WRAP or Base64.URL_SAFE
                            )
                        )
                        val response = responseJson.toString(2)

                        // Send result back to credman
                        val resultData = Intent()
                        IntentHelper.setGetCredentialResponse(
                            resultData,
                            createGetCredentialResponse(response)
                        )
                        setResult(RESULT_OK, resultData)
                        finish()
                    }
                )
            } else if (protocol == "openid4vp") {
                val openid4vpRequest = JSONObject(request)
                val clientID = openid4vpRequest.getString("client_id")
                Logger.i(TAG, "client_id $clientID")
                val nonceBase64 = openid4vpRequest.getString("nonce")
                Logger.i(TAG, "nonce: $nonceBase64")
                val nonce = Base64.decode(nonceBase64, Base64.NO_WRAP or Base64.URL_SAFE)

                val presentationDefinition = openid4vpRequest.getJSONObject("presentation_definition")
                val inputDescriptors = presentationDefinition.getJSONArray("input_descriptors")
                if (inputDescriptors.length() != 1) {
                    throw IllegalArgumentException("Only support a single input input_descriptor")
                }
                val inputDescriptor = inputDescriptors.getJSONObject(0)!!
                val docType = inputDescriptor.getString("id")
                Logger.i(TAG, "DocType: $docType")

                val constraints = inputDescriptor.getJSONObject("constraints")
                val fields = constraints.getJSONArray("fields")

                for (n in 0 until fields.length()) {
                    val field = fields.getJSONObject(n)
                    // Only support a single path entry for now
                    val path = field.getJSONArray("path").getString(0)!!
                    // JSONPath is horrible, hacky way to parse it for demonstration purposes
                    val st = StringTokenizer(path, "'", false).asSequence().toList()
                    val namespace = st[1] as String
                    val name = st[3] as String
                    Logger.i(TAG, "namespace $namespace name $name")
                    val intentToRetain = field.getBoolean("intent_to_retain")
                    dataElements.add(
                        DocumentRequest.DataElement(
                            namespace,
                            name,
                            intentToRetain
                        )
                    )
                }
                // Generate the Session Transcript
                val encodedSessionTranscript = if (callingOrigin == null) {
                    CredmanUtil.generateAndroidSessionTranscript(
                        nonce,
                        callingPackageName,
                        Crypto.digest(Algorithm.SHA256, clientID.toByteArray())
                    )
                } else {
                    CredmanUtil.generateBrowserSessionTranscript(
                        nonce,
                        callingOrigin,
                        Crypto.digest(Algorithm.SHA256, clientID.toByteArray())
                    )
                }
                // Create ISO DeviceResponse
                createMDocDeviceResponse(
                    credentialId,
                    dataElements,
                    encodedSessionTranscript,
                    onComplete = { deviceResponse, credential ->
                        credential.increaseUsageCount()
                        // Create the openid4vp response
                        val responseJson = JSONObject()
                        responseJson.put(
                            "vp_token",
                            Base64.encodeToString(
                                deviceResponse,
                                Base64.NO_WRAP or Base64.URL_SAFE
                            )
                        )
                        val response = responseJson.toString(2)

                        // Send result back to credman
                        val resultData = Intent()
                        IntentHelper.setGetCredentialResponse(
                            resultData,
                            createGetCredentialResponse(response)
                        )
                        setResult(RESULT_OK, resultData)
                        finish()
                    }
                )
            } else {
                // Unknown protocol
                throw IllegalArgumentException("Unknown protocol")
            }

        } catch (e: Exception) {
            Logger.i(TAG, "Exception $e")
            val resultData = Intent()
            IntentHelper.setGetCredentialException(resultData, e.toString(), e.message)
            setResult(RESULT_OK, resultData)
            finish()
        }
    }

    private fun createGetCredentialResponse(response: String): GetCredentialResponse {
        val bundle = Bundle()
        bundle.putByteArray("identityToken", response.toByteArray())
        val credentialResponse = com.google.android.gms.identitycredentials.Credential("type", bundle)
        return GetCredentialResponse(credentialResponse)
    }
}
