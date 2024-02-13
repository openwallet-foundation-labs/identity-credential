package com.android.identity.issuance.simple

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.model.UnicodeString
import com.android.identity.internal.Util
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
import com.android.identity.securearea.EcCurve
import com.android.identity.storage.StorageEngine
import com.android.identity.util.Logger
import java.security.PublicKey
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

    abstract fun getProofingGraphRoot(): SimpleIssuingAuthorityProofingGraph.Node

    // Collected evidence responses are keyed by graph node ids.
    abstract fun checkEvidence(collectedEvidence: Map<String, EvidenceResponse>): Boolean
    abstract fun generateCredentialConfiguration(collectedEvidence: Map<String, EvidenceResponse>): CredentialConfiguration

    abstract fun createPresentationData(presentationFormat: CredentialPresentationFormat,
                                        credentialConfiguration: CredentialConfiguration,
                                        authenticationKey: PublicKey): ByteArray

    private data class CpoRequest(
        val authenticationKey: PublicKey,
        val presentationData: ByteArray,
        val deadlineMillis: Long,
    ) {

        companion object {
            fun fromCbor(encodedData: ByteArray): CpoRequest {
                val map = Util.cborDecode(encodedData)
                return CpoRequest(
                    Util.coseKeyDecode(Util.cborMapExtract(map, "authenticationKey")),
                    Util.cborMapExtractByteString(map, "presentationData"),
                    Util.cborMapExtractNumber(map, "deadline")
                )
            }
        }

        fun toCbor(): ByteArray {
            return Util.cborEncode(
                CborBuilder()
                    .addMap()
                    .put(
                        UnicodeString("authenticationKey"),
                        Util.cborBuildCoseKey(authenticationKey, EcCurve.P256)
                    )
                    .put("presentationData", presentationData)
                    .put("deadline", deadlineMillis)
                    .end()
                    .build().get(0))
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
                val map = Util.cborDecode(encodedData)
                val stateAsInt = Util.cborMapExtractNumber(map, "state").toInt()
                val state = CredentialCondition.values().firstOrNull {it.ordinal == stateAsInt}
                    ?: throw IllegalArgumentException("Unknown state with value $stateAsInt")

                val collectedEvidence = mutableMapOf<String, EvidenceResponse>()
                val evidenceMap = Util.cborMapExtractMap(map, "collectedEvidence")
                        as co.nstant.`in`.cbor.model.Map
                for (evidenceId in evidenceMap.keys) {
                    val evidenceDataItem = evidenceMap[evidenceId]
                    collectedEvidence[Util.checkedStringValue(evidenceId)] =
                        EvidenceResponse.fromCbor(Util.cborEncode(evidenceDataItem))
                }

                val cpoRequests = mutableListOf<CpoRequest>()
                for (cpoRequestDataItem in Util.cborMapExtractArray(map, "cpoRequests")) {
                    cpoRequests.add(CpoRequest.fromCbor(Util.cborEncode(cpoRequestDataItem)))
                }

                var credentialConfiguration: CredentialConfiguration? = null
                if (Util.cborMapHasKey(map, "credentialConfiguration")) {
                    credentialConfiguration = CredentialConfiguration.fromCbor(
                        Util.cborMapExtractByteString(map, "credentialConfiguration")
                    )
                }

                return IssuerCredential(
                    Util.cborMapExtractNumber(map, "proofingDeadline"),
                    state,
                    collectedEvidence,
                    credentialConfiguration,
                    cpoRequests,
                )
            }
        }
        fun toCbor(): ByteArray {
            val cpoBuilder = CborBuilder()
            val cpoArrayBuilder = cpoBuilder.addArray()
            for (cpoRequest in cpoRequests) {
                cpoArrayBuilder.add(Util.cborDecode(cpoRequest.toCbor()))
            }
            val ceBuilder = CborBuilder()
            val ceMapBuilder = ceBuilder.addMap()
            for (evidence in collectedEvidence) {
                ceMapBuilder.put(UnicodeString(evidence.key),
                    Util.cborDecode(evidence.value.toCbor()))
            }

            val builder = CborBuilder()
            val mapBuilder = builder.addMap()
            mapBuilder
                .put("proofingDeadline", proofingDeadlineMillis)
                .put("state", state.ordinal.toLong())
                .put(UnicodeString("collectedEvidence"), ceBuilder.build()[0])
                .put(UnicodeString("cpoRequests"), cpoBuilder.build()[0])
            if (credentialConfiguration != null) {
                mapBuilder.put("credentialConfiguration", credentialConfiguration!!.toCbor())
            }
            return Util.cborEncode(builder.build()[0])
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
        return SimpleIssuingAuthorityProofingFlow(this, credentialId, getProofingGraphRoot())
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
        issuerCredential.collectedEvidence.put(nodeId, evidenceResponse)
        saveIssuerCredential(credentialId, issuerCredential)
    }

    private fun hasCpoRequestForAuthenticationKey(
        issuerCredential: IssuerCredential,
        authenticationKey: PublicKey
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
            if (hasCpoRequestForAuthenticationKey(issuerCredential, request.authenticationKeyAttestation[0].publicKey)) {
                Logger.d(TAG, "Already has cpoRequest for attestation with key " +
                        "${request.authenticationKeyAttestation[0].publicKey}")
                continue
            }
            val authenticationKey = request.authenticationKeyAttestation[0].publicKey
            val presentationData = createPresentationData(
                request.credentialPresentationFormat,
                issuerCredential.credentialConfiguration!!,
                authenticationKey
            )
            val request = CpoRequest(
                authenticationKey,
                presentationData,
                deadlineMillis,
            )
            issuerCredential.cpoRequests.add(request)
        }
        saveIssuerCredential(credentialId, issuerCredential)
    }

}