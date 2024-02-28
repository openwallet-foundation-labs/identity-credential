package com.android.identity_credential.wallet.presentation

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper
import com.android.identity.cbor.Cbor
import com.android.identity.credential.CredentialRequest
import com.android.identity.credential.CredentialStore
import com.android.identity.credential.NameSpacedData
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.javaX509Certificates
import com.android.identity.issuance.CredentialExtensions.credentialConfiguration
import com.android.identity.issuance.CredentialExtensions.issuingAuthorityIdentifier
import com.android.identity.issuance.CredentialPresentationFormat
import com.android.identity.issuance.IssuingAuthorityRepository
import com.android.identity.mdoc.mso.MobileSecurityObjectParser
import com.android.identity.mdoc.mso.StaticAuthDataParser
import com.android.identity.mdoc.request.DeviceRequestParser
import com.android.identity.mdoc.response.DeviceResponseGenerator
import com.android.identity.mdoc.response.DocumentGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.trustmanagement.TrustManager
import com.android.identity.trustmanagement.TrustPoint
import com.android.identity.util.Constants
import com.android.identity.util.Logger
import com.android.identity.util.Timestamp
import com.android.identity_credential.wallet.SelfSignedMdlIssuingAuthority
import com.android.identity_credential.wallet.WalletApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.OptionalLong

/**
 * Transfer Helper provides helper functions for starting to process a presentation request, as well
 * as finishing processing the request to get response bytes to send to requesting party (after user
 * has accepted one or more dialog prompts)
 */
class TransferHelper(
    private val credentialStore: CredentialStore,
    private val issuingAuthorityRepository: IssuingAuthorityRepository,
    private val trustManager: TrustManager,
    private val context: Context,
    private val deviceRetrievalHelper: DeviceRetrievalHelper,
    private val onError: (Throwable) -> Unit
) {
    companion object {
        private const val TAG = "TransferHelper"
    }

    /**
     * Builder class returning a new TransferHelper instance with a new deviceRetrievalHelper object.
     */
    class Builder(
        val credentialStore: CredentialStore,
        val issuingAuthorityRepository: IssuingAuthorityRepository,
        val trustManager: TrustManager,
        val context: Context,
        var deviceRetrievalHelper: DeviceRetrievalHelper? = null,
        var onError: (Throwable) -> Unit = {},
    ) {
        fun setDeviceRetrievalHelper(deviceRetrievalHelper: DeviceRetrievalHelper) = apply {
            this.deviceRetrievalHelper = deviceRetrievalHelper
        }

        fun build() = TransferHelper(
            credentialStore = credentialStore,
            issuingAuthorityRepository = issuingAuthorityRepository,
            trustManager = trustManager,
            context = context,
            deviceRetrievalHelper = deviceRetrievalHelper!!,
            onError = onError
        )
    }

    // used for reading credential visible to user
    private val sharedPreferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    /**
     * Start processing the presentation request and return a [PresentationRequestData] object that
     * is used in finishProcessingRequest() to generate response bytes to send to requesting party.
     *
     * @param deviceRequest the request bytes for initiating a Presentation
     * @return a PresentationRequestData object containing data used to finish processing the request
     * and generate response bytes, or null if no credential id could be found.
     */
    fun startProcessingRequest(deviceRequest: ByteArray): PresentationRequestData? {

        // TODO support more formats
        val credentialPresentationFormat: CredentialPresentationFormat =
            CredentialPresentationFormat.MDOC_MSO

        // TODO when selecting a matching credential of the MDOC_MSO format, also use docRequest.docType
        //     to select a credential of the right doctype
        val credentialId: String = firstMatchingCredentialID(credentialPresentationFormat)
            ?: run {
                onError(IllegalStateException("No matching credentials in wallet"))
                return null
            }

        val credential = credentialStore.lookupCredential(credentialId)!!
        val request = DeviceRequestParser(deviceRequest, deviceRetrievalHelper.sessionTranscript).parse()
        val docRequest = request.documentRequests[0]

        var trustPoint: TrustPoint? = null
        if (docRequest.readerAuthenticated) {
            val result = trustManager.verify(
                docRequest.readerCertificateChain!!.javaX509Certificates,
                customValidators = emptyList()  // not neeeded for reader auth
            )
            if (result.isTrusted && !result.trustPoints.isEmpty()) {
                trustPoint = result.trustPoints.first()
            } else if (result.error != null) {
                Logger.w(TAG, "Error finding trustpoint for reader auth", result.error!!)
            }
        }

        val credentialRequest = MdocUtil.generateCredentialRequest(docRequest!!)
        val requestedDocType: String = docRequest.docType
        return PresentationRequestData(
            credential,
            credentialRequest,
            requestedDocType,
            trustPoint
        )
    }

    /**
     * Finish processing the request and produce response bytes to be sent to requesting party.
     * This is called once the user accepted various prompts (consent, biometrics, etc..)
     *
     * Expects 3 arguments generated from [startProcessingRequest] along with a callback for when
     * processing is finished.
     *
     * @param requestedDocType the type of credential document requested
     * @param credentialId the id of the credential to send data from
     * @param credentialRequest the object containing list of DataElements for the user to approve
     * @param onFinishedProcessing callback when processing finished to give UI a chance to update
     */
    suspend fun finishProcessingRequest(
        requestedDocType: String,
        credentialId: String,
        credentialRequest: CredentialRequest,
        onFinishedProcessing: (ByteArray) -> Unit
    ) = withContext(Dispatchers.IO) {
        val credential = credentialStore.lookupCredential(credentialId)!!
        val encodedDeviceResponse: ByteArray
        if (requestedDocType == SelfSignedMdlIssuingAuthority.MDL_DOCTYPE) {
            val credentialConfiguration = credential.credentialConfiguration
            val now = Timestamp.now()
            val authKey = credential.findAuthenticationKey(WalletApplication.AUTH_KEY_DOMAIN, now)
                ?: run {
                    onError(IllegalStateException("No valid auth keys, please request more"))
                    return@withContext
                }

            val staticAuthData = StaticAuthDataParser(authKey.issuerProvidedData).parse()
            val issuerAuthCoseSign1 = Cbor.decode(staticAuthData.issuerAuth).asCoseSign1
            val encodedMsoBytes = Cbor.decode(issuerAuthCoseSign1.payload!!)
            val encodedMso = Cbor.encode(encodedMsoBytes.asTaggedEncodedCbor)
            val mso = MobileSecurityObjectParser(encodedMso).parse()

            val mergedIssuerNamespaces = MdocUtil.mergeIssuerNamesSpaces(
                credentialRequest,
                credentialConfiguration.staticData,
                staticAuthData
            )

            val deviceResponseGenerator =
                DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)
            deviceResponseGenerator.addDocument(
                DocumentGenerator(
                    mso.docType,
                    staticAuthData.issuerAuth, deviceRetrievalHelper!!.sessionTranscript
                )
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
            onError(IllegalStateException("$requestedDocType not available"))
            return@withContext
        }
        onFinishedProcessing(encodedDeviceResponse)
    }


    /**
     * Send response bytes of credential data to requesting party
     * @param deviceResponseBytes response bytes to send to requesting party
     */
    fun sendResponse(deviceResponseBytes: ByteArray) {
        deviceRetrievalHelper.sendDeviceResponse(
            deviceResponseBytes,
            OptionalLong.of(Constants.SESSION_DATA_STATUS_SESSION_TERMINATION)
        )
    }

    /**
     * Return the first credential ID that is matched with either in SharedPreferences (credential
     * that is visible to user) or from CredentialStore.
     *
     * @param credentialPresentationFormat the presentation format type for which credentials are queried
     * @return the found credential id
     */
    private fun firstMatchingCredentialID(
        credentialPresentationFormat: CredentialPresentationFormat
    ): String? {
        // prefer the credential which is on-screen if possible
        val credentialIdFromPager: String? = sharedPreferences
            .getString(WalletApplication.PREFERENCE_CURRENT_CREDENTIAL_ID, null)
        if (credentialIdFromPager != null
            && isCredentialValid(credentialIdFromPager, credentialPresentationFormat)
        ) {
            return credentialIdFromPager
        }

        return credentialStore.listCredentials().firstOrNull { credentialId ->
            isCredentialValid(credentialId, credentialPresentationFormat)
        }
    }

    /**
     * Return whether the passed credential id is valid (it exists in credential store and the
     * issuing authority recognizes the presentation format)
     * @param credentialId id of credential to validate
     * @param credentialPresentationFormat the presentation format for transferring credential data
     * @return whether the specified credential id is valid for the specified presentation format
     */
    private fun isCredentialValid(
        credentialId: String,
        credentialPresentationFormat: CredentialPresentationFormat
    ): Boolean {
        val credential = credentialStore.lookupCredential(credentialId)!!
        val issuingAuthorityIdentifier = credential.issuingAuthorityIdentifier
        val issuer =
            issuingAuthorityRepository.lookupIssuingAuthority(issuingAuthorityIdentifier)
                ?: throw IllegalArgumentException("No issuer with id $issuingAuthorityIdentifier")
        val credentialFormats = issuer.configuration.credentialFormats
        return credentialFormats.contains(credentialPresentationFormat)
    }
}