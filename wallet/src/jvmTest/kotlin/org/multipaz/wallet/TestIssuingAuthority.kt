package org.multipaz_credential.wallet

import org.multipaz.document.NameSpacedData
import org.multipaz.crypto.EcPublicKey
import org.multipaz.issuance.CredentialConfiguration
import org.multipaz.issuance.IssuingAuthorityConfiguration
import org.multipaz.issuance.MdocDocumentConfiguration
import org.multipaz.issuance.evidence.EvidenceResponse
import org.multipaz.issuance.evidence.EvidenceResponseQuestionString
import org.multipaz.issuance.simple.SimpleIssuingAuthority
import org.multipaz.issuance.simple.SimpleIssuingAuthorityProofingGraph
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
        pendingDocumentInformation = org.multipaz.issuance.DocumentConfiguration(
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

    override fun createPresentationData(presentationFormat: org.multipaz.issuance.CredentialFormat,
                                        documentConfiguration: org.multipaz.issuance.DocumentConfiguration,
                                        authenticationKey: EcPublicKey
    ): ByteArray {
        return byteArrayOf(1, 2, 3)
    }

    override fun developerModeRequestUpdate(currentConfiguration: org.multipaz.issuance.DocumentConfiguration): org.multipaz.issuance.DocumentConfiguration {
        return configuration.pendingDocumentInformation
    }

    override suspend fun getConfiguration(): org.multipaz.issuance.IssuingAuthorityConfiguration {
        return configuration
    }

    override fun getProofingGraphRoot(
        registrationResponse: org.multipaz.issuance.RegistrationResponse
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

    override fun generateDocumentConfiguration(collectedEvidence: Map<String, EvidenceResponse>): org.multipaz.issuance.DocumentConfiguration {
        val firstName = (collectedEvidence["name"] as EvidenceResponseQuestionString).answer
        return org.multipaz.issuance.DocumentConfiguration(
            displayName = "${firstName}'s Driving License",
            typeDisplayName = "Driving License",
            cardArt = byteArrayOf(1, 2, 3),
            requireUserAuthenticationToViewDocument = true,
            mdocConfiguration = org.multipaz.issuance.MdocDocumentConfiguration(
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