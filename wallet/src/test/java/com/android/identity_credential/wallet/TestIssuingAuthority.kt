package com.android.identity_credential.wallet

import com.android.identity.issuance.CredentialConfiguration
import com.android.identity.issuance.CredentialPresentationFormat
import com.android.identity.issuance.IssuingAuthorityConfiguration
import com.android.identity.issuance.evidence.EvidenceRequest
import com.android.identity.issuance.evidence.EvidenceRequestMessage
import com.android.identity.issuance.evidence.EvidenceRequestQuestionMultipleChoice
import com.android.identity.issuance.evidence.EvidenceRequestQuestionString
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseQuestionString
import com.android.identity.issuance.evidence.EvidenceType
import com.android.identity.issuance.simple.SimpleIssuingAuthority
import com.android.identity.storage.EphemeralStorageEngine
import java.security.PublicKey

class TestIssuingAuthority: SimpleIssuingAuthority(EphemeralStorageEngine()) {
    companion object {
        private const val TAG = "TestIssuingAuthority"
    }

    override lateinit var configuration: IssuingAuthorityConfiguration

    init {
        configuration = IssuingAuthorityConfiguration(
            "mDL_SelfSigned",
            "Test IA",
            byteArrayOf(1, 2, 3),
            setOf(CredentialPresentationFormat.MDOC_MSO),
            CredentialConfiguration(
                "mDL for Test IA (proofing pending)",
                byteArrayOf(1, 2, 3),
            )
        )
    }

    override fun createPresentationData(presentationFormat: CredentialPresentationFormat,
                                       authenticationKey: PublicKey
    ): ByteArray {
        return byteArrayOf(1, 2, 3)
    }

    override fun getProofingQuestions(): List<EvidenceRequest> {
        return listOf(
            EvidenceRequestMessage(
                "Here's a long string with TOS",
                "Accept",
                "Do Not Accept",
            ),
            EvidenceRequestQuestionString(
                "What first name should be used for the mDL?",
                "Erika",
                "Continue",
            ),
            EvidenceRequestQuestionMultipleChoice(
                "Select the card art for the credential",
                listOf("Green", "Blue", "Red"),
                "Continue",
            ),
            EvidenceRequestMessage(
                "Your application is about to be sent the ID issuer for " +
                        "verification. You will get notified when the " +
                        "application is approved.",
                "Continue",
                null,
            )
        )
    }

    override fun checkEvidence(collectedEvidence: List<EvidenceResponse>): Boolean {
        return true
    }

    override fun generateCredentialConfiguration(collectedEvidence: List<EvidenceResponse>): CredentialConfiguration {
        val firstName = (collectedEvidence.find { r -> r.evidenceType == EvidenceType.QUESTION_STRING}
                as EvidenceResponseQuestionString).answer
        return CredentialConfiguration(
            "${firstName}'s Driving License",
            byteArrayOf(1, 2, 3),
        )
    }

}