package com.android.identity_credential.wallet

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborMap
import com.android.identity.crypto.EcCurve
import com.android.identity.document.NameSpacedData
import com.android.identity.crypto.EcPublicKey
import com.android.identity.issuance.CredentialConfiguration
import com.android.identity.issuance.DocumentConfiguration
import com.android.identity.issuance.CredentialFormat
import com.android.identity.issuance.RegistrationResponse
import com.android.identity.issuance.IssuingAuthorityConfiguration
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseQuestionString
import com.android.identity.issuance.simple.SimpleIssuingAuthority
import com.android.identity.issuance.simple.SimpleIssuingAuthorityProofingGraph
import com.android.identity.securearea.KeyPurpose
import com.android.identity.storage.EphemeralStorageEngine
import com.android.identity_credential.mrtd.MrtdAccessData
import kotlin.time.Duration.Companion.seconds

class TestIssuingAuthority: SimpleIssuingAuthority(EphemeralStorageEngine()) {
    companion object {
        private const val TAG = "TestIssuingAuthority"
    }

    override lateinit var configuration: IssuingAuthorityConfiguration

    override fun getMrtdAccessData(collectedEvidence: Map<String, EvidenceResponse>): MrtdAccessData? {
        return null
    }

    init {
        configuration = IssuingAuthorityConfiguration(
            "mDL_SelfSigned",
            "Test IA",
            byteArrayOf(1, 2, 3),
            "mDL from Test IA",
            setOf(CredentialFormat.MDOC_MSO),
            DocumentConfiguration(
                "mDL for Test IA (proofing pending)",
                byteArrayOf(1, 2, 3),
                "org.iso.18013.5.1.mDL",
                NameSpacedData.Builder().build(),
                false
            )
        )

        // This is used in testing, see SelfSignedMdlTest
        delayForProofingAndIssuance = 3.seconds
    }

    override fun createPresentationData(presentationFormat: CredentialFormat,
                                        documentConfiguration: DocumentConfiguration,
                                        authenticationKey: EcPublicKey
    ): ByteArray {
        return byteArrayOf(1, 2, 3)
    }

    override fun developerModeRequestUpdate(currentConfiguration: DocumentConfiguration): DocumentConfiguration {
        return configuration.pendingDocumentInformation
    }

    override fun getProofingGraphRoot(
        registrationResponse: RegistrationResponse
    ): SimpleIssuingAuthorityProofingGraph.Node {
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

    override fun generateDocumentConfiguration(collectedEvidence: Map<String, EvidenceResponse>): DocumentConfiguration {
        val firstName = (collectedEvidence["name"] as EvidenceResponseQuestionString).answer
        return DocumentConfiguration(
            "${firstName}'s Driving License",
            byteArrayOf(1, 2, 3),
            "org.iso.18013.5.1.mDL",
            NameSpacedData.Builder().build(),
            false
        )
    }

    override fun createCredentialConfiguration(collectedEvidence: MutableMap<String, EvidenceResponse>): CredentialConfiguration {
        return CredentialConfiguration(
            byteArrayOf(1, 2, 3),
            "SoftwareSecureArea",
            Cbor.encode(
                CborMap.builder()
                    .put("curve", EcCurve.P256.coseCurveIdentifier)
                    .put("purposes", KeyPurpose.encodeSet(setOf(KeyPurpose.SIGN)))
                    .end().build()
            )
        )
    }

}