package org.multipaz.testapp.provisioning.model

import org.multipaz.testapp.provisioning.backend.ProvisioningBackendProvider
import org.multipaz.testapp.provisioning.backend.createOpenid4VciIssuingAuthorityByUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.credential.Credential
import org.multipaz.credential.SecureAreaBoundCredential
import org.multipaz.device.AssertionBindingKeys
import org.multipaz.document.Document
import org.multipaz.document.DocumentStore
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.prompt.PromptModel
import org.multipaz.provisioning.ApplicationSupport
import org.multipaz.provisioning.CredentialConfiguration
import org.multipaz.provisioning.CredentialFormat
import org.multipaz.provisioning.CredentialRequest
import org.multipaz.provisioning.KeyPossessionProof
import org.multipaz.provisioning.RegistrationResponse
import org.multipaz.provisioning.evidence.EvidenceRequest
import org.multipaz.provisioning.evidence.EvidenceRequestCredentialOffer
import org.multipaz.provisioning.evidence.EvidenceRequestOpenid4Vp
import org.multipaz.provisioning.evidence.EvidenceResponse
import org.multipaz.provisioning.evidence.EvidenceResponseCredentialOffer
import org.multipaz.provisioning.evidence.Openid4VciCredentialOffer
import org.multipaz.rpc.handler.RpcAuthClientSession
import org.multipaz.sdjwt.credential.KeyBoundSdJwtVcCredential
import org.multipaz.sdjwt.credential.KeylessSdJwtVcCredential
import org.multipaz.sdjwt.credential.SdJwtVcCredential
import org.multipaz.securearea.SecureAreaRepository
import org.multipaz.util.Logger
import org.multipaz.util.fromBase64Url
import kotlin.coroutines.CoroutineContext

class ProvisioningModel(
    private val provisioningBackendProvider: ProvisioningBackendProvider,
    private val documentStore: DocumentStore,
    private val promptModel: PromptModel,
    private val secureAreaRepository: SecureAreaRepository
) {
    private var pendingOffer: Openid4VciCredentialOffer? = null

    private var mutableState = MutableSharedFlow<State>()

    val state: SharedFlow<State> get() = mutableState

    private var privateApplicationSupport: ApplicationSupport? = null

    val applicationSupport: ApplicationSupport get() = privateApplicationSupport!!

    private val evidenceResponseChannel = Channel<EvidenceResponse>()

    fun startProvisioning(offer: Openid4VciCredentialOffer) {
        pendingOffer = offer
    }

    suspend fun provideEvidence(evidence: EvidenceResponse) {
        Logger.i(TAG, "Providing evidence: ${evidence::class.simpleName}")
        evidenceResponseChannel.send(evidence)
    }

    private val coreCoroutineContext =
        Dispatchers.Default + promptModel + provisioningBackendProvider.extraCoroutineContext

    var coroutineContext: CoroutineContext = coreCoroutineContext
        private set

    /**
     * Run provisioning model.
     *
     * It must be run in a coroutine scope that is cancelled when provisioning UI is
     * navigated away from.
     */
    suspend fun run(): Document? {
        val offer = pendingOffer ?: return null
        pendingOffer = null
        // Run in its own RPC session.
        coroutineContext = coreCoroutineContext + RpcAuthClientSession()
        return withContext(coroutineContext) {
            try {
                runProvisioning(offer)
            } catch(err: CancellationException) {
                throw err
            } catch(err: Throwable) {
                Logger.e(TAG, "Error provisioning", err)
                mutableState.emit(Error(err))
                null
            } finally {
                privateApplicationSupport = null
            }
        }
    }

    private suspend fun runProvisioning(offer: Openid4VciCredentialOffer): Document {
        mutableState.emit(Initial)
        privateApplicationSupport = provisioningBackendProvider.getApplicationSupport()
        mutableState.emit(Connected)
        val issuingAuthority = provisioningBackendProvider.createOpenid4VciIssuingAuthorityByUri(
            offer.issuerUri,
            offer.configurationId
        )
        val issuerConfiguration = issuingAuthority.getConfiguration()

        Logger.i(TAG, "Start registration")
        mutableState.emit(Registration)
        val registration = issuingAuthority.register()
        val documentRegistrationConfiguration =
            registration.getDocumentRegistrationConfiguration()
        val issuerDocumentIdentifier = documentRegistrationConfiguration.documentId
        val response = RegistrationResponse(
            developerModeEnabled = false
        )
        registration.sendDocumentRegistrationResponse(response)
        issuingAuthority.completeRegistration(registration)
        Logger.i(TAG, "Registration complete")

        val pendingDocumentConfiguration = issuerConfiguration.pendingDocumentInformation
        val document = documentStore.createDocument(
            displayName = pendingDocumentConfiguration.displayName,
            typeDisplayName = pendingDocumentConfiguration.typeDisplayName,
            cardArt = ByteString(pendingDocumentConfiguration.cardArt)
        )

        Logger.i(TAG, "Start proofing")
        val proofing = issuingAuthority.proof(issuerDocumentIdentifier)
        var evidenceRequests = proofing.getEvidenceRequests()

        // EvidenceRequestCredentialOffer (if requested at all) is always the first request.
        if (evidenceRequests.isNotEmpty() &&
            evidenceRequests[0] is EvidenceRequestCredentialOffer
        ) {
            Logger.i(TAG, "Sending credential offer")
            proofing.sendEvidence(EvidenceResponseCredentialOffer(offer))
            evidenceRequests = proofing.getEvidenceRequests()
        }

        while (evidenceRequests.isNotEmpty()) {
            val requestsAsText = evidenceRequests.map { it::class.simpleName }.joinToString(" ")
            Logger.i(TAG, "Requesting evidence: $requestsAsText")
            mutableState.emit(selectViableEvidenceRequests(evidenceRequests))
            val evidence = evidenceResponseChannel.receive()
            Logger.i(TAG, "Sending evidence: ${evidence::class.simpleName}")
            mutableState.emit(SendingEvidence)
            proofing.sendEvidence(evidence)
            Logger.i(TAG, "Processing evidence")
            mutableState.emit(ProcessingEvidence)
            evidenceRequests = proofing.getEvidenceRequests()
        }

        Logger.i(TAG, "Proofing complete")
        mutableState.emit(ProofingComplete)

        issuingAuthority.completeProof(proofing)

        issuingAuthority.getState(issuerDocumentIdentifier)

        val credentialCount = issuerConfiguration.numberOfCredentialsToRequest ?: 3
        val documentConfiguration = issuingAuthority.getDocumentConfiguration(issuerDocumentIdentifier)

        var pendingCredentials: List<Credential>
        var credentialConfiguration: CredentialConfiguration?

        // get the initial set of credentials
        if (documentConfiguration.sdJwtVcDocumentConfiguration?.keyBound == false) {
            // keyless, no need for keys
            pendingCredentials = listOf()
            credentialConfiguration = null
        } else {
            // create keys in the selected secure area and send them to the issuer
            val credentialWorkflow = issuingAuthority.requestCredentials(issuerDocumentIdentifier)
            val mdocConfiguration = documentConfiguration.mdocConfiguration
            val sdJwtVcDocumentConfiguration = documentConfiguration.sdJwtVcDocumentConfiguration
            if (mdocConfiguration != null) {
                credentialConfiguration =
                    credentialWorkflow.getCredentialConfiguration(CredentialFormat.MDOC_MSO)
                val (secureArea, createKeySettings) = secureAreaRepository.byConfiguration(
                    credentialConfiguration.secureAreaConfiguration,
                    credentialConfiguration.challenge
                )
                pendingCredentials = (0..<credentialCount).map {
                    MdocCredential.create(
                        document = document,
                        asReplacementForIdentifier = null,
                        domain = CREDENTIAL_DOMAIN_MDOC,
                        secureArea = secureArea,
                        docType = mdocConfiguration.docType,
                        createKeySettings = createKeySettings
                    )
                }
            } else if (sdJwtVcDocumentConfiguration != null) {
                credentialConfiguration =
                    credentialWorkflow.getCredentialConfiguration(CredentialFormat.SD_JWT_VC)
                val (secureArea, createKeySettings) = secureAreaRepository.byConfiguration(
                    credentialConfiguration.secureAreaConfiguration,
                    credentialConfiguration.challenge
                )
                pendingCredentials = (0..<credentialCount).map {
                    KeyBoundSdJwtVcCredential.create(
                        document = document,
                        asReplacementForIdentifier = null,
                        domain = CREDENTIAL_DOMAIN_SD_JWT_VC,
                        secureArea = secureArea,
                        vct = sdJwtVcDocumentConfiguration.vct,
                        createKeySettings = createKeySettings
                    )
                }
            } else {
                throw IllegalArgumentException("no supported configuration")
            }

            val credentialRequests = mutableListOf<CredentialRequest>()
            for (pendingCredential in pendingCredentials) {
                credentialRequests.add(
                    CredentialRequest(
                        (pendingCredential as SecureAreaBoundCredential).getAttestation()
                    )
                )
            }
            val keysAssertion = if (credentialConfiguration.keyAssertionRequired) {
                provisioningBackendProvider.makeDeviceAssertion { clientId ->
                    AssertionBindingKeys(
                        publicKeys = credentialRequests.map { request ->
                            request.secureAreaBoundKeyAttestation.publicKey
                        },
                        nonce = credentialConfiguration.challenge,
                        clientId = clientId,
                        keyStorage = listOf(),
                        userAuthentication = listOf(),
                        issuedAt = Clock.System.now()
                    )
                }
            } else {
                null
            }

            mutableState.emit(RequestingCredentials)

            val challenges = credentialWorkflow.sendCredentials(
                credentialRequests = credentialRequests,
                keysAssertion = keysAssertion
            )
            if (challenges.isNotEmpty()) {
                if (challenges.size != pendingCredentials.size) {
                    throw IllegalStateException("Unexpected number of possession challenges")
                }
                val possessionProofs = mutableListOf<KeyPossessionProof>()
                for ((credential, challenge) in pendingCredentials.zip(challenges)) {
                    credential as SecureAreaBoundCredential
                    val signature = credential.secureArea.sign(
                        alias = credential.alias,
                        dataToSign = challenge.messageToSign.toByteArray()
                    )
                    possessionProofs.add(KeyPossessionProof(ByteString(signature.toCoseEncoded())))
                }
                credentialWorkflow.sendPossessionProofs(possessionProofs)
            }
            issuingAuthority.completeRequestCredentials(credentialWorkflow)
        }

        val documentState = issuingAuthority.getState(issuerDocumentIdentifier)
        if (documentState.numAvailableCredentials > 0) {
            var issued = false
            for (credentialData in issuingAuthority.getCredentials(issuerDocumentIdentifier)) {
                val pendingCredential = if (credentialData.secureAreaBoundKey == null) {
                    // Keyless credential
                    KeylessSdJwtVcCredential.create(
                        document,
                        null,
                        CREDENTIAL_DOMAIN_SD_JWT_VC_KEYLESS,
                        documentConfiguration.sdJwtVcDocumentConfiguration!!.vct
                    )
                } else {
                    pendingCredentials.find {
                        val attestation = (it as SecureAreaBoundCredential).getAttestation()
                        attestation.publicKey == credentialData.secureAreaBoundKey
                    }
                }
                if (pendingCredential == null) {
                    Logger.w(TAG, "No pending Credential for pubkey ${credentialData.secureAreaBoundKey}")
                    continue
                }
                pendingCredential.certify(
                    credentialData.data,
                    credentialData.validFrom,
                    credentialData.validUntil
                )
                issued = true
            }
            if (issued) {
                document.metadata.markAsProvisioned()
                mutableState.emit(CredentialsIssued)
            }
        }

        return document
    }

    private suspend fun selectViableEvidenceRequests(
        evidenceRequests: List<EvidenceRequest>
    ): EvidenceRequested {
        val viableRequests = mutableListOf<EvidenceRequest>()
        val viableCredentials = mutableListOf<Credential>()
        for (request in evidenceRequests) {
            if (request is EvidenceRequestOpenid4Vp) {
                if (viableCredentials.isEmpty()) {
                    val credentials = selectCredentials(request.request)
                    if (credentials.isNotEmpty()) {
                        viableRequests.add(request)
                        viableCredentials.addAll(credentials)
                    }
                }
            } else {
                viableRequests.add(request)
            }
        }
        return EvidenceRequested(viableRequests.toList(), viableCredentials.toList())
    }

    private suspend fun selectCredentials(request: String): List<Credential> {
        val parts = request.split('.')
        val openid4vpRequest =
            Json.parseToJsonElement(parts[1].fromBase64Url().decodeToString()).jsonObject

        // Only examine a single credential for now; this all needs to be replaced with
        // proper DCQL processing anyway.
        val request0 = openid4vpRequest["dcql_query"]!!.jsonObject["credentials"]!!.jsonArray[0].jsonObject
        return when (request0["format"]?.jsonPrimitive?.content) {
            "mso_mdoc" -> {
                val doctype = request0["meta"]!!.jsonObject["doctype_value"]!!.jsonPrimitive.content
                selectMatchingCredentials(doctype, null)
            }
            "dc+sd-jwt" -> {
                val vct = request0["meta"]!!.jsonObject["vct_values"]!!.jsonArray[0].jsonPrimitive.content
                selectMatchingCredentials(null, vct)
            }
            else -> listOf()
        }
    }

    private suspend fun selectMatchingCredentials(
        docType: String?,
        vct: String?
    ): List<Credential> {
        val credentials = mutableListOf<Credential>()
        val now = Clock.System.now()
        for (documentId in documentStore.listDocuments()) {
            val document = documentStore.lookupDocument(documentId) ?: continue
            for (credential in document.getCertifiedCredentials()) {
                if (credential is MdocCredential && credential.validUntil > now) {
                    if (credential.docType == docType) {
                        credentials.add(credential)
                        break
                    }
                } else if (credential is SdJwtVcCredential) {
                    if (credential.vct == vct) {
                        credentials.add(credential)
                    }
                }
            }
        }
        return credentials.toList()
    }

    sealed class State

    data object Initial: State()
    data object Connected: State()
    data object Registration: State()
    data object SendingEvidence: State()
    data object ProcessingEvidence: State()
    data object ProofingComplete: State()
    data object RequestingCredentials: State()
    data object CredentialsIssued: State()

    data class EvidenceRequested(
        val evidenceRequests: List<EvidenceRequest>,
        val credentials: List<Credential>
    ): State()

    data class Error(
        val err: Throwable
    ): State()

    companion object {
        const val CREDENTIAL_DOMAIN_MDOC = "mdoc_user_auth"
        const val CREDENTIAL_DOMAIN_SD_JWT_VC = "sdjwt_user_auth"
        const val CREDENTIAL_DOMAIN_SD_JWT_VC_KEYLESS = "sdjwt_keyless"

        const val TAG = "ProvisioningModel"
    }
}