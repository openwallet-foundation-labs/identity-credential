package com.android.identity_credential.wallet.presentation

import androidx.fragment.app.FragmentActivity
import com.android.identity.android.securearea.AndroidKeystoreKeyUnlockData
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.securearea.UserAuthenticationType
import com.android.identity.cbor.Cbor
import com.android.identity.crypto.Algorithm
import com.android.identity.document.Document
import com.android.identity.document.NameSpacedData
import com.android.identity.issuance.DocumentExtensions.documentConfiguration
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.mdoc.mso.MobileSecurityObjectParser
import com.android.identity.mdoc.mso.StaticAuthDataParser
import com.android.identity.mdoc.response.DeviceResponseGenerator
import com.android.identity.mdoc.response.DocumentGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.securearea.KeyLockedException
import com.android.identity.securearea.KeyUnlockData
import com.android.identity.securearea.software.SoftwareKeyInfo
import com.android.identity.securearea.software.SoftwareKeyUnlockData
import com.android.identity.securearea.software.SoftwareSecureArea
import com.android.identity.util.Constants
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.WalletApplication
import com.android.identity_credential.wallet.presentation.PresentationFlowActivity.PromptUnsuccessfulException
import com.android.identity_credential.wallet.ui.prompt.biometric.showBiometricPrompt
import com.android.identity_credential.wallet.ui.prompt.consent.showConsentPrompt
import com.android.identity_credential.wallet.ui.prompt.passphrase.showPassphrasePrompt
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Clock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * A base Activity that provides functions for initiating an MDL Presentation flow and obtaining the
 * response bytes to send to the requesting party.
 *
 * Subclassing Activities call function [showPresentationFlow] with the request bytes parsed to a
 * [PresentationRequestData]. Response bytes are (eventually) generated after authentication prompts
 * validate the user successfully, else an exception is thrown.
 *
 * Exceptions are thrown for numerous reasons such as,
 * - [IllegalStateException] if credentials could not be found in the Document; authentication
 * type was not specified when showing Biometric prompt; a new secure area has not been handled yet
 * - [PromptUnsuccessfulException] if a prompt is unsuccessful when user cancels the prompt or is
 * unable to authenticate.
 */
open class PresentationFlowActivity : FragmentActivity() {
    companion object {
        const val TAG = "PresentationFlowActivity"
    }

    // reference WalletApplication for obtaining dependencies
    private val walletApp: WalletApplication by lazy {
        application as WalletApplication
    }

    // private class dedicated to managing/providing Mdoc-related functions needed by this Activity
    private val mdocHelper = MdocHelper()

    /**
     * Show the Presentation Flow starting with Consent prompt, followed by Biometric prompt
     * and/or Passphrase prompt. If all the prompts are successful, return the response bytes.
     * Otherwise throw the Exception that prevented the Presentation flow from generating the
     * response bytes.
     *
     * @param presentationRequestData the request data produced after parsing request bytes
     * @return the response bytes generated after all prompts succeed.
     * @throws [IllegalStateException] for incorrect configurations or if cannot find credential.
     * @throws [PromptUnsuccessfulException] if a prompt does not succeed because the user cancelled
     * or is unable to authenticate.
     */
    suspend fun showPresentationFlow(presentationRequestData: PresentationRequestData): ByteArray {
        val credential = presentationRequestData.document.getMdocCredential()
            ?: throw IllegalStateException("Unable to find credential in Document ${presentationRequestData.document.name}")

        // show Consent Prompt
        showConsentPrompt(
            activity = this,
            presentationRequestData = presentationRequestData,
            documentTypeRepository = walletApp.documentTypeRepository,
        ).let { resultSuccess ->
            if (!resultSuccess) {
                throw PromptUnsuccessfulException("Consent")
            }
        }

        // initially null and updated during a KeyLockedException in the while-loop
        var keyUnlockData: KeyUnlockData? = null

        while (true) {
            try {
                // try generate the response bytes or throw an Exception
                val responseBytes =
                    mdocHelper.generateDeviceResponse(
                        presentationRequestData,
                        credential,
                        keyUnlockData
                    )
                // at this point all corresponding secure areas have unlocked auth keys
                credential.increaseUsageCount()
                return responseBytes
            }
            // if KeyLockedException is raised show the corresponding Prompt to unlock the auth key
            catch (_: KeyLockedException) {
                when (credential.secureArea) {
                    // show Biometric prompt
                    is AndroidKeystoreSecureArea -> {
                        val unlockData = AndroidKeystoreKeyUnlockData(credential.alias)
                        val cryptoObject = unlockData.getCryptoObjectForSigning(Algorithm.ES256)

                        // update the KeyUnlockData
                        keyUnlockData = unlockData

                        val successfulBiometricResult = showBiometricPrompt(
                            activity = this,
                            title = resources.getString(R.string.presentation_biometric_prompt_title),
                            subtitle = resources.getString(R.string.presentation_biometric_prompt_subtitle),
                            cryptoObject = cryptoObject,
                            userAuthenticationTypes = setOf(
                                UserAuthenticationType.BIOMETRIC,
                                UserAuthenticationType.LSKF
                            ),
                            requireConfirmation = false
                        )

                        // if the prompt was not successful (unable to authenticate), throw Exception
                        if (!successfulBiometricResult) {
                            throw PromptUnsuccessfulException("Biometric")
                        }
                    }

                    // show Passphrase prompt
                    is SoftwareSecureArea -> {
                        val softwareKeyInfo =
                            credential.secureArea.getKeyInfo(credential.alias) as SoftwareKeyInfo

                        val passphrase = showPassphrasePrompt(
                            activity = this,
                            constraints = softwareKeyInfo.passphraseConstraints!!,
                            checkWeakPassphrase = softwareKeyInfo.isPassphraseProtected,
                        )

                        if (passphrase.isEmpty()) {
                            throw PromptUnsuccessfulException("Passphrase")
                        }

                        // use the passphrase that the user entered to create the KeyUnlockData
                        keyUnlockData = SoftwareKeyUnlockData(passphrase)
                    }

                    // for secure areas not yet implemented
                    else -> {
                        throw IllegalStateException("No prompts implemented for secure area ${credential.secureArea}")
                    }
                }
            }
        }
    }

    /**
     * Exception thrown when a prompt is cancelled during a Presentation, rather than throwing an
     * [IllegalStateException]. This is to differentiate unsuccessful prompts from other events
     * that throw [IllegalStateException] so an appropriate message can be shown if needed.
     *
     * @param promptType the prompt type that was unsuccessful (user cancelled or unauthorized)
     */
    class PromptUnsuccessfulException(promptType: String) :
        Exception("The $promptType Prompt Not Successful!")

    /**
     * Helper class that provides Mdoc-related functions for [PresentationFlowAcivity].
     */
    private class MdocHelper {

        /**
         * Generate the response bytes for a given request. This async function generates
         * a [Document] based on the document request and adds it to the device response which
         * includes signing the document data with the specified credential.
         * Throws exceptions such as [KeyLockedException] if the auth key is locked for a secure area.
         *
         * @param presentationRequestData the object containing the request information/data
         * @param credential the credential to use for signing the document data
         * @param keyUnlockData unlock data for the authentication key, or `null`.
         * @return the bytes of the generated document response.
         */
        suspend fun generateDeviceResponse(
            presentationRequestData: PresentationRequestData,
            credential: MdocCredential,
            keyUnlockData: KeyUnlockData?
        ): ByteArray {
            val documentGenerator = createDocumentGenerator(presentationRequestData, credential)
            return addDocumentToDeviceResponse(
                documentGenerator,
                credential,
                keyUnlockData
            )
        }

        /**
         * Create the [DocumentGenerator] responsible for generating the [Document] for the
         * requested credentials.
         *
         * @param presentationRequestData the data object containing the request [Document] and
         *                                other document configurations.
         * @param credential the credential used for signing the document data.
         * @return a unique [DocumentGenerator] that is used to generate the [Document]
         * after signing the Document's data.
         */
        private fun createDocumentGenerator(
            presentationRequestData: PresentationRequestData,
            credential: MdocCredential
        ): DocumentGenerator {
            val docConfiguration = presentationRequestData.document.documentConfiguration
            val staticAuthData = StaticAuthDataParser(credential.issuerProvidedData).parse()
            val mergedIssuerNamespaces = MdocUtil.mergeIssuerNamesSpaces(
                presentationRequestData.documentRequest,
                docConfiguration.mdocConfiguration!!.staticData,
                staticAuthData
            )

            val issuerAuthCoseSign1 = Cbor.decode(staticAuthData.issuerAuth).asCoseSign1
            val encodedMsoBytes = Cbor.decode(issuerAuthCoseSign1.payload!!)
            val encodedMso = Cbor.encode(encodedMsoBytes.asTaggedEncodedCbor)
            val mso = MobileSecurityObjectParser(encodedMso).parse()

            val documentGenerator = DocumentGenerator(
                mso.docType,
                staticAuthData.issuerAuth,
                presentationRequestData.sessionTranscript
            )
            documentGenerator.setIssuerNamespaces(mergedIssuerNamespaces)
            return documentGenerator
        }

        /**
         * Try adding adding the [Document], generated from [DocumentGenerator], to the device
         * response and retyrn the resulting bytes. If the passed credential has a locked auth key
         * then a [KeyLockedException] will be thrown
         *
         *
         * @param documentGenerator instance of [DocumentGenerator] that generates the [Document]
         * @param credential the [MdocCredential] to use for signing the generated [Document]
         * @return the bytes of the response document to be sent to the device.
         * @throws [KeyLockedException] if the credential has a locked auth key.
         */
        suspend fun addDocumentToDeviceResponse(
            documentGenerator: DocumentGenerator,
            credential: MdocCredential,
            keyUnlockData: KeyUnlockData?,
        ): ByteArray = suspendCancellableCoroutine { continuation ->
            val deviceResponseGenerator =
                DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)
            try {
                documentGenerator.updateDeviceNamespacesSignature(credential, keyUnlockData)
                deviceResponseGenerator.addDocument(documentGenerator.generate())
                continuation.resume(deviceResponseGenerator.generate())
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }

        /**
         * Helper function extending [DocumentGenerator] that updates the signature used to sign the
         * generated [Document]. Throws a [KeyLockedException] if the authentication key is locked.

         * @param credential the [MdocCredential] to use (for a secure area).
         * @param unlockData data used to create the signature for signing the key in a secure area
         * @throws [KeyLockedException] if the authentication key is locked.
         */
        fun DocumentGenerator.updateDeviceNamespacesSignature(
            credential: MdocCredential,
            unlockData: KeyUnlockData?
        ) {
            setDeviceNamespacesSignature(
                NameSpacedData.Builder().build(),
                credential.secureArea,
                credential.alias,
                unlockData,
                Algorithm.ES256
            )
        }
    }

    /**
     * Helper extension function extending [Document] to find and return the MDoc Credential.
     * @return the [MdocCredential] if one could be found on the [Document], or [null].
     */
    fun Document.getMdocCredential(): MdocCredential? = findCredential(
        WalletApplication.CREDENTIAL_DOMAIN_MDOC,
        Clock.System.now()
    ) as MdocCredential?
}