package com.android.identity_credential.wallet.presentation

import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.android.identity.android.securearea.AndroidKeystoreKeyUnlockData
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.securearea.UserAuthenticationType
import com.android.identity.cbor.Cbor
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.javaX509Certificates
import com.android.identity.document.Document
import com.android.identity.document.DocumentRequest
import com.android.identity.document.NameSpacedData
import com.android.identity.issuance.CredentialFormat
import com.android.identity.issuance.DocumentExtensions.documentConfiguration
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.mdoc.mso.MobileSecurityObjectParser
import com.android.identity.mdoc.mso.StaticAuthDataParser
import com.android.identity.mdoc.request.DeviceRequestParser
import com.android.identity.mdoc.response.DeviceResponseGenerator
import com.android.identity.mdoc.response.DocumentGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.securearea.KeyLockedException
import com.android.identity.securearea.KeyUnlockData
import com.android.identity.securearea.software.SoftwareKeyInfo
import com.android.identity.securearea.software.SoftwareKeyUnlockData
import com.android.identity.securearea.software.SoftwareSecureArea
import com.android.identity.trustmanagement.TrustPoint
import com.android.identity.util.Constants
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.WalletApplication
import com.android.identity_credential.wallet.ui.prompt.biometric.showBiometricPrompt
import com.android.identity_credential.wallet.ui.prompt.consent.showConsentPrompt
import com.android.identity_credential.wallet.ui.prompt.passphrase.showPassphrasePrompt
import kotlinx.datetime.Clock

/**
 * Class responsible for showing the Presentment Flow for every credential [Document] that is
 * requested by some "Verifying party". The purpose of the Presentment Flow is to authorize a
 * [Document] to be "sent" to the Verifier by showing a series of consent and authentication prompts
 * to the user. After a successful Presentment, the [Document] used during the Presentment is added
 * to the response to the Verifier. When all Presentment Flows have ended (successfully
 * or not), the response bytes are generated and contain all the [Document]s from successful
 * Presentments.
 *
 * An [Exception] (mostly an [IllegalStateException]) is thrown for numerous reasons, such as
 * if credentials could not be found in a Document; authentication type was not specified when
 * showing Biometric prompt; a (new) secure area has not been handled/implemented yet;
 * if a prompt is unsuccessful because the user cancelled or was not able to authenticate, etc..
 *
 * @param walletApp the WalletApplication instance used for dependencies.
 * @param fragmentActivity used for showing Dialog Fragments.
 */
class PresentmentFlow(
    private val walletApp: WalletApplication,
    private val fragmentActivity: FragmentActivity
) {
    private companion object {
        const val TAG = "PresentmentFlow"
    }

    /**
     * Parses the given device request bytes to produce a list of [DocumentRequest] objects.
     * Finds a suitable [Document] for every requested document and starts the Presentment Flow to
     * authorize the [Document] to be added to the response payload after the Presentment is
     * successful. Finally, generates and returns the response bytes that has one or more
     * [Document]s in it.
     *
     * The Presentment Flow always starts by showing the Consent Prompt to confirm with the user
     * before sending sensitive data of a [Document] to a Verifying party. If the Consent Prompt
     * is successful then the Biometric Prompt and/or Passphrase Prompt will be shown so long as the
     * auth key is locked for their corresponding Secure Area. If all the Presentment Prompts are
     * successful then the [Document] that was used by the Prompts is added to
     * [DeviceResponseGenerator] to eventually generate the `DeviceResponse` CBOR bytes.
     *
     * If a Presentment Flow is unsuccessful because the user cancels any of its prompts or the user
     * is unable to authenticate, then the [Document] is not added to the response generator.
     *
     * @param encodedDeviceRequest the bytes of the `DeviceRequest` CBOR that can have one or more
     *      documents requested ([DocumentRequest])
     * @param encodedSessionTranscript the bytes of `SessionTranscript`.
     * @return an response bytes that contains at least one [Document], else throws an
     *      [IllegalStateException].
     * @throws Exception for incorrect configurations, cannot find credentials or unsuccessful
     *      prompts because user cancelled or wasn't able to authenticate.
     */
    suspend fun showPresentmentFlow(
        encodedDeviceRequest: ByteArray,
        encodedSessionTranscript: ByteArray
    ): ByteArray {
        // parse the bytes into a device request
        val request = DeviceRequestParser(encodedDeviceRequest, encodedSessionTranscript).parse()

        // TODO: add SD_JWT_VC support
        // the supported formats for the Credentials of a Document
        val supportedCredentialFormats = listOf(CredentialFormat.MDOC_MSO)

        // all successful Presentation Flows have their Document added to this response generator
        val deviceResponseGenerator = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)

        // iterate through all docRequests and show a Presentment Flow for each with a suitable
        // Document. If any prompts in the Presentment are unsuccessful, the Document is not added
        // to the response generator.
        request.docRequests.forEach { docRequest ->

            /**
             * Return whether the given [Document] matches the requested [docType] that also has at
             * least one [Credential] with a [CredentialFormat] listed in the
             * [supportedCredentialFormats]. This function is nested here as a local function for
             * maintaining high cohesion and grouping this reusable function near the location of
             * where its used (directly underneath this function).
             *
             * @param document the [Document] being checked for suitability for a [DocumentRequest]
             * @param docType the mDoc Document Type that the [document] should have for a match
             * @param supportedCredentialFormats a list of [CredentialFormat]s that are supported
             *      for authentication - this applies Credentials of a [Document].
             * @return a Boolean, [true] if the [document] matches the requested [docType] and has
             * at least one Credential where its [CredentialFormat] is supported/listed in
             * param [supportedCredentialFormats].
             */
            fun isDocumentSuitableForDocRequest(
                document: Document,
                docType: String,
                supportedCredentialFormats: List<CredentialFormat>,
            ): Boolean {
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

            // find the most appropriate Document to use for each docRequest
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

            // if document == null no suitable documents could be found then skip to next docRequest
            if (document == null) {
                Toast.makeText(
                    fragmentActivity,
                    "Cannot find Document to add to the reply for docType: ${docRequest.docType}",
                    Toast.LENGTH_LONG
                ).show()
            }
            // else proceed with showing the Presentment Flow for current docType
            else {
                val mdocCredential = document!!.findCredential(
                    WalletApplication.CREDENTIAL_DOMAIN_MDOC,
                    Clock.System.now()
                ) as MdocCredential

                // extract the TrustPoint if possible
                var trustPoint: TrustPoint? = null
                if (docRequest.readerAuthenticated) {
                    val result = walletApp.trustManager.verify(
                        docRequest.readerCertificateChain!!.javaX509Certificates,
                        customValidators = emptyList()  // not needed for reader auth
                    )
                    if (result.isTrusted && result.trustPoints.isNotEmpty()) {
                        trustPoint = result.trustPoints.first()
                    } else if (result.error != null) {
                        Logger.w(TAG, "Error finding TrustPoint for reader auth", result.error!!)
                    }
                }

                // generate the DocumentRequest from the current docRequest
                val documentRequest = MdocUtil.generateDocumentRequest(docRequest)

                /////  Start showing Presentment Prompts  /////

                // always show the Consent Prompt first
                showConsentPrompt(
                    activity = fragmentActivity,
                    documentTypeRepository = walletApp.documentTypeRepository,
                    document = mdocCredential.document,
                    documentRequest = documentRequest,
                    trustPoint = trustPoint
                ).let { resultSuccess ->
                    // throw exception if user cancelled the Prompt
                    check(resultSuccess) { "[Consent Unsuccessful]" }
                }

                // initially null and updated when catching a KeyLockedException in the while-loop below
                var keyUnlockData: KeyUnlockData? = null

                while (true) {
                    try {
                        // create the document generator for the suitable Document (of DocumentRequest)
                        val documentGenerator =
                            createDocumentGenerator(
                                docRequest = documentRequest,
                                document = mdocCredential.document,
                                credential = mdocCredential,
                                sessionTranscript = encodedSessionTranscript
                            )
                        // try signing the data of the document (or KeyLockedException is thrown)
                        documentGenerator.setDeviceNamespacesSignature(
                            NameSpacedData.Builder().build(),
                            mdocCredential.secureArea,
                            mdocCredential.alias,
                            keyUnlockData,
                            Algorithm.ES256
                        )
                        // finally add the document to the response generator
                        deviceResponseGenerator.addDocument(documentGenerator.generate())
                        // at this point all corresponding secure areas have unlocked auth keys
                        mdocCredential.increaseUsageCount()

                        // we were successful at generating the Document, break out of while loop and
                        // iterate to the next parsed docRequest and show the Presentment Flow.
                        break
                    }
                    // if KeyLockedException is raised show the corresponding Prompt to unlock
                    // the auth key for a Credential's Secure Area
                    catch (_: KeyLockedException) {
                        when (mdocCredential.secureArea) {
                            // show Biometric prompt
                            is AndroidKeystoreSecureArea -> {
                                val unlockData =
                                    AndroidKeystoreKeyUnlockData(mdocCredential.alias)
                                val cryptoObject =
                                    unlockData.getCryptoObjectForSigning(Algorithm.ES256)

                                // update KeyUnlockData to be used on the next loop iteration
                                keyUnlockData = unlockData

                                val successfulBiometricResult = showBiometricPrompt(
                                    activity = fragmentActivity,
                                    title = fragmentActivity.resources.getString(R.string.presentation_biometric_prompt_title),
                                    subtitle = fragmentActivity.resources.getString(R.string.presentation_biometric_prompt_subtitle),
                                    cryptoObject = cryptoObject,
                                    userAuthenticationTypes = setOf(
                                        UserAuthenticationType.BIOMETRIC,
                                        UserAuthenticationType.LSKF
                                    ),
                                    requireConfirmation = false
                                )
                                // if user cancelled or was unable to authenticate, throw IllegalStateException
                                check(successfulBiometricResult) { "[Biometric Unsuccessful]" }
                            }

                            // show Passphrase prompt
                            is SoftwareSecureArea -> {
                                val softwareKeyInfo =
                                    mdocCredential.secureArea.getKeyInfo(mdocCredential.alias) as SoftwareKeyInfo

                                val passphrase = showPassphrasePrompt(
                                    activity = fragmentActivity,
                                    constraints = softwareKeyInfo.passphraseConstraints!!,
                                    checkWeakPassphrase = softwareKeyInfo.isPassphraseProtected,
                                )
                                // ensure the passphrase is not empty, else throw IllegalStateException
                                check(passphrase.isNotEmpty()) { "[Passphrase Unsuccessful]" }
                                // use the passphrase that the user entered to create the KeyUnlockData
                                keyUnlockData = SoftwareKeyUnlockData(passphrase)
                            }

                            // for secure areas not yet implemented
                            else -> {
                                throw IllegalStateException("No prompts implemented for Secure Area ${mdocCredential.secureArea.displayName}")
                            }
                        }
                    }
                }
            }
        }

        // generate bytes of all Documents added to the response on successful Presentment Flows
        return deviceResponseGenerator.generate()
    }

    /**
     * Create the [DocumentGenerator] responsible for generating the [Document] for the
     * requested credentials in [DocumentRequest].
     *
     * @param docRequest the [DocumentRequest] containing a listing of data elements of asd
     *      ocument to verify.
     * @param document a suitable [Document] that satisfies the [docRequest].
     * @param credential the credential used for signing the document data.
     * @param sessionTranscript the bytes of the SessionTrancript CBOR
     * @return a unique [DocumentGenerator] that is used to generate the [Document]
     * after signing the Document's data.
     */
    private fun createDocumentGenerator(
        docRequest: DocumentRequest,
        document: Document,
        credential: MdocCredential,
        sessionTranscript: ByteArray,
    ): DocumentGenerator {
        val docConfiguration = document.documentConfiguration
        val staticAuthData = StaticAuthDataParser(credential.issuerProvidedData).parse()
        val mergedIssuerNamespaces = MdocUtil.mergeIssuerNamesSpaces(
            docRequest,
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
            sessionTranscript
        )
        documentGenerator.setIssuerNamespaces(mergedIssuerNamespaces)
        return documentGenerator
    }
}