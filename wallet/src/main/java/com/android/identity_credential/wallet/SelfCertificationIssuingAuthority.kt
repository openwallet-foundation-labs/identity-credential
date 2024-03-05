package com.android.identity_credential.wallet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.toDataItemDateTimeString
import com.android.identity.credential.NameSpacedData
import com.android.identity.issuance.CredentialConfiguration
import com.android.identity.issuance.CredentialPresentationFormat
import com.android.identity.issuance.IssuingAuthorityConfiguration
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseMessage
import com.android.identity.issuance.evidence.EvidenceResponseQuestionMultipleChoice
import com.android.identity.issuance.evidence.EvidenceResponseQuestionString
import com.android.identity.issuance.simple.SimpleIssuingAuthorityProofingGraph
import com.android.identity.storage.StorageEngine
import kotlinx.datetime.Clock
import java.io.ByteArrayOutputStream
import kotlin.time.Duration.Companion.days

class SelfCertificationIssuingAuthority(
    application: WalletApplication, storageEngine: StorageEngine
) : SelfSignedMdlIssuingAuthority(application, storageEngine) {

    override lateinit var configuration: IssuingAuthorityConfiguration

    init {
        val baos = ByteArrayOutputStream()
        BitmapFactory.decodeResource(
            application.applicationContext.resources, R.drawable.img_erika_portrait
        ).compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val icon: ByteArray = baos.toByteArray()
        configuration = IssuingAuthorityConfiguration(
            "mDL_Anarchy",
            resourceString(R.string.self_certification_authority_name),
            icon,
            setOf(CredentialPresentationFormat.MDOC_MSO),
            createCredentialConfiguration(null)
        )
    }

    override fun getProofingGraphRoot(): SimpleIssuingAuthorityProofingGraph.Node {
        return SimpleIssuingAuthorityProofingGraph.create {
            message(
                "tos",
                resourceString(R.string.self_certification_authority_tos),
                mapOf(),
                resourceString(R.string.self_certification_authority_accept),
                resourceString(R.string.self_certification_authority_reject),
            )
            message(
                "message",
                resourceString(R.string.self_certification_authority_application_finish),
                mapOf(),
                resourceString(R.string.self_certification_authority_continue),
                null
            )
        }
    }

    override fun checkEvidence(collectedEvidence: Map<String, EvidenceResponse>): Boolean {
        return (collectedEvidence["tos"] as EvidenceResponseMessage).acknowledged
    }

    override fun generateCredentialConfiguration(collectedEvidence: Map<String, EvidenceResponse>): CredentialConfiguration {
        return createCredentialConfiguration(collectedEvidence)
    }

    private fun createCredentialConfiguration(collectedEvidence: Map<String, EvidenceResponse>?): CredentialConfiguration {
        if (collectedEvidence == null) {
            return CredentialConfiguration(
                resourceString(R.string.self_certification_authority_pending_credential_title),
                createArtwork(
                    Color.rgb(192, 192, 192),
                    Color.rgb(96, 96, 96),
                    null,
                    resourceString(R.string.self_certification_authority_pending_credential_text),
                ),
                NameSpacedData.Builder().build()
            )
        }

        val sex = 2L
        val portrait = bitmapData(null, R.drawable.img_erika_portrait)
        val signatureOrUsualMark = bitmapData(null, R.drawable.img_erika_signature)

        val gradientColor = Pair(
                    Color.rgb(64, 255, 64),
                    Color.rgb(0, 96, 0),
                )

        val now = Clock.System.now()
        val issueDate = now
        val expiryDate = now + 5.days * 365

        val staticData =
            NameSpacedData.Builder().putEntryString(MDL_NAMESPACE, "given_name", "Erika")
                .putEntryString(MDL_NAMESPACE, "family_name", "Mustermann")
                .putEntryByteString(MDL_NAMESPACE, "portrait", portrait)
                .putEntryByteString(MDL_NAMESPACE, "signature_usual_mark", signatureOrUsualMark)
                .putEntryNumber(MDL_NAMESPACE, "sex", sex).putEntry(
                    MDL_NAMESPACE,
                    "issue_date",
                    Cbor.encode(issueDate.toDataItemDateTimeString)
                ).putEntry(
                    MDL_NAMESPACE, "expiry_date", Cbor.encode(expiryDate.toDataItemDateTimeString)
                ).putEntryString(MDL_NAMESPACE, "document_number", "1234567890")
                .putEntryString(MDL_NAMESPACE, "issuing_authority", "State of Utopia")
                .putEntryString(AAMVA_NAMESPACE, "DHS_compliance", "F")
                .putEntryNumber(AAMVA_NAMESPACE, "EDL_credential", 1)
                .putEntryBoolean(MDL_NAMESPACE, "age_over_18", true)
                .putEntryBoolean(MDL_NAMESPACE, "age_over_21", true).build()

        return CredentialConfiguration(
            resourceString(R.string.self_certification_authority_credential_title, "Erika"),
            createArtwork(
                gradientColor.first,
                gradientColor.second,
                portrait,
                resourceString(R.string.self_certification_authority_credential_text, "Erika"),
            ),
            staticData
        )
    }
}