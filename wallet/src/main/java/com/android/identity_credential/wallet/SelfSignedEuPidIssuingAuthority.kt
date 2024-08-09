package com.android.identity_credential.wallet

import android.content.Context
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.toDataItemDateTimeString
import com.android.identity.cbor.toDataItemFullDate
import com.android.identity.crypto.EcCurve
import com.android.identity.document.NameSpacedData
import com.android.identity.documenttype.DocumentType
import com.android.identity.documenttype.knowntypes.EUPersonalID
import com.android.identity.issuance.CredentialConfiguration
import com.android.identity.issuance.DocumentConfiguration
import com.android.identity.issuance.RegistrationResponse
import com.android.identity.issuance.IssuingAuthorityConfiguration
import com.android.identity.issuance.MdocDocumentConfiguration
import com.android.identity.issuance.SdJwtVcDocumentConfiguration
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseIcaoNfcTunnelResult
import com.android.identity.issuance.evidence.EvidenceResponseIcaoPassiveAuthentication
import com.android.identity.issuance.evidence.EvidenceResponseMessage
import com.android.identity.issuance.evidence.EvidenceResponseQuestionMultipleChoice
import com.android.identity.issuance.evidence.EvidenceResponseQuestionString
import com.android.identity.issuance.simple.SimpleIcaoNfcTunnelDriver
import com.android.identity.issuance.simple.SimpleIssuingAuthorityProofingGraph
import com.android.identity.securearea.KeyPurpose
import com.android.identity.storage.StorageEngine
import com.android.identity.mrtd.MrtdAccessData
import com.android.identity.mrtd.MrtdAccessDataCan
import com.android.identity.mrtd.MrtdNfcData
import com.android.identity.mrtd.MrtdNfcDataDecoder
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

class SelfSignedEuPidIssuingAuthority(
    application: WalletApplication,
    storageEngine: StorageEngine,
    emitOnStateChanged: suspend (documentId: String) -> Unit
) : SelfSignedIssuingAuthority(
    application,
    storageEngine,
    emitOnStateChanged
) {
    companion object {
        private const val EUPID_NAMESPACE = EUPersonalID.EUPID_NAMESPACE
        private const val EUPID_DOCTYPE = EUPersonalID.EUPID_DOCTYPE

        fun getConfiguration(context: Context): IssuingAuthorityConfiguration {
            return IssuingAuthorityConfiguration(
                identifier = "euPid_Utopia",
                issuingAuthorityName = resourceString(context, R.string.utopia_eu_pid_issuing_authority_name),
                issuingAuthorityLogo = pngData(context, R.drawable.utopia_pid_issuing_authority_logo),
                issuingAuthorityDescription = resourceString(context, R.string.utopia_eu_pid_issuing_authority_description),
                pendingDocumentInformation = DocumentConfiguration(
                    displayName = resourceString(context, R.string.utopia_eu_pid_issuing_authority_pending_document_title),
                    typeDisplayName = "Personal Identification Document",
                    cardArt = pngData(context, R.drawable.utopia_pid_card_art),
                    requireUserAuthenticationToViewDocument = false,
                    mdocConfiguration = null,
                    sdJwtVcDocumentConfiguration = null
                ),
                numberOfCredentialsToRequest = 3,
                minCredentialValidityMillis = 30 * 24 * 3600L,
                maxUsesPerCredentials = 1
            )
        }
    }

    override suspend fun getConfiguration(): IssuingAuthorityConfiguration {
        return getConfiguration(application.applicationContext)
    }

    override val docType: String = EUPID_DOCTYPE

    private val tosAssets: Map<String, ByteArray> =
        mapOf("utopia_logo.png" to resourceBytes(R.drawable.utopia_pid_issuing_authority_logo))

    override fun getProofingGraphRoot(
        registrationResponse: RegistrationResponse
    ): SimpleIssuingAuthorityProofingGraph.Node {
        return SimpleIssuingAuthorityProofingGraph.create {
            message(
                "tos",
                resourceString(R.string.utopia_eu_pid_issuing_authority_tos),
                tosAssets,
                resourceString(R.string.utopia_eu_pid_issuing_authority_accept),
                resourceString(R.string.utopia_eu_pid_issuing_authority_reject),
            )
            choice(
                id = "path",
                message = resourceString(R.string.utopia_eu_pid_issuing_authority_hardcoded_or_derived),
                assets = mapOf(),
                acceptButtonText = resourceString(R.string.utopia_eu_pid_issuing_authority_continue)
            ) {
                on(id = "hardcoded", text = resourceString(R.string.utopia_eu_pid_issuing_authority_hardcoded_option)) {
                }
                on(id = "passport", text = resourceString(R.string.utopia_eu_pid_issuing_authority_passport_option)) {
                    icaoPassiveAuthentication("passive", listOf(1))
                }
                on(id = "id_card", text = resourceString(R.string.utopia_eu_pid_issuing_authority_id_option)) {
                    question("can",
                        resourceString(R.string.utopia_eu_pid_issuing_authority_enter_can),
                        mapOf(), "",
                        resourceString(R.string.utopia_eu_pid_issuing_authority_continue))
                    icaoTunnel("tunnel", listOf(1), false) {
                        whenChipAuthenticated {}
                        whenActiveAuthenticated {}
                        whenNotAuthenticated {}
                    }
                }
            }
            message(
                "message",
                resourceString(R.string.utopia_eu_pid_issuing_authority_application_finish),
                mapOf(),
                resourceString(R.string.utopia_eu_pid_issuing_authority_continue),
                null
            )
            requestNotificationPermission(
                "notificationPermission",
                permissionNotAvailableMessage = resourceString(R.string.permission_post_notifications_rationale_md),
                grantPermissionButtonText = resourceString(R.string.permission_post_notifications_grant_permission_button_text),
                continueWithoutPermissionButtonText = resourceString(R.string.permission_post_notifications_continue_without_permission_button_text),
                assets = mapOf()
            )
        }
    }

    override fun createNfcTunnelHandler(): SimpleIcaoNfcTunnelDriver {
        return NfcTunnelDriver()
    }

    override fun getMrtdAccessData(collectedEvidence: Map<String, EvidenceResponse>): MrtdAccessData? {
        return if (collectedEvidence.containsKey("can")) {
            MrtdAccessDataCan((collectedEvidence["can"] as EvidenceResponseQuestionString).answer)
        } else {
            null
        }
    }

    override fun checkEvidence(collectedEvidence: Map<String, EvidenceResponse>): Boolean {
        return (collectedEvidence["tos"] as EvidenceResponseMessage).acknowledged
    }

    override fun generateDocumentConfiguration(collectedEvidence: Map<String, EvidenceResponse>): DocumentConfiguration {
        return createDocumentConfiguration(collectedEvidence)
    }

    override fun createCredentialConfiguration(
        collectedEvidence: MutableMap<String, EvidenceResponse>
    ): CredentialConfiguration {
        val challenge = byteArrayOf(1, 2, 3)
        return CredentialConfiguration(
            challenge,
            "AndroidKeystoreSecureArea",
            Cbor.encode(
                CborMap.builder()
                    .put("curve", EcCurve.P256.coseCurveIdentifier)
                    .put("purposes", KeyPurpose.encodeSet(setOf(KeyPurpose.SIGN)))
                    .put("userAuthenticationRequired", true)
                    .put("userAuthenticationTimeoutMillis", 0L)
                    .put("userAuthenticationTypes", 3 /* LSKF + Biometrics */)
                    .end().build()
            )
        )
    }

    private fun createDocumentConfiguration(collectedEvidence: Map<String, EvidenceResponse>?): DocumentConfiguration {
        val cardArt: ByteArray = pngData(application.applicationContext, R.drawable.utopia_pid_card_art)

        if (collectedEvidence == null) {
            return DocumentConfiguration(
                displayName = resourceString(R.string.utopia_eu_pid_issuing_authority_pending_document_title),
                typeDisplayName = "Personal Identification Document",
                cardArt = cardArt,
                requireUserAuthenticationToViewDocument = false,
                mdocConfiguration = null,
                sdJwtVcDocumentConfiguration = null,
            )
        }

        val staticData: NameSpacedData

        val now = Clock.System.now()
        val issueDate = now
        val expiryDate = now + 365.days * 5

        val credType = application.documentTypeRepository.getDocumentTypeForMdoc(EUPID_DOCTYPE)!!

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
            val decoder = MrtdNfcDataDecoder()
            val decoded = decoder.decode(mrtdData)
            val firstName = decoded.firstName
            val lastName = decoded.lastName
            val sex = when (decoded.gender) {
                "MALE" -> 1L
                "FEMALE" -> 2L
                else -> 0L
            }
            val timeZone = TimeZone.currentSystemDefault()
            val dateOfBirth = LocalDate.parse(input = decoded.dateOfBirth,
                format = LocalDate.Format {
                    // date of birth cannot be in future
                    yearTwoDigits(now.toLocalDateTime(timeZone).year - 99)
                    monthNumber()
                    dayOfMonth()
                })
            val dateOfBirthInstant = dateOfBirth.atStartOfDayIn(timeZone)
            // over 18/21 is calculated purely based on calendar date (not based on the birth time zone)
            val ageOver18 = now > dateOfBirthInstant.plus(18, DateTimeUnit.YEAR, timeZone)
            val ageOver21 = now > dateOfBirthInstant.plus(21, DateTimeUnit.YEAR, timeZone)

            // Make sure we set at least all the mandatory data elements
            staticData = NameSpacedData.Builder()
                .putEntryString(EUPID_NAMESPACE, "given_name", firstName)
                .putEntryString(EUPID_NAMESPACE, "family_name", lastName)
                .putEntry(EUPID_NAMESPACE, "birth_date",
                    Cbor.encode(dateOfBirth.toDataItemFullDate()))
                .putEntryNumber(EUPID_NAMESPACE, "gender", sex)
                .putEntry(EUPID_NAMESPACE, "issuance_date",
                    Cbor.encode(issueDate.toDataItemDateTimeString()))
                .putEntry(EUPID_NAMESPACE, "expiry_date",
                    Cbor.encode(expiryDate.toDataItemDateTimeString())
                )
                .putEntryString(EUPID_NAMESPACE, "issuing_authority",
                    resourceString(R.string.utopia_eu_pid_issuing_authority_name))
                .putEntryString(EUPID_NAMESPACE, "issuing_country",
                    "UT")
                .putEntryBoolean(EUPID_NAMESPACE, "age_over_18", ageOver18)
                .putEntryBoolean(EUPID_NAMESPACE, "age_over_21", ageOver21)
                .putEntryString(EUPID_NAMESPACE, "document_number", "1234567890")
                .putEntryString(EUPID_NAMESPACE, "administrative_number", "123456789")
                .build()
        }

        val firstName = staticData.getDataElementString(EUPID_NAMESPACE, "given_name")
        return DocumentConfiguration(
            displayName = resourceString(R.string.utopia_eu_pid_issuing_authority_document_title, firstName),
            typeDisplayName = "Personal Identification Document",
            cardArt = cardArt,
            requireUserAuthenticationToViewDocument = false,
            mdocConfiguration = MdocDocumentConfiguration(
                docType = EUPID_DOCTYPE,
                staticData = staticData
            ),
            sdJwtVcDocumentConfiguration = SdJwtVcDocumentConfiguration(
                // TODO: the vct is TBD, just use this for now
                vct = "PersonalIdentificationDocument"
            ),
        )
    }

    override fun developerModeRequestUpdate(currentConfiguration: DocumentConfiguration): DocumentConfiguration {
        // The update consists of just slapping an extra 0 at the end of `administrative_number`
        val newAdministrativeNumber = currentConfiguration.mdocConfiguration!!.staticData
            .getDataElementString(EUPID_NAMESPACE, "administrative_number") + "0"

        val builder = NameSpacedData.Builder(currentConfiguration.mdocConfiguration!!.staticData)
        builder.putEntryString(
            EUPID_NAMESPACE,
            "administrative_number",
            newAdministrativeNumber
        )

        return DocumentConfiguration(
            displayName = currentConfiguration.displayName,
            typeDisplayName = "Personal Identification Document",
            cardArt = currentConfiguration.cardArt,
            requireUserAuthenticationToViewDocument = false,
            mdocConfiguration = MdocDocumentConfiguration(
                docType = EUPID_DOCTYPE,
                staticData = builder.build()
            ),
            sdJwtVcDocumentConfiguration = SdJwtVcDocumentConfiguration(
                // TODO: the vct is TBD, just use this for now
                vct = "PersonalIdentificationDocument"
            ),
        )
    }

    private fun getSampleData(documentType: DocumentType): NameSpacedData.Builder {
        val builder = NameSpacedData.Builder()
        for ((namespaceName, namespace) in documentType.mdocDocumentType!!.namespaces) {
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
        return builder
    }
}