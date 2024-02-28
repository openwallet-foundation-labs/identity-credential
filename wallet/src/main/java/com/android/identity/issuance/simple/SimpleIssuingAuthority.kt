package com.android.identity.issuance.simple

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
import com.android.identity.storage.StorageEngine
import com.android.identity.util.Logger
import java.lang.UnsupportedOperationException
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
            throw IllegalArgumentException("Unknown credentialId")
        }
        return IssuerCredential.fromCbor(encoded)
    }

    private fun saveIssuerCredential(credentialId: String, state: IssuerCredential) {
        storageEngine.put(credentialId, state.toCbor())
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
        val credentialConfiguration = generateCredentialConfiguration(issuerCredential.collectedEvidence)
        issuerCredential.credentialConfiguration = credentialConfiguration
        saveIssuerCredential(credentialId, issuerCredential)
        return credentialConfiguration
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
        // Initial state is PROOFING_REQUIRED and set it so proofing takes just one second...
        saveIssuerCredential(
            credentialId,
            IssuerCredential(
                System.currentTimeMillis() + 3*1000,
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
        saveIssuerCredential(credentialId, issuerCredential)
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
        val deadlineMillis = nowMillis + 1000L

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
                deadlineMillis,
            )
            issuerCredential.cpoRequests.add(cpoRequest)
        }
        saveIssuerCredential(credentialId, issuerCredential)
    }

}