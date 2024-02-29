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
import com.android.identity.issuance.evidence.EvidenceResponseIcaoNfcTunnelResult
import com.android.identity.issuance.evidence.EvidenceResponseIcaoPassiveAuthentication
import com.android.identity.issuance.evidence.EvidenceResponseMessage
import com.android.identity.issuance.simple.SimpleIcaoNfcTunnelDriver
import com.android.identity.issuance.simple.SimpleIssuingAuthorityProofingGraph
import com.android.identity.storage.StorageEngine
import com.android.identity_credential.mrtd.MrtdNfcData
import com.android.identity_credential.mrtd.MrtdNfcDataDecoder
import kotlinx.datetime.Clock
import java.io.ByteArrayOutputStream
import kotlin.time.Duration.Companion.days

class PassportBasedIssuingAuthority(
    application: WalletApplication,
    storageEngine: StorageEngine
) : SelfSignedMdlIssuingAuthority(application, storageEngine) {

    override lateinit var configuration: IssuingAuthorityConfiguration

    init {
        val baos = ByteArrayOutputStream()
        BitmapFactory.decodeResource(
            application.applicationContext.resources,
            R.drawable.img_erika_portrait
        )
            .compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val icon: ByteArray = baos.toByteArray()
        configuration = IssuingAuthorityConfiguration(
            "mDL_Utopia",
            resourceString(R.string.passport_based_authority_name),
            icon,
            setOf(CredentialPresentationFormat.MDOC_MSO),
            createCredentialConfiguration(null)
        )
    }

    override fun getProofingGraphRoot(): SimpleIssuingAuthorityProofingGraph.Node {
        return SimpleIssuingAuthorityProofingGraph.create {
            message(
                "tos",
                resourceString(R.string.passport_based_authority_tos),
                resourceString(R.string.passport_based_authority_accept),
                resourceString(R.string.passport_based_authority_reject),
            )
            icaoTunnel("tunnel", listOf(1, 2, 7)) {
                whenChipAuthenticated {
                    message(
                        "inform",
                        resourceString(R.string.passport_based_authority_chip_authentication),
                        resourceString(R.string.passport_based_authority_continue),
                        null
                    )
                }
                whenActiveAuthenticated {
                    message(
                        "inform",
                        resourceString(R.string.passport_based_authority_active_authentication),
                        resourceString(R.string.passport_based_authority_continue),
                        null
                    )
                }
                whenNotAuthenticated {
                    message(
                        "inform",
                        resourceString(R.string.passport_based_authority_no_authentication),
                        resourceString(R.string.passport_based_authority_continue),
                        null
                    )
                }
            }
            message(
                "message",
                resourceString(R.string.passport_based_authority_application_finish),
                resourceString(R.string.passport_based_authority_continue),
                null
            )
        }
    }

    override fun createNfcTunnelHandler(): SimpleIcaoNfcTunnelDriver {
        return NfcTunnelDriver()
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
                resourceString(R.string.self_signed_authority_pending_credential_title),
                createArtwork(
                    Color.rgb(192, 192, 192),
                    Color.rgb(96, 96, 96),
                    null,
                    resourceString(R.string.self_signed_authority_pending_credential_text),
                ),
                NameSpacedData.Builder().build()
            )
        }

        val icaoPassiveData = collectedEvidence["passive"]
        val icaoTunnelData = collectedEvidence["tunnel"]
        val mrtdData = if (icaoTunnelData is EvidenceResponseIcaoNfcTunnelResult)
            MrtdNfcData(icaoTunnelData.dataGroups, icaoTunnelData.securityObject)
        else if (icaoPassiveData is EvidenceResponseIcaoPassiveAuthentication)
            MrtdNfcData(icaoPassiveData.dataGroups, icaoPassiveData.securityObject)
        else
            throw IllegalStateException("Should not happen")
        val decoder = MrtdNfcDataDecoder(application.cacheDir)
        val decoded = decoder.decode(mrtdData)
        val firstName = decoded.firstName
        val lastName = decoded.lastName
        val sex = when (decoded.gender) {
            "MALE" -> 1L
            "FEMALE" -> 2L
            else -> 0L
        }
        val portrait = bitmapData(decoded.photo, R.drawable.img_erika_portrait)
        val signatureOrUsualMark = bitmapData(decoded.signature, R.drawable.img_erika_signature)

        val gradientColor = Pair(
            Color.rgb(255, 255, 64),
            Color.rgb(96, 96, 0),
        )

        val now = Clock.System.now()
        val issueDate = now
        val expiryDate = now + 5.days * 365

        val staticData = NameSpacedData.Builder()
            .putEntryString(MDL_NAMESPACE, "given_name", firstName)
            .putEntryString(MDL_NAMESPACE, "family_name", lastName)
            .putEntryByteString(MDL_NAMESPACE, "portrait", portrait)
            .putEntryByteString(MDL_NAMESPACE, "signature_usual_mark", signatureOrUsualMark)
            .putEntryNumber(MDL_NAMESPACE, "sex", sex)
            .putEntry(MDL_NAMESPACE, "issue_date", Cbor.encode(issueDate.toDataItemDateTimeString))
            .putEntry(
                MDL_NAMESPACE,
                "expiry_date",
                Cbor.encode(expiryDate.toDataItemDateTimeString)
            )
            .putEntryString(MDL_NAMESPACE, "document_number", "1234567890")
            .putEntryString(MDL_NAMESPACE, "issuing_authority", "State of Utopia")
            .putEntryString(AAMVA_NAMESPACE, "DHS_compliance", "F")
            .putEntryNumber(AAMVA_NAMESPACE, "EDL_credential", 1)
            .putEntryBoolean(MDL_NAMESPACE, "age_over_18", true)
            .putEntryBoolean(MDL_NAMESPACE, "age_over_21", true)
            .build()

        return CredentialConfiguration(
            resourceString(R.string.self_signed_authority_credential_title, firstName),
            createArtwork(
                gradientColor.first,
                gradientColor.second,
                portrait,
                resourceString(R.string.self_signed_authority_credential_text, firstName),
            ),
            staticData
        )
    }
}