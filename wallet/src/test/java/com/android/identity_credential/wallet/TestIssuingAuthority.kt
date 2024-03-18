package com.android.identity_credential.wallet

import com.android.identity.credential.NameSpacedData
import com.android.identity.crypto.EcPublicKey
import com.android.identity.issuance.CredentialConfiguration
import com.android.identity.issuance.CredentialPresentationFormat
import com.android.identity.issuance.IssuingAuthorityConfiguration
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseQuestionString
import com.android.identity.issuance.simple.SimpleIssuingAuthority
import com.android.identity.issuance.simple.SimpleIssuingAuthorityProofingGraph
import com.android.identity.storage.EphemeralStorageEngine

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
            "mDL from Test IA",
            setOf(CredentialPresentationFormat.MDOC_MSO),
            CredentialConfiguration(
                "mDL for Test IA (proofing pending)",
                byteArrayOf(1, 2, 3),
                "org.iso.18013.5.1.mDL",
                NameSpacedData.Builder().build()
            )
        )

        // This is used in testing, see SelfSignedMdlTest
        deadlineMillis = 3000L
    }

    override fun createPresentationData(presentationFormat: CredentialPresentationFormat,
                                        credentialConfiguration: CredentialConfiguration,
                                        authenticationKey: EcPublicKey
    ): ByteArray {
        return byteArrayOf(1, 2, 3)
    }

    override fun getProofingGraphRoot(): SimpleIssuingAuthorityProofingGraph.Node {
        return SimpleIssuingAuthorityProofingGraph.create {
            message(
                "tos",
                "Here's a long string with TOS",
                mapOf(),
                "Accept",
                "Do Not Accept"
            )
            question(
                "name",
                "What first name should be used for the mDL?",
                mapOf(),
                "Erika",
                "Continue",
            )
            choice("multi", "Select the card art for the credential", mapOf(),"Continue") {
                on("green", "Green") {}
                on("blue", "Blue") {}
                on("red", "Red") {}
            }
            message(
                "message",
                "Your application is about to be sent the ID issuer for " +
                        "verification. You will get notified when the " +
                        "application is approved.",
                mapOf(),
                "Continue",
                null,
            )
        }
    }

    override fun checkEvidence(collectedEvidence: Map<String, EvidenceResponse>): Boolean {
        return true
    }

    override fun generateCredentialConfiguration(collectedEvidence: Map<String, EvidenceResponse>): CredentialConfiguration {
        val firstName = (collectedEvidence["name"] as EvidenceResponseQuestionString).answer
        return CredentialConfiguration(
            "${firstName}'s Driving License",
            byteArrayOf(1, 2, 3),
            "org.iso.18013.5.1.mDL",
            NameSpacedData.Builder().build()
        )
    }

}