package com.android.identity.issuance.simple

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.RawCbor
import com.android.identity.crypto.EcPublicKey
import com.android.identity.issuance.CredentialConfiguration
import com.android.identity.issuance.CredentialData
import com.android.identity.issuance.CredentialFormat
import com.android.identity.issuance.CredentialRequest
import com.android.identity.issuance.DocumentCondition
import com.android.identity.issuance.DocumentConfiguration
import com.android.identity.issuance.DocumentState
import com.android.identity.issuance.IssuingAuthority
import com.android.identity.issuance.IssuingAuthorityConfiguration
import com.android.identity.issuance.ProofingFlow
import com.android.identity.issuance.RegistrationFlow
import com.android.identity.issuance.RegistrationResponse
import com.android.identity.issuance.RequestCredentialsFlow
import com.android.identity.issuance.UnknownDocumentException
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.fromDataItem
import com.android.identity.issuance.toDataItem
import com.android.identity.storage.StorageEngine
import com.android.identity.util.Logger
import com.android.identity_credential.mrtd.MrtdAccessData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.lang.UnsupportedOperationException
import java.util.Timer
import kotlin.concurrent.timerTask
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * An simple implementation of an [IssuingAuthority].
 *
 * @param storageEngine the [StorageEngine] to use for persisting issuer state.
 */
abstract class SimpleIssuingAuthority(
    private val storageEngine: StorageEngine,
) : IssuingAuthority {
    companion object {
        private const val TAG = "SimpleIssuingAuthority"
    }

    abstract override val configuration: IssuingAuthorityConfiguration

    // This can be changed to simulate proofing and requesting CPOs being slow.
    protected var delayForProofingAndIssuance: Duration = 0.seconds

    private val _eventFlow = MutableSharedFlow<Pair<IssuingAuthority, String>>()

    override val eventFlow
        get() = _eventFlow.asSharedFlow()

    private fun emitOnStateChanged(credentialId: String) {
        runBlocking {
            _eventFlow.emit(Pair(this@SimpleIssuingAuthority, credentialId))
        }
    }

    open fun createNfcTunnelHandler(): SimpleIcaoNfcTunnelDriver {
        throw UnsupportedOperationException("Tunnel not supported")
    }

    // If issuing authority has NFC card access data (such as PIN or CAN code -
    // probably from already-collected evidence) return it here. This is required
    // to avoid scanning passport/id card MRZ strip with camera.
    abstract fun getMrtdAccessData(collectedEvidence: Map<String, EvidenceResponse>): MrtdAccessData?

    abstract fun getProofingGraphRoot(registrationResponse: RegistrationResponse): SimpleIssuingAuthorityProofingGraph.Node

    // Collected evidence responses are keyed by graph node ids.
    abstract fun checkEvidence(collectedEvidence: Map<String, EvidenceResponse>): Boolean

    abstract fun generateDocumentConfiguration(collectedEvidence: Map<String, EvidenceResponse>): DocumentConfiguration

    abstract fun createCredentialConfiguration(collectedEvidence: MutableMap<String, EvidenceResponse>): CredentialConfiguration

    abstract fun createPresentationData(
        presentationFormat: CredentialFormat,
        documentConfiguration: DocumentConfiguration,
        authenticationKey: EcPublicKey,
    ): ByteArray

    abstract fun developerModeRequestUpdate(currentConfiguration: DocumentConfiguration): DocumentConfiguration

    private data class SimpleCredentialRequest(
        val authenticationKey: EcPublicKey,
        val format: CredentialFormat,
        val data: ByteArray,
        val deadline: Instant,
    ) {
        companion object {
            fun fromCbor(encodedData: ByteArray): SimpleCredentialRequest {
                val map = Cbor.decode(encodedData)
                return SimpleCredentialRequest(
                    map["authenticationKey"].asCoseKey.ecPublicKey,
                    CredentialFormat.valueOf(map["format"].asTstr),
                    map["data"].asBstr,
                    Instant.fromEpochMilliseconds(map["deadline"].asNumber),
                )
            }
        }

        fun toCbor(): ByteArray {
            return Cbor.encode(
                CborMap.builder()
                    .put("authenticationKey", authenticationKey.toCoseKey().toDataItem)
                    .put("format", format.name)
                    .put("data", data)
                    .put("deadline", deadline.toEpochMilliseconds())
                    .end()
                    .build(),
            )
        }
    }

    // The document as seen from the issuer's perspective
    private data class IssuerDocument(
        val registrationResponse: RegistrationResponse,
        var proofingDeadline: Instant,
        var state: DocumentCondition,
        var collectedEvidence: MutableMap<String, EvidenceResponse>,
        var documentConfiguration: DocumentConfiguration?,
        var simpleCredentialRequests: MutableList<SimpleCredentialRequest>,
    ) {
        companion object {
            fun fromCbor(encodedData: ByteArray): IssuerDocument {
                val map = Cbor.decode(encodedData)

                val registrationResponse = RegistrationResponse.fromDataItem(map["registrationResponse"])

                val stateAsInt = map["state"].asNumber.toInt()
                val state =
                    DocumentCondition.values().firstOrNull { it.ordinal == stateAsInt }
                        ?: throw IllegalArgumentException("Unknown state with value $stateAsInt")

                val collectedEvidence = mutableMapOf<String, EvidenceResponse>()
                val evidenceMap = map["collectedEvidence"].asMap
                for (evidenceId in evidenceMap.keys) {
                    collectedEvidence[evidenceId.asTstr] =
                        EvidenceResponse.fromCbor(Cbor.encode(evidenceMap[evidenceId]!!))
                }

                val credentialRequests = mutableListOf<SimpleCredentialRequest>()
                for (credentialRequestDataItem in map["credentialRequests"].asArray) {
                    credentialRequests.add(
                        SimpleCredentialRequest.fromCbor(Cbor.encode(credentialRequestDataItem)),
                    )
                }

                val documentConfiguration: DocumentConfiguration? =
                    map.getOrNull("documentConfiguration")?.let {
                        DocumentConfiguration.fromCbor(it.asBstr)
                    }

                return IssuerDocument(
                    registrationResponse,
                    Instant.fromEpochMilliseconds(map["proofingDeadline"].asNumber),
                    state,
                    collectedEvidence,
                    documentConfiguration,
                    credentialRequests,
                )
            }
        }

        fun toCbor(): ByteArray {
            val credentialRequestsBuilder = CborArray.builder()
            simpleCredentialRequests.forEach { cpoRequest ->
                credentialRequestsBuilder.add(RawCbor(cpoRequest.toCbor()))
            }
            val ceMapBuilder = CborMap.builder()
            collectedEvidence.forEach { evidence ->
                ceMapBuilder.put(evidence.key, RawCbor(evidence.value.toCbor()))
            }
            val mapBuilder =
                CborMap.builder()
                    .put("registrationResponse", registrationResponse.toDataItem)
                    .put("proofingDeadline", proofingDeadline.toEpochMilliseconds())
                    .put("state", state.ordinal.toLong())
                    .put("collectedEvidence", ceMapBuilder.end().build())
                    .put("credentialRequests", credentialRequestsBuilder.end().build())
            if (documentConfiguration != null) {
                mapBuilder.put("documentConfiguration", documentConfiguration!!.toCbor())
            }
            return Cbor.encode(mapBuilder.end().build())
        }
    }

    private fun loadIssuerDocument(documentId: String): IssuerDocument {
        val encoded = storageEngine.get(documentId)
        if (encoded == null) {
            throw UnknownDocumentException("Unknown documentId")
        }
        return IssuerDocument.fromCbor(encoded)
    }

    private fun saveIssuerDocument(
        documentId: String,
        state: IssuerDocument,
    ) {
        storageEngine.put(documentId, state.toCbor())
    }

    private fun deleteIssuerDocument(documentId: String) {
        storageEngine.delete(documentId)
    }

    override suspend fun getState(documentId: String): DocumentState {
        val now = Clock.System.now()

        val issuerDocument = loadIssuerDocument(documentId)

        // Evaluate proofing, if deadline has passed...
        if (issuerDocument.state == DocumentCondition.PROOFING_PROCESSING) {
            if (now >= issuerDocument.proofingDeadline) {
                issuerDocument.state =
                    if (checkEvidence(issuerDocument.collectedEvidence)) {
                        DocumentCondition.CONFIGURATION_AVAILABLE
                    } else {
                        DocumentCondition.PROOFING_FAILED
                    }
                saveIssuerDocument(documentId, issuerDocument)
            }
        }

        // Calculate pending/available depending on deadline
        var numPendingCredentialRequests = 0
        var numAvailableCredentialRequests = 0
        for (cpoRequest in issuerDocument.simpleCredentialRequests) {
            if (now >= cpoRequest.deadline) {
                numAvailableCredentialRequests += 1
            } else {
                numPendingCredentialRequests += 1
            }
        }
        return DocumentState(
            now,
            issuerDocument.state,
            numPendingCredentialRequests,
            numAvailableCredentialRequests,
        )
    }

    override fun register(): RegistrationFlow {
        val now = Clock.System.now()
        val documentId = "Document_${now}_${Random.nextInt()}"
        return SimpleIssuingAuthorityRegistrationFlow(this, documentId)
    }

    override fun proof(documentId: String): ProofingFlow {
        val issuerDocument = loadIssuerDocument(documentId)
        return SimpleIssuingAuthorityProofingFlow(
            this,
            documentId,
            getProofingGraphRoot(issuerDocument.registrationResponse),
            this::createNfcTunnelHandler,
        )
    }

    override suspend fun getDocumentConfiguration(documentId: String): DocumentConfiguration {
        val issuerDocument = loadIssuerDocument(documentId)
        check(issuerDocument.state == DocumentCondition.CONFIGURATION_AVAILABLE)
        issuerDocument.state = DocumentCondition.READY
        if (issuerDocument.documentConfiguration == null) {
            issuerDocument.documentConfiguration =
                generateDocumentConfiguration(issuerDocument.collectedEvidence)
        }
        saveIssuerDocument(documentId, issuerDocument)
        return issuerDocument.documentConfiguration!!
    }

    override fun requestCredentials(documentId: String): RequestCredentialsFlow {
        val issuerDocument = loadIssuerDocument(documentId)
        check(issuerDocument.state == DocumentCondition.READY)

        val credentialConfiguration = createCredentialConfiguration(issuerDocument.collectedEvidence)
        return SimpleIssuingAuthorityRequestCredentialsFlow(
            this,
            documentId,
            credentialConfiguration,
        )
    }

    override suspend fun getCredentials(documentId: String): List<CredentialData> {
        val now = Clock.System.now()
        val validFrom = now
        val validUntil = now + 30.days
        val issuerDocument = loadIssuerDocument(documentId)
        val availableCPOs = mutableListOf<CredentialData>()
        val pendingCPOs = mutableListOf<SimpleCredentialRequest>()
        for (cpoRequest in issuerDocument.simpleCredentialRequests) {
            if (now >= cpoRequest.deadline) {
                availableCPOs.add(
                    CredentialData(
                        cpoRequest.authenticationKey,
                        validFrom,
                        validUntil,
                        cpoRequest.format,
                        cpoRequest.data,
                    ),
                )
            } else {
                pendingCPOs.add(cpoRequest)
            }
        }
        issuerDocument.simpleCredentialRequests = pendingCPOs
        saveIssuerDocument(documentId, issuerDocument)
        return availableCPOs
    }

    fun addDocumentId(
        documentId: String,
        response: RegistrationResponse,
    ) {
        // Initial state is PROOFING_REQUIRED
        saveIssuerDocument(
            documentId,
            IssuerDocument(
                response,
                Instant.fromEpochMilliseconds(0),
                DocumentCondition.PROOFING_REQUIRED,
                mutableMapOf(), // collectedEvidence - initially empty
                null, // no initial document configuration
                mutableListOf(), // cpoRequests - initially empty
            ),
        )
    }

    fun setProofingProcessing(documentId: String) {
        val issuerDocument = loadIssuerDocument(documentId)
        issuerDocument.state = DocumentCondition.PROOFING_PROCESSING

        issuerDocument.proofingDeadline = Clock.System.now() + delayForProofingAndIssuance

        saveIssuerDocument(documentId, issuerDocument)

        if (delayForProofingAndIssuance == 0.seconds) {
            Logger.i(TAG, "Emitting onStateChanged on $documentId for proofing")
            emitOnStateChanged(documentId)
        } else {
            Timer().schedule(
                timerTask {
                    Logger.i(TAG, "Emitting onStateChanged on $documentId for proofing")
                    emitOnStateChanged(documentId)
                },
                delayForProofingAndIssuance.inWholeMilliseconds,
            )
        }
    }

    fun addCollectedEvidence(
        documentId: String,
        nodeId: String,
        evidenceResponse: EvidenceResponse,
    ) {
        val issuerDocument = loadIssuerDocument(documentId)
        issuerDocument.collectedEvidence[nodeId] = evidenceResponse
        saveIssuerDocument(documentId, issuerDocument)
    }

    fun getMrtdAccessData(documentId: String): MrtdAccessData? {
        val issuerDocument = loadIssuerDocument(documentId)
        return getMrtdAccessData(issuerDocument.collectedEvidence)
    }

    private fun hasCpoRequestForAuthenticationKey(
        issuerDocument: IssuerDocument,
        authenticationKey: EcPublicKey,
    ): Boolean {
        for (cpoRequest in issuerDocument.simpleCredentialRequests) {
            if (cpoRequest.authenticationKey.equals(authenticationKey)) {
                return true
            }
        }
        return false
    }

    fun addCpoRequests(
        documentId: String,
        credentialRequests: List<CredentialRequest>,
    ) {
        val now = Clock.System.now()

        val issuerDocument = loadIssuerDocument(documentId)
        for (request in credentialRequests) {
            // Skip if we already have a request for the authentication key
            if (hasCpoRequestForAuthenticationKey(
                    issuerDocument,
                    request.secureAreaBoundKeyAttestation.certificates.first().publicKey,
                )
            ) {
                Logger.d(
                    TAG,
                    "Already has cpoRequest for attestation with key " +
                        "${request.secureAreaBoundKeyAttestation.certificates.first().publicKey}",
                )
                continue
            }
            val authenticationKey = request.secureAreaBoundKeyAttestation.certificates.first().publicKey
            val presentationData =
                createPresentationData(
                    request.format,
                    issuerDocument.documentConfiguration!!,
                    authenticationKey,
                )
            val simpleCredentialRequest =
                SimpleCredentialRequest(
                    authenticationKey,
                    request.format,
                    presentationData,
                    now + delayForProofingAndIssuance,
                )
            issuerDocument.simpleCredentialRequests.add(simpleCredentialRequest)
        }
        saveIssuerDocument(documentId, issuerDocument)

        if (delayForProofingAndIssuance == 0.seconds) {
            Logger.i(TAG, "Emitting onStateChanged on $documentId for CpoRequest")
            emitOnStateChanged(documentId)
        } else {
            Timer().schedule(
                timerTask {
                    Logger.i(TAG, "Emitting onStateChanged on $documentId for CpoRequest")
                    emitOnStateChanged(documentId)
                },
                delayForProofingAndIssuance.inWholeMilliseconds,
            )
        }
    }

    override suspend fun developerModeRequestUpdate(
        documentId: String,
        requestRemoteDeletion: Boolean,
        notifyApplicationOfUpdate: Boolean,
    ) {
        if (requestRemoteDeletion) {
            deleteIssuerDocument(documentId)
            if (notifyApplicationOfUpdate) {
                emitOnStateChanged(documentId)
            }
            return
        }

        val document = loadIssuerDocument(documentId)
        document.documentConfiguration = developerModeRequestUpdate(document.documentConfiguration!!)
        document.state = DocumentCondition.CONFIGURATION_AVAILABLE
        document.simpleCredentialRequests.clear()
        saveIssuerDocument(documentId, document)
        if (notifyApplicationOfUpdate) {
            emitOnStateChanged(documentId)
        }
    }
}
