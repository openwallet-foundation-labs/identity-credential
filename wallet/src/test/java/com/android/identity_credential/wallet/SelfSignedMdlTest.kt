package com.android.identity_credential.wallet

import com.android.identity.issuance.CredentialRegistrationResponse
import com.android.identity.issuance.CredentialCondition
import com.android.identity.issuance.CredentialPresentationFormat
import com.android.identity.issuance.CredentialPresentationRequest
import com.android.identity.issuance.evidence.EvidenceRequestMessage
import com.android.identity.issuance.evidence.EvidenceRequestQuestionMultipleChoice
import com.android.identity.issuance.evidence.EvidenceRequestQuestionString
import com.android.identity.issuance.evidence.EvidenceResponseMessage
import com.android.identity.issuance.evidence.EvidenceResponseQuestionMultipleChoice
import com.android.identity.issuance.evidence.EvidenceResponseQuestionString
import com.android.identity.issuance.evidence.EvidenceType
import com.android.identity.issuance.evidence.EvidenceRequest
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.software.SoftwareSecureArea
import com.android.identity.storage.EphemeralStorageEngine
import kotlinx.coroutines.test.runTest
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.security.Security

class SelfSignedMdlTest {
    @Before
    fun setup() {
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    private fun getProofingQuestions() : List<EvidenceRequest> {
        return listOf<EvidenceRequest>(
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
                mapOf("green" to "Green", "blue" to "Blue", "red" to "Red"),
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

    @Test
    fun happyPath() = runTest {
        val storageEngine = EphemeralStorageEngine()
        val secureArea = SoftwareSecureArea(storageEngine)

        val ia = TestIssuingAuthority()

        // Register credential...
        val registerCredentialFlow = ia.registerCredential()
        val credentialRegistrationConfiguration = registerCredentialFlow.getCredentialRegistrationConfiguration()
        val credentialId = credentialRegistrationConfiguration.identifier
        val registrationResponse = CredentialRegistrationResponse()
        registerCredentialFlow.sendCredentialRegistrationResponse(registrationResponse)

        // Check we're now in the proofing state.
        Assert.assertEquals(
            CredentialCondition.PROOFING_REQUIRED,
            ia.credentialGetState(credentialId).condition)

        // Perform proofing
        val proofingFlow = ia.credentialProof(credentialId)

        // First piece of evidence to return...
        var evidenceToGet = proofingFlow.getEvidenceRequests()
        Assert.assertEquals(1, evidenceToGet.size)
        Assert.assertEquals(EvidenceType.MESSAGE, evidenceToGet[0].evidenceType)
        Assert.assertEquals("Here's a long string with TOS", (evidenceToGet[0] as EvidenceRequestMessage).message)
        proofingFlow.sendEvidence(EvidenceResponseMessage(true))

        // Second piece of evidence to return...
        evidenceToGet = proofingFlow.getEvidenceRequests()
        Assert.assertEquals(1, evidenceToGet.size)
        Assert.assertEquals(EvidenceType.QUESTION_STRING, evidenceToGet[0].evidenceType)
        Assert.assertEquals(
            "What first name should be used for the mDL?",
            (evidenceToGet[0] as EvidenceRequestQuestionString).message)
        Assert.assertEquals(
            "Erika",
            (evidenceToGet[0] as EvidenceRequestQuestionString).defaultValue)
        proofingFlow.sendEvidence(EvidenceResponseQuestionString("Max"))
        Assert.assertEquals(
            CredentialCondition.PROOFING_REQUIRED,
            ia.credentialGetState(credentialId).condition)

        // Third piece of evidence to return...
        evidenceToGet = proofingFlow.getEvidenceRequests()
        Assert.assertEquals(1, evidenceToGet.size)
        Assert.assertEquals(EvidenceType.QUESTION_MULTIPLE_CHOICE, evidenceToGet[0].evidenceType)
        Assert.assertEquals(3,
            (evidenceToGet[0] as EvidenceRequestQuestionMultipleChoice).possibleValues.size)
        proofingFlow.sendEvidence(
            EvidenceResponseQuestionMultipleChoice(
            (evidenceToGet[0] as EvidenceRequestQuestionMultipleChoice).possibleValues.keys.iterator().next()
        )
        )

        // Fourth piece of evidence to return...
        evidenceToGet = proofingFlow.getEvidenceRequests()
        Assert.assertEquals(1, evidenceToGet.size)
        Assert.assertEquals(EvidenceType.MESSAGE, evidenceToGet[0].evidenceType)
        Assert.assertTrue((evidenceToGet[0] as EvidenceRequestMessage).message
            .startsWith("Your application is about to be sent"))
        proofingFlow.sendEvidence(EvidenceResponseMessage(true))

        // Check there are no more pieces of evidence to return and it's now processing...
        evidenceToGet = proofingFlow.getEvidenceRequests()
        Assert.assertEquals(0, evidenceToGet.size)
        Assert.assertEquals(
            CredentialCondition.PROOFING_PROCESSING,
            ia.credentialGetState(credentialId).condition)
        // Processing is hard-coded to take three seconds
        Thread.sleep(2100)
        Assert.assertEquals(
            CredentialCondition.PROOFING_PROCESSING,
            ia.credentialGetState(credentialId).condition)
        Thread.sleep(900)
        Assert.assertEquals(
            CredentialCondition.CONFIGURATION_AVAILABLE,
            ia.credentialGetState(credentialId).condition)

        // Check we can get the credential configuration
        val configuration = ia.credentialGetConfiguration(credentialId)
        Assert.assertEquals("Max's Driving License", configuration.displayName)
        Assert.assertEquals(
            CredentialCondition.READY,
            ia.credentialGetState(credentialId).condition)

        // Check we can get CPOs, first request them
        val numMso = 5
        val requestCpoFlow = ia.credentialRequestPresentationObjects(credentialId)
        val authKeyConfiguration = requestCpoFlow.getAuthenticationKeyConfiguration()
        val credentialPresentationRequests = mutableListOf<CredentialPresentationRequest>()
        for (authKeyNumber in IntRange(0, numMso - 1)) {
            val alias = "AuthKey_$authKeyNumber"
            secureArea.createKey(alias, CreateKeySettings(authKeyConfiguration.challenge))
            credentialPresentationRequests.add(
                CredentialPresentationRequest(
                    CredentialPresentationFormat.MDOC_MSO,
                    secureArea.getKeyInfo(alias).attestation
                )
            )
        }
        requestCpoFlow.sendAuthenticationKeys(credentialPresentationRequests)

        // CredentialInformation should now reflect that the CPOs are pending and not
        // yet available..
        ia.credentialGetState(credentialId).let {
            Assert.assertEquals(CredentialCondition.READY, it.condition)
            Assert.assertEquals(5, it.numPendingCPO)
            Assert.assertEquals(0, it.numAvailableCPO)
        }
        Assert.assertEquals(0, ia.credentialGetPresentationObjects(credentialId).size)
        Thread.sleep(100)
        // Still not available...
        ia.credentialGetState(credentialId).let {
            Assert.assertEquals(CredentialCondition.READY, it.condition)
            Assert.assertEquals(5, it.numPendingCPO)
            Assert.assertEquals(0, it.numAvailableCPO)
        }
        Assert.assertEquals(0, ia.credentialGetPresentationObjects(credentialId).size)
        // But it is available after 1 second
        Thread.sleep(900)
        ia.credentialGetState(credentialId).let {
            Assert.assertEquals(CredentialCondition.READY, it.condition)
            Assert.assertEquals(0, it.numPendingCPO)
            Assert.assertEquals(5, it.numAvailableCPO)
        }
        // Check we get 5 CPOs and that they match the keys we passed in..
        ia.credentialGetPresentationObjects(credentialId).let {
            Assert.assertEquals(5, it.size)
            for (n in IntRange(0, it.size - 1)) {
                Assert.assertEquals(
                    it[n].authenticationKey,
                    credentialPresentationRequests[n]
                        .authenticationKeyAttestation.certificates.first().publicKey
                )
            }
        }
        // Once we collected them, they are no longer available to be collected
        // and nothing is pending
        ia.credentialGetState(credentialId).let {
            Assert.assertEquals(CredentialCondition.READY, it.condition)
            Assert.assertEquals(0, it.numPendingCPO)
            Assert.assertEquals(0, it.numAvailableCPO)
        }
        Assert.assertEquals(0, ia.credentialGetPresentationObjects(credentialId).size)
    }

}