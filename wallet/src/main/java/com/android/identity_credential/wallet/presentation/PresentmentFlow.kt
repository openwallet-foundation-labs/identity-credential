package com.android.identity_credential.wallet.presentation

import androidx.fragment.app.FragmentActivity
import com.android.identity.android.securearea.AndroidKeystoreKeyUnlockData
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.securearea.UserAuthenticationType
import com.android.identity.cbor.Cbor
import com.android.identity.crypto.Algorithm
import com.android.identity.document.Document
import com.android.identity.document.DocumentRequest
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
import com.android.identity.trustmanagement.TrustPoint
import com.android.identity.util.Constants
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.WalletApplication
import com.android.identity_credential.wallet.ui.prompt.biometric.showBiometricPrompt
import com.android.identity_credential.wallet.ui.prompt.consent.showConsentPrompt
import com.android.identity_credential.wallet.ui.prompt.passphrase.showPassphrasePrompt

const val TAG = "PresentmentFlow"

/**
 * Function responsible for showing the Presentment Flow for a given [DocumentRequest] and returning
 * the [Document] CBOR bytes or throw an Exception. The purpose of the Presentment Flow is to
 * authorize a [Document] that is suitable for fulfilling the specified [DocumentRequest],
 * by showing a series of consent and authentication Prompts to the user and upon success
 * the [Document] CBOR bytes are returned.
 *
 * The Presentment Flow always starts by showing the Consent Prompt to confirm with the user
 * before sending sensitive data of a [Document] to a Verifying party. If the Consent Prompt
 * is successful then the Biometric Prompt and/or Passphrase Prompt will be shown so long as the
 * auth key is locked for their corresponding Secure Area.
 *
 * An [Exception] (mostly [IllegalStateException]) is thrown for numerous reasons, such as
 * if credentials could not be found in a Document; authentication type was not specified when
 * showing Biometric prompt; a (new) secure area has not been handled/implemented yet; if a prompt
 * is unsuccessful because the user cancelled or was not able to authenticate, etc..
 *
 * @param activity the [FragmentActivity] used for showing Dialog Fragments.
 * @param walletApp the [WalletApplication] instance used for dependencies.
 * @param documentRequest a [DocumentRequest] instance defining a list of
 *      credential fields/data elements to obtain from the suitable [Document] in MdocCredential.
 * @param mdocCredential the object containing the [Document] and docType amongst other properties.
 * @param trustPoint if provided, identifies the Verifying party.
 * @param encodedSessionTranscript the bytes of `SessionTranscript` CBOR.
 * @return the [Document] CBOR bytes, else throws an exception.
 * @throws Exception for incorrect configurations, cannot find credentials or unsuccessful
 *      prompts because user cancelled or wasn't able to authenticate.
 */
suspend fun showPresentmentFlow(
    activity: FragmentActivity,
    walletApp: WalletApplication,
    documentRequest: DocumentRequest,
    mdocCredential: MdocCredential,
    trustPoint: TrustPoint?,
    encodedSessionTranscript: ByteArray,
): ByteArray {
    // always show the Consent Prompt first
    showConsentPrompt(
        activity = activity,
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
            // increment the credential's usage count since it just finished signing the data successfully
            mdocCredential.increaseUsageCount()
            // finally add the document to the response generator and generate the bytes
            return documentGenerator.generate()
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
                        activity = activity,
                        title = activity.resources.getString(R.string.presentation_biometric_prompt_title),
                        subtitle = activity.resources.getString(R.string.presentation_biometric_prompt_subtitle),
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
                        activity = activity,
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

/**
 * Create the [DocumentGenerator] responsible for generating the [Document] for the
 * requested credentials in [DocumentRequest]. This function encapsulates the functionality of
 * creating a [DocumentGenerator] so the code that calls this function isn't overcrowded by
 * lower-level details needed to create a [DocumentGenerator].
 *
 * @param docRequest the [DocumentRequest] containing a listing of data elements to verify.
 * @param document a suitable [Document] that satisfies the [docRequest].
 * @param credential the credential used for signing the document data.
 * @param sessionTranscript the bytes of the SessionTrancript CBOR.
 * @return a unique [DocumentGenerator] that is used to generate the [Document]
 * after signing the Document's data.
 */
private fun createDocumentGenerator(
    docRequest: DocumentRequest,
    document: Document,
    credential: MdocCredential,
    sessionTranscript: ByteArray,
): DocumentGenerator {
    val staticAuthData = StaticAuthDataParser(credential.issuerProvidedData).parse()
    val mergedIssuerNamespaces = MdocUtil.mergeIssuerNamesSpaces(
        docRequest,
        document.documentConfiguration.mdocConfiguration!!.staticData,
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