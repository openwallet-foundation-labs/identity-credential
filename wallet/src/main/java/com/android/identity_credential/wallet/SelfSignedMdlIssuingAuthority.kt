package com.android.identity_credential.wallet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.toDataItemDateTimeString
import com.android.identity.cbor.toDataItemFullDate
import com.android.identity.credential.NameSpacedData
import com.android.identity.credentialtype.CredentialType
import com.android.identity.credentialtype.knowntypes.DrivingLicense
import com.android.identity.credentialtype.knowntypes.EUPersonalID
import com.android.identity.issuance.CredentialConfiguration
import com.android.identity.issuance.CredentialPresentationFormat
import com.android.identity.issuance.IssuingAuthorityConfiguration
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseIcaoNfcTunnelResult
import com.android.identity.issuance.evidence.EvidenceResponseIcaoPassiveAuthentication
import com.android.identity.issuance.evidence.EvidenceResponseMessage
import com.android.identity.issuance.evidence.EvidenceResponseQuestionMultipleChoice
import com.android.identity.issuance.simple.SimpleIcaoNfcTunnelDriver
import com.android.identity.issuance.simple.SimpleIssuingAuthorityProofingGraph
import com.android.identity.storage.StorageEngine
import com.android.identity_credential.mrtd.MrtdNfcData
import com.android.identity_credential.mrtd.MrtdNfcDataDecoder
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import java.io.ByteArrayOutputStream
import kotlin.time.Duration.Companion.days

class SelfSignedMdlIssuingAuthority(
    application: WalletApplication,
    storageEngine: StorageEngine
) : SelfSignedMdocIssuingAuthority(application, storageEngine) {

    companion object {
        private const val MDL_DOCTYPE = DrivingLicense.MDL_DOCTYPE
        private const val MDL_NAMESPACE = DrivingLicense.MDL_NAMESPACE
        private const val AAMVA_NAMESPACE = DrivingLicense.AAMVA_NAMESPACE
    }

    override val docType: String = MDL_DOCTYPE
    override lateinit var configuration: IssuingAuthorityConfiguration
    private val tosAssets: Map<String, ByteArray>;

    init {
        val baos = ByteArrayOutputStream()
        BitmapFactory.decodeResource(
            application.applicationContext.resources, R.drawable.utopia_dmv_issuing_authority_logo
        ).compress(Bitmap.CompressFormat.PNG, 90, baos)
        val icon: ByteArray = baos.toByteArray()
        configuration = IssuingAuthorityConfiguration(
            identifier = "mDL_Utopia",
            issuingAuthorityName = resourceString(R.string.utopia_mdl_issuing_authority_name),
            issuingAuthorityLogo = icon,
            description = resourceString(R.string.utopia_mdl_issuing_authority_description),
            credentialFormats = setOf(CredentialPresentationFormat.MDOC_MSO),
            pendingCredentialInformation = createCredentialConfiguration(null)
        )
        tosAssets = mapOf("utopia_logo.png" to resourceBytes(R.drawable.utopia_dmv_issuing_authority_logo))
    }

    override fun getProofingGraphRoot(): SimpleIssuingAuthorityProofingGraph.Node {
        return SimpleIssuingAuthorityProofingGraph.create {
            message(
                "tos",
                resourceString(R.string.utopia_mdl_issuing_authority_tos),
                tosAssets,
                resourceString(R.string.utopia_mdl_issuing_authority_accept),
                resourceString(R.string.utopia_mdl_issuing_authority_reject),
            )
            choice(
                id = "path",
                message = resourceString(R.string.utopia_mdl_issuing_authority_hardcoded_or_derived),
                assets = mapOf(),
                acceptButtonText = "Continue"
            ) {
                on(id = "hardcoded", text = resourceString(R.string.utopia_mdl_issuing_authority_hardcoded_option)) {
                }
                on(id = "passport", text = resourceString(R.string.utopia_mdl_issuing_authority_passport_option)) {
                    icaoTunnel("tunnel", listOf(1, 2, 7)) {
                        whenChipAuthenticated {}
                        whenActiveAuthenticated {}
                        whenNotAuthenticated {}
                    }
                }
            }
            message(
                "message",
                message = resourceString(R.string.utopia_mdl_issuing_authority_application_finish),
                assets = mapOf(),
                acceptButtonText = resourceString(R.string.utopia_mdl_issuing_authority_continue),
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
        val baos = ByteArrayOutputStream()
        BitmapFactory.decodeResource(
            application.applicationContext.resources, R.drawable.utopia_driving_license_card_art
        ).compress(Bitmap.CompressFormat.PNG, 90, baos)
        val cardArt: ByteArray = baos.toByteArray()

        if (collectedEvidence == null) {
            return CredentialConfiguration(
                resourceString(R.string.utopia_mdl_issuing_authority_pending_credential_title),
                cardArt,
                MDL_DOCTYPE,
                NameSpacedData.Builder().build()
            )
        }

        val staticData: NameSpacedData

        val now = Clock.System.now()
        val issueDate = now
        val expiryDate = now + 365.days * 5

        val credType = application.credentialTypeRepository.getCredentialTypeForMdoc(MDL_DOCTYPE)!!

        val path = (collectedEvidence["path"] as EvidenceResponseQuestionMultipleChoice).answerId
        if (path == "hardcoded") {
            staticData = getSampleData(credType).build()
        } else {
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

            // Make sure we set at least all the mandatory data elements
            //
            // TODO: get birth_date from passport
            //
            staticData = NameSpacedData.Builder()
                .putEntryString(MDL_NAMESPACE, "given_name", firstName)
                .putEntryString(MDL_NAMESPACE, "family_name", lastName)
                .putEntry(MDL_NAMESPACE, "birth_date",
                    Cbor.encode(LocalDate.parse("1970-01-01").toDataItemFullDate))
                .putEntryByteString(MDL_NAMESPACE, "portrait", portrait)
                .putEntryByteString(MDL_NAMESPACE, "signature_usual_mark", signatureOrUsualMark)
                .putEntryNumber(MDL_NAMESPACE, "sex", sex)
                .putEntry(MDL_NAMESPACE, "issue_date",
                    Cbor.encode(issueDate.toDataItemDateTimeString))
                .putEntry(MDL_NAMESPACE, "expiry_date",
                    Cbor.encode(expiryDate.toDataItemDateTimeString)
                )
                .putEntryString(MDL_NAMESPACE, "issuing_authority",
                    resourceString(R.string.utopia_mdl_issuing_authority_name),)
                .putEntryString(MDL_NAMESPACE, "issuing_country", "UT")
                .putEntryString(MDL_NAMESPACE, "un_distinguishing_sign", "UTO")
                .putEntryString(MDL_NAMESPACE, "document_number", "1234567890")
                .putEntry(MDL_NAMESPACE, "driving_privileges",
                    Cbor.encode(CborArray.builder().end().build()))

                .putEntryBoolean(MDL_NAMESPACE, "age_over_18", true)
                .putEntryBoolean(MDL_NAMESPACE, "age_over_21", true)

                .putEntryString(AAMVA_NAMESPACE, "DHS_compliance", "F")
                .putEntryNumber(AAMVA_NAMESPACE, "EDL_credential", 1)
                .putEntryNumber(AAMVA_NAMESPACE, "sex", sex)
                .build()
        }

        val firstName = staticData.getDataElementString(MDL_NAMESPACE, "given_name")
        return CredentialConfiguration(
            resourceString(R.string.utopia_mdl_issuing_authority_credential_title, firstName),
            cardArt,
            MDL_DOCTYPE,
            staticData
        )
    }

    private fun getSampleData(credentialType: CredentialType): NameSpacedData.Builder {
        val portrait = bitmapData(null, R.drawable.img_erika_portrait)
        val signatureOrUsualMark = bitmapData(null, R.drawable.img_erika_signature)
        val builder = NameSpacedData.Builder()
        for ((namespaceName, namespace) in credentialType.mdocCredentialType!!.namespaces) {
            for ((dataElementName, dataElement) in namespace.dataElements) {
                if (dataElement.attribute.sampleValue != null) {
                    builder.putEntry(
                        namespaceName,
                        dataElementName,
                        Cbor.encode(dataElement.attribute.sampleValue!!)
                    )
                }
            }
        }
        // Sample data currently doesn't have portrait or signature_usual_mark
        builder
            .putEntryByteString(MDL_NAMESPACE, "portrait", portrait)
            .putEntryByteString(MDL_NAMESPACE, "signature_usual_mark", signatureOrUsualMark)
        return builder
    }
}