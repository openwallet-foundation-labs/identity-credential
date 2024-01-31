package com.android.identity.issuance.simple

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.model.UnicodeString
import com.android.identity.internal.Util
import com.android.identity.issuance.RegisterCredentialFlow
import com.android.identity.issuance.CredentialConfiguration
import com.android.identity.issuance.CredentialState
import com.android.identity.issuance.CredentialPresentationObject
import com.android.identity.issuance.CredentialCondition
import com.android.identity.issuance.CredentialPresentationFormat
import com.android.identity.issuance.CredentialPresentationRequest
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.IssuingAuthority
import com.android.identity.issuance.IssuingAuthorityConfiguration
import com.android.identity.issuance.ProofingFlow
import com.android.identity.issuance.RequestPresentationObjectsFlow
import com.android.identity.issuance.evidence.EvidenceRequest
import com.android.identity.securearea.SecureArea
import com.android.identity.storage.StorageEngine
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.R
import java.io.ByteArrayOutputStream
import java.security.PublicKey
import kotlin.random.Random

/**
 * An simple implementation of an [IssuingAuthority].
 *
 * @param configuration the [IssuingAuthorityConfiguration] to use.
 * @param checkEvidence a function to check the evidence. Returns `true` if the user can get a
 *                      credential based on this, `false` otherwise.
 * @param generateCredentialConfiguration a function to create a [CredentialConfiguration] for
 *   an already proofed user.
 * @param storageEngine the [StorageEngine] to use for persisting issuer state.
 */
abstract class SimpleIssuingAuthority(
    private val storageEngine: StorageEngine,
) : IssuingAuthority {

    companion object {
        private const val TAG = "SimpleIssuingAuthority"
    }

    abstract override val configuration: IssuingAuthorityConfiguration

    abstract fun getProofingQuestions(): List<EvidenceRequest>
    abstract fun checkEvidence(collectedEvidence: List<EvidenceResponse>): Boolean
    abstract fun generateCredentialConfiguration(collectedEvidence: List<EvidenceResponse>): CredentialConfiguration

    abstract fun createPresentationData(presentationFormat: CredentialPresentationFormat,
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
                        Util.cborBuildCoseKey(authenticationKey, SecureArea.EC_CURVE_P256)
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
        var collectedEvidence: MutableList<EvidenceResponse>,
        var cpoRequests: MutableList<CpoRequest>
    ) {
        companion object {
            fun fromCbor(encodedData: ByteArray): IssuerCredential {
                val map = Util.cborDecode(encodedData)
                val stateAsInt = Util.cborMapExtractNumber(map, "state").toInt()
                val state = CredentialCondition.values().firstOrNull {it.ordinal == stateAsInt}
                    ?: throw IllegalArgumentException("Unknown state with value $stateAsInt")

                val collectedEvidence = mutableListOf<EvidenceResponse>()
                for (evidenceDataItem in Util.cborMapExtractArray(map, "collectedEvidence")) {
                    collectedEvidence.add(EvidenceResponse.fromCbor(Util.cborEncode(evidenceDataItem)))
                }

                val cpoRequests = mutableListOf<CpoRequest>()
                for (cpoRequestDataItem in Util.cborMapExtractArray(map, "cpoRequests")) {
                    cpoRequests.add(CpoRequest.fromCbor(Util.cborEncode(cpoRequestDataItem)))
                }
                return IssuerCredential(
                    Util.cborMapExtractNumber(map, "proofingDeadline"),
                    state,
                    collectedEvidence,
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
            val ceArrayBuilder = ceBuilder.addArray()
            for (evidence in collectedEvidence) {
                ceArrayBuilder.add(Util.cborDecode(evidence.toCbor()))
            }
            return Util.cborEncode(
                CborBuilder()
                    .addMap()
                    .put("proofingDeadline", proofingDeadlineMillis)
                    .put("state", state.ordinal.toLong())
                    .put(UnicodeString("collectedEvidence"), ceBuilder.build()[0])
                    .put(UnicodeString("cpoRequests"), cpoBuilder.build()[0])
                    .end()
                    .build().get(0))
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
        return SimpleIssuingAuthorityProofingFlow(this, credentialId, getProofingQuestions())
    }

    override suspend fun credentialGetConfiguration(credentialId: String): CredentialConfiguration {
        val issuerCredential = loadIssuerCredential(credentialId)
        check(issuerCredential.state == CredentialCondition.CONFIGURATION_AVAILABLE)
        issuerCredential.state = CredentialCondition.READY
        saveIssuerCredential(credentialId, issuerCredential)
        return generateCredentialConfiguration(issuerCredential.collectedEvidence)
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
                mutableListOf(),  // collectedEvidence - initially empty
                mutableListOf()   // cpoRequests - initially empty
            )
        )
    }

    fun setProofingProcessing(credentialId: String) {
        val issuerCredential = loadIssuerCredential(credentialId)
        issuerCredential.state = CredentialCondition.PROOFING_PROCESSING
        saveIssuerCredential(credentialId, issuerCredential)
    }

    fun addCollectedEvidence(credentialId: String, evidenceResponse: EvidenceResponse) {
        val issuerCredential = loadIssuerCredential(credentialId)
        issuerCredential.collectedEvidence.add(evidenceResponse)
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
            val presentationData = createPresentationData(request.credentialPresentationFormat, authenticationKey)
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