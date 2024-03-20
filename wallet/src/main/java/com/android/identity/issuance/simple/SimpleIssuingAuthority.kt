package com.android.identity.issuance.simple

import androidx.lifecycle.Observer
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.RawCbor
import com.android.identity.issuance.CredentialCondition
import com.android.identity.issuance.CredentialConfiguration
import com.android.identity.issuance.CredentialPresentationFormat
import com.android.identity.issuance.CredentialPresentationObject
import com.android.identity.issuance.CredentialPresentationRequest
import com.android.identity.issuance.CredentialState
import com.android.identity.issuance.IssuingAuthority
import com.android.identity.issuance.IssuingAuthorityConfiguration
import com.android.identity.issuance.ProofingFlow
import com.android.identity.issuance.RegisterCredentialFlow
import com.android.identity.issuance.RequestPresentationObjectsFlow
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.crypto.EcPublicKey
import com.android.identity.issuance.CredentialExtensions
import com.android.identity.issuance.CredentialExtensions.credentialConfiguration
import com.android.identity.issuance.UnknownCredentialException
import com.android.identity.storage.StorageEngine
import com.android.identity.util.Logger
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

    private fun emitOnStateChanged(credentialId: String) {
        for (observer in observers) {
            observer.onCredentialStateChanged(this, credentialId)
        }
    }

    open fun createNfcTunnelHandler(): SimpleIcaoNfcTunnelDriver {
        throw UnsupportedOperationException("Tunnel not supported")
    }

    abstract fun getProofingGraphRoot(): SimpleIssuingAuthorityProofingGraph.Node

    // Collected evidence responses are keyed by graph node ids.
    abstract fun checkEvidence(collectedEvidence: Map<String, EvidenceResponse>): Boolean
    abstract fun generateCredentialConfiguration(collectedEvidence: Map<String, EvidenceResponse>): CredentialConfiguration

    abstract fun createPresentationData(presentationFormat: CredentialPresentationFormat,
                                        credentialConfiguration: CredentialConfiguration,
                                        authenticationKey: EcPublicKey): ByteArray

    abstract fun developerModeRequestUpdate(currentConfiguration: CredentialConfiguration): CredentialConfiguration

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

    // The Credential as seen from the issuer's perspective
    private data class IssuerCredential(
        var proofingDeadlineMillis: Long,
        var state: CredentialCondition,
        var collectedEvidence: MutableMap<String, EvidenceResponse>,
        var credentialConfiguration: CredentialConfiguration?,
        var cpoRequests: MutableList<CpoRequest>
    ) {
        companion object {
            fun fromCbor(encodedData: ByteArray): IssuerCredential {
                val map = Cbor.decode(encodedData)
                val stateAsInt = map["state"].asNumber.toInt()
                val state = CredentialCondition.values().firstOrNull {it.ordinal == stateAsInt}
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

                val credentialConfiguration: CredentialConfiguration? =
                    map.getOrNull("credentialConfiguration")?.let {
                        CredentialConfiguration.fromCbor(it.asBstr)
                    }

                return IssuerCredential(
                    map["proofingDeadline"].asNumber,
                    state,
                    collectedEvidence,
                    credentialConfiguration,
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
            if (credentialConfiguration != null) {
                mapBuilder.put("credentialConfiguration", credentialConfiguration!!.toCbor())
            }
            return Cbor.encode(mapBuilder.end().build())
        }
    }

    private fun loadIssuerCredential(credentialId: String): IssuerCredential {
        val encoded = storageEngine.get(credentialId)
        if (encoded == null) {
            throw UnknownCredentialException("Unknown credentialId")
        }
        return IssuerCredential.fromCbor(encoded)
    }

    private fun saveIssuerCredential(credentialId: String, state: IssuerCredential) {
        storageEngine.put(credentialId, state.toCbor())
    }

    private fun deleteIssuerCredential(credentialId: String) {
        storageEngine.delete(credentialId)
    }

    override suspend fun credentialGetState(credentialId: String): CredentialState {
        val nowMillis = System.currentTimeMillis()

        val issuerCredential = loadIssuerCredential(credentialId)

        // Evaluate proofing, if deadline has passed...
        if (issuerCredential.state == CredentialCondition.PROOFING_PROCESSING) {
            if (nowMillis >= issuerCredential.proofingDeadlineMillis) {
                issuerCredential.state =
                    if (checkEvidence(issuerCredential.collectedEvidence)) {
                        CredentialCondition.CONFIGURATION_AVAILABLE
                    } else {
                        CredentialCondition.PROOFING_FAILED
                    }
                saveIssuerCredential(credentialId, issuerCredential)
            }
        }

        // Calculate pending/available depending on deadline
        var numPendingCPOs = 0
        var numAvailableCPOs = 0
        for (cpoRequest in issuerCredential.cpoRequests) {
            if (nowMillis >= cpoRequest.deadlineMillis) {
                numAvailableCPOs += 1
            } else {
                numPendingCPOs += 1
            }
        }
        return CredentialState(nowMillis, issuerCredential.state, numPendingCPOs, numAvailableCPOs)
    }


    override fun registerCredential(): RegisterCredentialFlow {
        val credentialId = "Credential_${System.currentTimeMillis()}_${Random.nextInt()}"
        return SimpleIssuingAuthorityRegisterCredentialFlow(this, credentialId)
    }

    override fun credentialProof(credentialId: String): ProofingFlow {
        return SimpleIssuingAuthorityProofingFlow(this, credentialId,
            getProofingGraphRoot(), this::createNfcTunnelHandler)
    }

    override suspend fun credentialGetConfiguration(credentialId: String): CredentialConfiguration {
        val issuerCredential = loadIssuerCredential(credentialId)
        check(issuerCredential.state == CredentialCondition.CONFIGURATION_AVAILABLE)
        issuerCredential.state = CredentialCondition.READY
        if (issuerCredential.credentialConfiguration == null) {
            issuerCredential.credentialConfiguration =
                generateCredentialConfiguration(issuerCredential.collectedEvidence)
        }
        saveIssuerCredential(credentialId, issuerCredential)
        return issuerCredential.credentialConfiguration!!
    }

    override fun credentialRequestPresentationObjects(credentialId: String): RequestPresentationObjectsFlow {
        return SimpleIssuingAuthorityRequestPresentationObjectsFlow(this, credentialId)
    }

    override suspend fun credentialGetPresentationObjects(credentialId: String): List<CredentialPresentationObject> {
        val nowMillis = System.currentTimeMillis()
        val validFromMillis = nowMillis
        val validUntilMillis = nowMillis + 30*24*3600*1000L
        val issuerCredential = loadIssuerCredential(credentialId)
        val availableCPOs = mutableListOf<CredentialPresentationObject>()
        val pendingCPOs = mutableListOf<CpoRequest>()
        for (cpoRequest in issuerCredential.cpoRequests) {
            if (nowMillis >= cpoRequest.deadlineMillis) {
                availableCPOs.add(CredentialPresentationObject(
                    cpoRequest.authenticationKey,
                    validFromMillis,
                    validUntilMillis,
                    cpoRequest.presentationData,
                ))
            } else {
                pendingCPOs.add(cpoRequest)
            }
        }
        issuerCredential.cpoRequests = pendingCPOs
        saveIssuerCredential(credentialId, issuerCredential)
        return availableCPOs
    }

    fun addCredentialId(credentialId: String) {
        // Initial state is PROOFING_REQUIRED
        saveIssuerCredential(
            credentialId,
            IssuerCredential(
                0L,
                CredentialCondition.PROOFING_REQUIRED,
                mutableMapOf(),           // collectedEvidence - initially empty
                null,  // no initial credential configuration
                mutableListOf()           // cpoRequests - initially empty
            )
        )
    }

    fun setProofingProcessing(credentialId: String) {
        val issuerCredential = loadIssuerCredential(credentialId)
        issuerCredential.state = CredentialCondition.PROOFING_PROCESSING

        val proofingTimeMillis = deadlineMillis
        issuerCredential.proofingDeadlineMillis = Clock.System.now().toEpochMilliseconds() + proofingTimeMillis

        saveIssuerCredential(credentialId, issuerCredential)

        if (proofingTimeMillis == 0L) {
            Logger.i(TAG, "Emitting onStateChanged on $credentialId for proofing")
            emitOnStateChanged(credentialId)
        } else {
            Timer().schedule(timerTask {
                Logger.i(TAG, "Emitting onStateChanged on $credentialId for proofing")
                emitOnStateChanged(credentialId)
            }, proofingTimeMillis)
        }
    }

    fun addCollectedEvidence(
          credentialId: String, nodeId: String, evidenceResponse: EvidenceResponse) {
        val issuerCredential = loadIssuerCredential(credentialId)
        issuerCredential.collectedEvidence[nodeId] = evidenceResponse
        saveIssuerCredential(credentialId, issuerCredential)
    }

    private fun hasCpoRequestForAuthenticationKey(
        issuerCredential: IssuerCredential,
        authenticationKey: EcPublicKey
    ) : Boolean {
        for (cpoRequest in issuerCredential.cpoRequests) {
            if (cpoRequest.authenticationKey.equals(authenticationKey)) {
                return true
            }
        }
        return false
    }

    fun addCpoRequests(
        credentialId: String,
        credentialPresentationRequests: List<CredentialPresentationRequest>
    ) {
        val nowMillis = System.currentTimeMillis()
        val deadlineTimeMillis = deadlineMillis

        val issuerCredential = loadIssuerCredential(credentialId)
        for (request in credentialPresentationRequests) {
            // Skip if we already have a request for the authentication key
            if (hasCpoRequestForAuthenticationKey(issuerCredential,
                    request.authenticationKeyAttestation.certificates.first().publicKey)) {
                Logger.d(TAG, "Already has cpoRequest for attestation with key " +
                        "${request.authenticationKeyAttestation.certificates.first().publicKey}")
                continue
            }
            val authenticationKey = request.authenticationKeyAttestation.certificates.first().publicKey
            val presentationData = createPresentationData(
                request.credentialPresentationFormat,
                issuerCredential.credentialConfiguration!!,
                authenticationKey
            )
            val cpoRequest = CpoRequest(
                authenticationKey,
                presentationData,
                nowMillis + deadlineTimeMillis,
            )
            issuerCredential.cpoRequests.add(cpoRequest)
        }
        saveIssuerCredential(credentialId, issuerCredential)

        if (deadlineTimeMillis == 0L) {
            Logger.i(TAG, "Emitting onStateChanged on $credentialId for CpoRequest")
            emitOnStateChanged(credentialId)
        } else {
            Timer().schedule(timerTask {
                Logger.i(TAG, "Emitting onStateChanged on $credentialId for CpoRequest")
                emitOnStateChanged(credentialId)
            }, deadlineTimeMillis)
        }
    }

    override suspend fun credentialDeveloperModeRequestUpdate(
        credentialId: String,
        requestRemoteDeletion: Boolean,
        notifyApplicationOfUpdate: Boolean
    ) {
        if (requestRemoteDeletion) {
            deleteIssuerCredential(credentialId)
            if (notifyApplicationOfUpdate) {
                emitOnStateChanged(credentialId)
            }
            return
        }

        val credential = loadIssuerCredential(credentialId)
        credential.credentialConfiguration = developerModeRequestUpdate(credential.credentialConfiguration!!)
        credential.state = CredentialCondition.CONFIGURATION_AVAILABLE
        credential.cpoRequests.clear()
        saveIssuerCredential(credentialId, credential)
        if (notifyApplicationOfUpdate) {
            emitOnStateChanged(credentialId)
        }
    }
}