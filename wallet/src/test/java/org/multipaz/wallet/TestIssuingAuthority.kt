package org.multipaz.wallet

import org.multipaz.document.NameSpacedData
import org.multipaz.crypto.EcPublicKey
import org.multipaz.provisioning.CredentialConfiguration
import org.multipaz.provisioning.IssuingAuthorityConfiguration
import org.multipaz.provisioning.MdocDocumentConfiguration
import org.multipaz.provisioning.evidence.EvidenceResponse
import org.multipaz.provisioning.evidence.EvidenceResponseQuestionString
import org.multipaz.wallet.provisioning.simple.SimpleIssuingAuthority
import org.multipaz.wallet.provisioning.simple.SimpleIssuingAuthorityProofingGraph
import org.multipaz.storage.EphemeralStorageEngine
import org.multipaz.mrtd.MrtdAccessData
import org.multipaz.securearea.config.SecureAreaConfigurationSoftware
import kotlinx.io.bytestring.ByteString
import kotlin.time.Duration.Companion.seconds

class TestIssuingAuthority: SimpleIssuingAuthority(EphemeralStorageEngine(), {}) {

    private var configuration = IssuingAuthorityConfiguration(
        identifier = "mDL_SelfSigned",
        issuingAuthorityName = "Test IA",
        issuingAuthorityLogo = byteArrayOf(1, 2, 3),
        issuingAuthorityDescription = "mDL from Test IA",
        pendingDocumentInformation = org.multipaz.provisioning.DocumentConfiguration(
            displayName = "mDL for Test IA (proofing pending)",
            typeDisplayName = "Driving License",
            cardArt = byteArrayOf(1, 2, 3),
            requireUserAuthenticationToViewDocument = false,
            mdocConfiguration = MdocDocumentConfiguration(
                "org.iso.18013.5.1.mDL",
                NameSpacedData.Builder().build(),
            ),
            sdJwtVcDocumentConfiguration = null,
            directAccessConfiguration = null
        ),
        maxUsesPerCredentials = 1,
        minCredentialValidityMillis = 1000L,
        numberOfCredentialsToRequest = 3
    )

    override fun getMrtdAccessData(collectedEvidence: Map<String, EvidenceResponse>): MrtdAccessData? {
        return null
    }

    init {

        // This is used in testing, see SelfSignedMdlTest
        delayForProofingAndIssuance = 3.seconds
    }

    override fun createPresentationData(presentationFormat: org.multipaz.provisioning.CredentialFormat,
                                        documentConfiguration: org.multipaz.provisioning.DocumentConfiguration,
                                        authenticationKey: EcPublicKey
    ): ByteArray {
        return byteArrayOf(1, 2, 3)
    }

    override fun developerModeRequestUpdate(currentConfiguration: org.multipaz.provisioning.DocumentConfiguration): org.multipaz.provisioning.DocumentConfiguration {
        return configuration.pendingDocumentInformation
    }

    override suspend fun getConfiguration(): org.multipaz.provisioning.IssuingAuthorityConfiguration {
        return configuration
    }

    override fun getProofingGraphRoot(
        registrationResponse: org.multipaz.provisioning.RegistrationResponse
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

    override fun generateDocumentConfiguration(collectedEvidence: Map<String, EvidenceResponse>): org.multipaz.provisioning.DocumentConfiguration {
        val firstName = (collectedEvidence["name"] as EvidenceResponseQuestionString).answer
        return org.multipaz.provisioning.DocumentConfiguration(
            displayName = "${firstName}'s Driving License",
            typeDisplayName = "Driving License",
            cardArt = byteArrayOf(1, 2, 3),
            requireUserAuthenticationToViewDocument = true,
            mdocConfiguration = org.multipaz.provisioning.MdocDocumentConfiguration(
                "org.iso.18013.5.1.mDL",
                NameSpacedData.Builder().build(),
            ),
            sdJwtVcDocumentConfiguration = null,
            directAccessConfiguration = null
        )
    }

    override fun createCredentialConfiguration(
        collectedEvidence: MutableMap<String, EvidenceResponse>
    ): CredentialConfiguration {
        return CredentialConfiguration(
            challenge = ByteString(byteArrayOf(1, 2, 3)),
            keyAssertionRequired = false,
            secureAreaConfiguration = SecureAreaConfigurationSoftware()
        )
    }
}