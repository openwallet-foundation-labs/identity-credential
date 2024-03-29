package com.android.identity.issuance.simple

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.RawCbor
import com.android.identity.issuance.DocumentCondition
import com.android.identity.issuance.DocumentConfiguration
import com.android.identity.issuance.DocumentPresentationFormat
import com.android.identity.issuance.DocumentPresentationObject
import com.android.identity.issuance.DocumentPresentationRequest
import com.android.identity.issuance.DocumentState
import com.android.identity.issuance.IssuingAuthority
import com.android.identity.issuance.IssuingAuthorityConfiguration
import com.android.identity.issuance.ProofingFlow
import com.android.identity.issuance.RegisterDocumentFlow
import com.android.identity.issuance.RequestPresentationObjectsFlow
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.crypto.EcPublicKey
import com.android.identity.issuance.UnknownDocumentException
import com.android.identity.storage.StorageEngine
import com.android.identity.util.Logger
import com.android.identity_credential.mrtd.MrtdAccessData
import kotlinx.datetime.Clock
import java.lang.UnsupportedOperationException
import java.util.Timer
import kotlin.concurrent.timerTask
import kotlin.random.Random

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

    private val observers = mutableListOf<IssuingAuthority.Observer>()

    // This can be changed to simulate proofing and requesting CPOs being slow.
    protected var deadlineMillis: Long = 0L

    override fun startObserving(observer: IssuingAuthority.Observer) {
        this.observers.add(observer)
    }

    override fun stopObserving(observer: IssuingAuthority.Observer) {
        this.observers.remove(observer)
    }

    private fun emitOnStateChanged(documentId: String) {
        for (observer in observers) {
            observer.onDocumentStateChanged(this, documentId)
        }
    }

    open fun createNfcTunnelHandler(): SimpleIcaoNfcTunnelDriver {
        throw UnsupportedOperationException("Tunnel not supported")
    }

    // If issuing authority has NFC card access data (such as PIN or CAN code -
    // probably from already-collected evidence) return it here. This is required
    // to avoid scanning passport/id card MRZ strip with camera.
    abstract fun getMrtdAccessData(
        collectedEvidence: Map<String, EvidenceResponse>): MrtdAccessData?

    abstract fun getProofingGraphRoot(): SimpleIssuingAuthorityProofingGraph.Node

    // Collected evidence responses are keyed by graph node ids.
    abstract fun checkEvidence(collectedEvidence: Map<String, EvidenceResponse>): Boolean
    abstract fun generateDocumentConfiguration(collectedEvidence: Map<String, EvidenceResponse>): DocumentConfiguration

    abstract fun createPresentationData(presentationFormat: DocumentPresentationFormat,
                                        documentConfiguration: DocumentConfiguration,
                                        authenticationKey: EcPublicKey): ByteArray

    abstract fun developerModeRequestUpdate(currentConfiguration: DocumentConfiguration): DocumentConfiguration

    private data class CpoRequest(
        val authenticationKey: EcPublicKey,
        val presentationData: ByteArray,
        val deadlineMillis: Long,
    ) {

        companion object {
            fun fromCbor(encodedData: ByteArray): CpoRequest {
                val map = Cbor.decode(encodedData)
                return CpoRequest(
                    map["authenticationKey"].asCoseKey.ecPublicKey,
                    map["presentationData"].asBstr,
                    map["deadline"].asNumber
                )
            }
        }

        fun toCbor(): ByteArray {
            return Cbor.encode(
                CborMap.builder()
                    .put("authenticationKey", authenticationKey.toCoseKey().toDataItem)
                    .put("presentationData", presentationData)
                    .put("deadline", deadlineMillis)
                    .end()
                    .build())
        }
    }

    // The document as seen from the issuer's perspective
    private data class IssuerDocument(
        var proofingDeadlineMillis: Long,
        var state: DocumentCondition,
        var collectedEvidence: MutableMap<String, EvidenceResponse>,
        var documentConfiguration: DocumentConfiguration?,
        var cpoRequests: MutableList<CpoRequest>
    ) {
        companion object {
            fun fromCbor(encodedData: ByteArray): IssuerDocument {
                val map = Cbor.decode(encodedData)
                val stateAsInt = map["state"].asNumber.toInt()
                val state = DocumentCondition.values().firstOrNull {it.ordinal == stateAsInt}
                    ?: throw IllegalArgumentException("Unknown state with value $stateAsInt")

                val collectedEvidence = mutableMapOf<String, EvidenceResponse>()
                val evidenceMap = map["collectedEvidence"].asMap
                for (evidenceId in evidenceMap.keys) {
                    collectedEvidence[evidenceId.asTstr] =
                        EvidenceResponse.fromCbor(Cbor.encode(evidenceMap[evidenceId]!!))
                }

                val cpoRequests = mutableListOf<CpoRequest>()
                for (cpoRequestDataItem in map["cpoRequests"].asArray) {
                    cpoRequests.add(CpoRequest.fromCbor(Cbor.encode(cpoRequestDataItem)))
                }

                val documentConfiguration: DocumentConfiguration? =
                    map.getOrNull("documentConfiguration")?.let {
                        DocumentConfiguration.fromCbor(it.asBstr)
                    }

                return IssuerDocument(
                    map["proofingDeadline"].asNumber,
                    state,
                    collectedEvidence,
                    documentConfiguration,
                    cpoRequests,
                )
            }
        }
        fun toCbor(): ByteArray {
            val cpoArrayBuilder = CborArray.builder()
            cpoRequests.forEach() { cpoRequest ->
                cpoArrayBuilder.add(RawCbor(cpoRequest.toCbor()))
            }
            val ceMapBuilder = CborMap.builder()
            collectedEvidence.forEach() { evidence ->
                ceMapBuilder.put(evidence.key, RawCbor(evidence.value.toCbor()))
            }
            val mapBuilder = CborMap.builder()
                .put("proofingDeadline", proofingDeadlineMillis)
                .put("state", state.ordinal.toLong())
                .put("collectedEvidence", ceMapBuilder.end().build())
                .put("cpoRequests", cpoArrayBuilder.end().build())
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

    private fun saveIssuerDocument(documentId: String, state: IssuerDocument) {
        storageEngine.put(documentId, state.toCbor())
    }

    private fun deleteIssuerDocument(documentId: String) {
        storageEngine.delete(documentId)
    }

    override suspend fun documentGetState(documentId: String): DocumentState {
        val nowMillis = System.currentTimeMillis()

        val issuerDocument = loadIssuerDocument(documentId)

        // Evaluate proofing, if deadline has passed...
        if (issuerDocument.state == DocumentCondition.PROOFING_PROCESSING) {
            if (nowMillis >= issuerDocument.proofingDeadlineMillis) {
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
        var numPendingCPOs = 0
        var numAvailableCPOs = 0
        for (cpoRequest in issuerDocument.cpoRequests) {
            if (nowMillis >= cpoRequest.deadlineMillis) {
                numAvailableCPOs += 1
            } else {
                numPendingCPOs += 1
            }
        }
        return DocumentState(nowMillis, issuerDocument.state, numPendingCPOs, numAvailableCPOs)
    }


    override fun registerDocument(): RegisterDocumentFlow {
        val documentId = "Document_${System.currentTimeMillis()}_${Random.nextInt()}"
        return SimpleIssuingAuthorityRegisterDocumentFlow(this, documentId)
    }

    override fun documentProof(documentId: String): ProofingFlow {
        return SimpleIssuingAuthorityProofingFlow(this, documentId,
            getProofingGraphRoot(), this::createNfcTunnelHandler)
    }

    override suspend fun documentGetConfiguration(documentId: String): DocumentConfiguration {
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

    override fun documentRequestPresentationObjects(documentId: String): RequestPresentationObjectsFlow {
        return SimpleIssuingAuthorityRequestPresentationObjectsFlow(this, documentId)
    }

    override suspend fun documentGetPresentationObjects(documentId: String): List<DocumentPresentationObject> {
        val nowMillis = System.currentTimeMillis()
        val validFromMillis = nowMillis
        val validUntilMillis = nowMillis + 30*24*3600*1000L
        val issuerDocument = loadIssuerDocument(documentId)
        val availableCPOs = mutableListOf<DocumentPresentationObject>()
        val pendingCPOs = mutableListOf<CpoRequest>()
        for (cpoRequest in issuerDocument.cpoRequests) {
            if (nowMillis >= cpoRequest.deadlineMillis) {
                availableCPOs.add(DocumentPresentationObject(
                    cpoRequest.authenticationKey,
                    validFromMillis,
                    validUntilMillis,
                    cpoRequest.presentationData,
                ))
            } else {
                pendingCPOs.add(cpoRequest)
            }
        }
        issuerDocument.cpoRequests = pendingCPOs
        saveIssuerDocument(documentId, issuerDocument)
        return availableCPOs
    }

    fun addDocumentId(documentId: String) {
        // Initial state is PROOFING_REQUIRED
        saveIssuerDocument(
            documentId,
            IssuerDocument(
                0L,
                DocumentCondition.PROOFING_REQUIRED,
                mutableMapOf(),           // collectedEvidence - initially empty
                null,  // no initial document configuration
                mutableListOf()           // cpoRequests - initially empty
            )
        )
    }

    fun setProofingProcessing(documentId: String) {
        val issuerDocument = loadIssuerDocument(documentId)
        issuerDocument.state = DocumentCondition.PROOFING_PROCESSING

        val proofingTimeMillis = deadlineMillis
        issuerDocument.proofingDeadlineMillis = Clock.System.now().toEpochMilliseconds() + proofingTimeMillis

        saveIssuerDocument(documentId, issuerDocument)

        if (proofingTimeMillis == 0L) {
            Logger.i(TAG, "Emitting onStateChanged on $documentId for proofing")
            emitOnStateChanged(documentId)
        } else {
            Timer().schedule(timerTask {
                Logger.i(TAG, "Emitting onStateChanged on $documentId for proofing")
                emitOnStateChanged(documentId)
            }, proofingTimeMillis)
        }
    }

    fun addCollectedEvidence(
        documentId: String, nodeId: String, evidenceResponse: EvidenceResponse) {
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
        authenticationKey: EcPublicKey
    ) : Boolean {
        for (cpoRequest in issuerDocument.cpoRequests) {
            if (cpoRequest.authenticationKey.equals(authenticationKey)) {
                return true
            }
        }
        return false
    }

    fun addCpoRequests(
        documentId: String,
        documentPresentationRequests: List<DocumentPresentationRequest>
    ) {
        val nowMillis = System.currentTimeMillis()
        val deadlineTimeMillis = deadlineMillis

        val issuerDocument = loadIssuerDocument(documentId)
        for (request in documentPresentationRequests) {
            // Skip if we already have a request for the authentication key
            if (hasCpoRequestForAuthenticationKey(issuerDocument,
                    request.authenticationKeyAttestation.certificates.first().publicKey)) {
                Logger.d(TAG, "Already has cpoRequest for attestation with key " +
                        "${request.authenticationKeyAttestation.certificates.first().publicKey}")
                continue
            }
            val authenticationKey = request.authenticationKeyAttestation.certificates.first().publicKey
            val presentationData = createPresentationData(
                request.documentPresentationFormat,
                issuerDocument.documentConfiguration!!,
                authenticationKey
            )
            val cpoRequest = CpoRequest(
                authenticationKey,
                presentationData,
                nowMillis + deadlineTimeMillis,
            )
            issuerDocument.cpoRequests.add(cpoRequest)
        }
        saveIssuerDocument(documentId, issuerDocument)

        if (deadlineTimeMillis == 0L) {
            Logger.i(TAG, "Emitting onStateChanged on $documentId for CpoRequest")
            emitOnStateChanged(documentId)
        } else {
            Timer().schedule(timerTask {
                Logger.i(TAG, "Emitting onStateChanged on $documentId for CpoRequest")
                emitOnStateChanged(documentId)
            }, deadlineTimeMillis)
        }
    }

    override suspend fun documentDeveloperModeRequestUpdate(
        documentId: String,
        requestRemoteDeletion: Boolean,
        notifyApplicationOfUpdate: Boolean
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
        document.cpoRequests.clear()
        saveIssuerDocument(documentId, document)
        if (notifyApplicationOfUpdate) {
            emitOnStateChanged(documentId)
        }
    }
}