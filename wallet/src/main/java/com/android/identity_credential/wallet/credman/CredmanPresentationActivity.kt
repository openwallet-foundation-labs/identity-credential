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
import com.android.identity.android.mdoc.util.CredmanUtil.Companion.generatePublicKeyHash
import com.android.identity.android.securearea.AndroidKeystoreKeyUnlockData
import com.android.identity.android.securearea.UserAuthenticationType
import com.android.identity.cbor.Cbor
import com.android.identity.document.Credential
import com.android.identity.document.DocumentRequest
import com.android.identity.document.NameSpacedData
import com.android.identity.crypto.Algorithm
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
import java.security.PublicKey


// using FragmentActivity in order to support androidx.biometric.BiometricPrompt
class CredmanPresentationActivity : FragmentActivity() {
    companion object {
        private const val TAG = "CredmanPresentationActivity"
    }

    // reference WalletApplication for obtaining dependencies
    private val walletApp: WalletApplication by lazy {
        application as WalletApplication
    }


    override fun onDestroy() {
        Logger.i(TAG, "onDestroy")
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Logger.i(TAG, "onCreate")
        super.onCreate(savedInstanceState)

        val request = IntentHelper.extractGetCredentialRequest(intent)
        val credentialId = intent.getLongExtra(IntentHelper.EXTRA_CREDENTIAL_ID, -1)
        val callingAppInfo = IntentHelper.extractCallingAppInfo(intent)
        //log("CredId: $credentialId ${request!!.credentialOptions.get(0).requestMatcher}")

        val dataElements = mutableListOf<DocumentRequest.DataElement>()

        val json = JSONObject(request!!.credentialOptions.get(0).requestMatcher)
        val provider = json.getJSONArray("providers").getJSONObject(0)
        val responseFormat = provider.get("responseFormat")
        val params = provider.getJSONObject("params")
        val nonceBase64 = params.getString("nonce")
        val nonce = Base64.decode(nonceBase64, Base64.NO_WRAP or Base64.URL_SAFE)
        val requesterIdentityBase64 = params.getString("requesterIdentity")
        val requesterIdentity = CredmanUtil.publicKeyFromUncompressed(
            Base64.decode(requesterIdentityBase64, Base64.NO_WRAP or Base64.URL_SAFE))
        Logger.i(TAG, "responseFormat: $responseFormat nonce: $nonce requester: $requesterIdentity")
        val fields = provider.getJSONObject("selector").getJSONArray("fields")
        for (n in 0 until fields.length()) {
            // TODO: request format needs revision, this won't work if data elements have dots in them.
            val field = fields.getJSONObject(n)
            val name = field.getString("name")
            var finalDot = name.lastIndexOf('.')
            if (finalDot > 0) {
                val nameSpaceName = name.substring(0, finalDot)
                val dataElementName = name.substring(finalDot + 1)
                dataElements.add(
                    DocumentRequest.DataElement(
                        nameSpaceName,
                        dataElementName,
                        false
                    )
                )
            }
        }

        val documentRequest = DocumentRequest(dataElements)

        val credentialStore = walletApp.documentStore
        val credentialName = credentialStore.listDocuments().get(credentialId.toInt())
        val document = credentialStore.lookupDocument(credentialName)
        val credConf = document!!.documentConfiguration

        val encodedSessionTranscript = CredmanUtil.generateAndroidSessionTranscript(
            nonce,
            "com.android.mdl.appreader",
            generatePublicKeyHash(requesterIdentity)
        ) // TODO: get from |request|

        val credential = document.findCredential(
            WalletApplication.CREDENTIAL_DOMAIN,
            Timestamp.now()
        )
        if (credential == null) {
            throw IllegalStateException("No credential")
        }
        val staticAuthData = StaticAuthDataParser(credential.issuerProvidedData).parse()
        val mergedIssuerNamespaces = MdocUtil.mergeIssuerNamesSpaces(
            documentRequest, credConf.staticData, staticAuthData
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
            completeResponse(credential, deviceResponseGenerator, documentGenerator,
                requesterIdentity, encodedSessionTranscript)
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
                    completeResponse(credential, deviceResponseGenerator, documentGenerator,
                        requesterIdentity, encodedSessionTranscript)
                },
                onCanceled = {},
                onError = {
                    Logger.i(TAG, "Biometric auth failed", e)
                }
            )
        }
    }

    private fun addDeviceNamespaces(
        documentGenerator: DocumentGenerator,
        authKey: Credential,
        unlockData: KeyUnlockData?
    ) {
        documentGenerator.setDeviceNamespacesSignature(
            NameSpacedData.Builder().build(),
            authKey.secureArea,
            authKey.alias,
            unlockData,
            Algorithm.ES256)
    }

    private fun completeResponse(
        authKey: Credential,
        deviceResponseGenerator: DeviceResponseGenerator,
        documentGenerator: DocumentGenerator,
        requesterIdentity: PublicKey,
        encodedSessionTranscript: ByteArray
    ) {
        deviceResponseGenerator.addDocument(documentGenerator.generate())
        val encodedDeviceResponse = deviceResponseGenerator.generate()
        //log("Response: " + CborUtil.toDiagnostics(encodedDeviceResponse,
        //    CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT + CborUtil.DIAGNOSTICS_FLAG_EMBEDDED_CBOR))

        val credmanUtil = CredmanUtil(requesterIdentity, null)
        val (cipherText, encapsulatedPublicKey) = credmanUtil.encrypt(encodedDeviceResponse, encodedSessionTranscript)
        val encodedCredentialDocument = CredmanUtil.generateCredentialDocument(cipherText, encapsulatedPublicKey)
        authKey.increaseUsageCount()

        val bundle = Bundle()
        bundle.putByteArray("identityToken",
            Base64.encodeToString(encodedCredentialDocument, Base64.NO_WRAP or Base64.URL_SAFE)
                .toByteArray()
        )
        val credentialResponse = com.google.android.gms.identitycredentials.Credential("type", bundle)
        val response = GetCredentialResponse(credentialResponse)
        val resultData = Intent()
        IntentHelper.setGetCredentialResponse(resultData, response)
        setResult(RESULT_OK, resultData)
        finish()
    }
}
