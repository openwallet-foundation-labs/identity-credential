package com.android.identity_credential.wallet

import com.android.identity.credential.NameSpacedData
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
import com.android.identity.issuance.simple.SimpleIssuingAuthorityProofingGraph
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
                NameSpacedData.Builder().build()
            )
        )
    }

    override fun createPresentationData(presentationFormat: CredentialPresentationFormat,
                                        credentialConfiguration: CredentialConfiguration,
                                        authenticationKey: PublicKey
    ): ByteArray {
        return byteArrayOf(1, 2, 3)
    }

    override fun getProofingGraphRoot(): SimpleIssuingAuthorityProofingGraph.Node {
        return SimpleIssuingAuthorityProofingGraph()
            .add(
                SimpleIssuingAuthorityProofingGraph.SimpleNode(
                    "tos",
                    EvidenceRequestMessage(
                        "Here's a long string with TOS",
                        "Accept",
                        "Do Not Accept",
                    )
                )
            )
            .add(
                SimpleIssuingAuthorityProofingGraph.SimpleNode(
                    "name",
                    EvidenceRequestQuestionString(
                        "What first name should be used for the mDL?",
                        "Erika",
                        "Continue",
                    )
                )
            )
            .add(
                SimpleIssuingAuthorityProofingGraph.SimpleNode(
                    "multi",
                    EvidenceRequestQuestionMultipleChoice(
                        "Select the card art for the credential",
                        listOf("Green", "Blue", "Red"),
                        "Continue",
                    )
                )
            )
            .add(
                SimpleIssuingAuthorityProofingGraph.SimpleNode(
                    "message",
                    EvidenceRequestMessage(
                        "Your application is about to be sent the ID issuer for " +
                                "verification. You will get notified when the " +
                                "application is approved.",
                        "Continue",
                        null,
                    )
                )
            )
            .build()
    }

    override fun checkEvidence(collectedEvidence: Map<String, EvidenceResponse>): Boolean {
        return true
    }

    override fun generateCredentialConfiguration(collectedEvidence: Map<String, EvidenceResponse>): CredentialConfiguration {
        val firstName = (collectedEvidence["name"] as EvidenceResponseQuestionString).answer
        return CredentialConfiguration(
            "${firstName}'s Driving License",
            byteArrayOf(1, 2, 3),
            NameSpacedData.Builder().build()
        )
    }

}