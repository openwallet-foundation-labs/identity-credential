package com.android.identity.issuance.hardcoded

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.Tagged
import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.cbor.toDataItem
import com.android.identity.cbor.toDataItemDateTimeString
import com.android.identity.cbor.toDataItemFullDate
import com.android.identity.cose.Cose
import com.android.identity.cose.CoseLabel
import com.android.identity.cose.CoseNumberLabel
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.X509Certificate
import com.android.identity.crypto.X509CertificateChain
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPublicKey
import com.android.identity.document.NameSpacedData
import com.android.identity.documenttype.DocumentType
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.documenttype.knowntypes.DrivingLicense
import com.android.identity.documenttype.knowntypes.EUPersonalID
import com.android.identity.flow.annotation.FlowJoin
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.server.Configuration
import com.android.identity.flow.server.Resources
import com.android.identity.flow.server.Storage
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.issuance.CredentialData
import com.android.identity.issuance.CredentialFormat
import com.android.identity.issuance.DocumentCondition
import com.android.identity.issuance.DocumentConfiguration
import com.android.identity.issuance.DocumentState
import com.android.identity.issuance.IssuingAuthority
import com.android.identity.issuance.IssuingAuthorityConfiguration
import com.android.identity.issuance.MdocDocumentConfiguration
import com.android.identity.issuance.RegistrationResponse
import com.android.identity.issuance.IssuingAuthorityNotification
import com.android.identity.issuance.WalletServerSettings
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseIcaoNfcTunnelResult
import com.android.identity.issuance.evidence.EvidenceResponseIcaoPassiveAuthentication
import com.android.identity.issuance.evidence.EvidenceResponseQuestionMultipleChoice
import com.android.identity.issuance.proofing.defaultCredentialConfiguration
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator
import com.android.identity.mdoc.mso.StaticAuthDataGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.mrtd.MrtdNfcData
import com.android.identity.mrtd.MrtdNfcDataDecoder
import com.android.identity.util.Logger
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearsUntil
import kotlinx.io.bytestring.ByteString
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

private const val MDL_DOCTYPE = DrivingLicense.MDL_DOCTYPE
private const val MDL_NAMESPACE = DrivingLicense.MDL_NAMESPACE
private const val AAMVA_NAMESPACE = DrivingLicense.AAMVA_NAMESPACE
private const val EUPID_NAMESPACE = EUPersonalID.EUPID_NAMESPACE
private const val EUPID_DOCTYPE = EUPersonalID.EUPID_DOCTYPE

/**
 * State of [IssuingAuthority] RPC implementation.
 */
@FlowState(
    flowInterface = IssuingAuthority::class
)
@CborSerializable
class IssuingAuthorityState(
    val clientId: String = "",
    val authorityId: String = ""
) {
    companion object {
        private const val TAG = "IssuingAuthorityState"
        
        private const val TYPE_EU_PID = "EuPid"
        private const val TYPE_DRIVING_LICENSE = "DrivingLicense"

        fun getConfiguration(env: FlowEnvironment, id: String): IssuingAuthorityConfiguration {
            return env.cache(IssuingAuthorityConfiguration::class, id) { configuration, resources ->
                val settings = WalletServerSettings(configuration)
                val prefix = "issuingAuthority.$id"
                val logoPath = settings.getString("${prefix}.logo") ?: "default/logo.png"
                val logo = resources.getRawResource(logoPath)!!
                val artPath =
                    settings.getString("${prefix}.cardArt") ?: "default/card_art.png"
                val art = resources.getRawResource(artPath)!!
                val requireUserAuthenticationToViewDocument = settings.getBool(
                    "${prefix}.requireUserAuthenticationToViewDocument",
                    false
                )
                val type = settings.getString("$prefix.type") ?: TYPE_DRIVING_LICENSE

                IssuingAuthorityConfiguration(
                    identifier = id,
                    issuingAuthorityName = settings.getString("${prefix}.name") ?: "Untitled",
                    issuingAuthorityLogo = logo.toByteArray(),
                    issuingAuthorityDescription = settings.getString("${prefix}.description")
                        ?: "Unknown",
                    pendingDocumentInformation = DocumentConfiguration(
                        displayName = "Pending",
                        typeDisplayName = if (type == TYPE_DRIVING_LICENSE) "Driving License" else "EU Personal ID",
                        cardArt = art.toByteArray(),
                        requireUserAuthenticationToViewDocument = requireUserAuthenticationToViewDocument,
                        mdocConfiguration = null,
                        sdJwtVcDocumentConfiguration = null
                    )
                )
            }
        }

        val documentTypeRepository = DocumentTypeRepository().apply {
            addDocumentType(DrivingLicense.getDocumentType())
            addDocumentType(EUPersonalID.getDocumentType())
        }
    }

    @FlowMethod
    suspend fun register(env: FlowEnvironment): RegistrationState {
        val documentId = createIssuerDocument(env,
            IssuerDocument(
                RegistrationResponse(false),
                DocumentCondition.PROOFING_REQUIRED,
                mutableMapOf(),
                null,
                mutableListOf()
            )
        )
        return RegistrationState(documentId)
    }

    @FlowMethod
    suspend fun getConfiguration(env: FlowEnvironment): IssuingAuthorityConfiguration {
        return getConfiguration(env, authorityId)
    }

    @FlowJoin
    suspend fun completeRegistration(env: FlowEnvironment, registrationState: RegistrationState) {
        updateIssuerDocument(
            env,
            registrationState.documentId,
            IssuerDocument(
                registrationState.response!!,
                DocumentCondition.PROOFING_REQUIRED,
                mutableMapOf(),           // collectedEvidence - initially empty
                null,  // no initial document configuration
                mutableListOf()           // cpoRequests - initially empty
            ))
    }

    @FlowMethod
    suspend fun getState(env: FlowEnvironment, documentId: String): DocumentState {
        val now = Clock.System.now()

        if (!issuerDocumentExists(env, documentId)) {
            return DocumentState(
                now,
                DocumentCondition.NO_SUCH_DOCUMENT,
                0,
                0
            )
        }

        val issuerDocument = loadIssuerDocument(env, documentId)

        if (issuerDocument.state == DocumentCondition.PROOFING_PROCESSING) {
            issuerDocument.state =
                if (checkEvidence(issuerDocument.collectedEvidence)) {
                    DocumentCondition.CONFIGURATION_AVAILABLE
                } else {
                    DocumentCondition.PROOFING_FAILED
                }
            updateIssuerDocument(env, documentId, issuerDocument)
        }

        // This information is helpful for when using the admin web interface.
        Logger.i(TAG, "Returning state for clientId=$clientId " +
                "issuingAuthorityId=$authorityId documentId=$documentId")

        // We create and sign Credential requests immediately so numPendingCredentialRequests is
        // always 0
        return DocumentState(
            now,
            issuerDocument.state,
            0,
            issuerDocument.simpleCredentialRequests.count()
        )
    }

    @FlowMethod
    suspend fun proof(env: FlowEnvironment, documentId: String): ProofingState {
        val issuerDocument = loadIssuerDocument(env, documentId)
        issuerDocument.state = DocumentCondition.PROOFING_PROCESSING
        issuerDocument.collectedEvidence.clear()
        // TODO: propagate developer mode
        val settings = WalletServerSettings(env.getInterface(Configuration::class)!!)
        return ProofingState(documentId, authorityId, settings.developerMode)
    }

    @FlowJoin
    suspend fun completeProof(env: FlowEnvironment, state: ProofingState) {
        val issuerDocument = loadIssuerDocument(env, state.documentId)
        issuerDocument.state = DocumentCondition.PROOFING_PROCESSING
        issuerDocument.collectedEvidence.putAll(state.evidence)
        updateIssuerDocument(env, state.documentId, issuerDocument)
    }

    @FlowMethod
    suspend fun getDocumentConfiguration(env: FlowEnvironment, documentId: String): DocumentConfiguration {
        val issuerDocument = loadIssuerDocument(env, documentId)
        check(issuerDocument.state == DocumentCondition.CONFIGURATION_AVAILABLE)
        issuerDocument.state = DocumentCondition.READY
        if (issuerDocument.documentConfiguration == null) {
            issuerDocument.documentConfiguration =
                generateDocumentConfiguration(env, issuerDocument.collectedEvidence)
        }
        updateIssuerDocument(env, documentId, issuerDocument)
        return issuerDocument.documentConfiguration!!
    }

    @FlowMethod
    suspend fun requestCredentials(env: FlowEnvironment, documentId: String): RequestCredentialsState {
        val issuerDocument = loadIssuerDocument(env, documentId)
        check(issuerDocument.state == DocumentCondition.READY)

        val credentialConfiguration = defaultCredentialConfiguration(issuerDocument.collectedEvidence)
        return RequestCredentialsState(
            documentId,
            credentialConfiguration)
    }

    @FlowJoin
    suspend fun completeRequestCredentials(env: FlowEnvironment, state: RequestCredentialsState) {
        val now = Clock.System.now()

        val issuerDocument = loadIssuerDocument(env, state.documentId)
        for (request in state.credentialRequests) {
            // Skip if we already have a request for the authentication key
            if (hasCpoRequestForAuthenticationKey(issuerDocument,
                    request.secureAreaBoundKeyAttestation.publicKey)) {
                continue
            }
            val authenticationKey = request.secureAreaBoundKeyAttestation.publicKey
            val presentationData = createPresentationData(
                env,
                issuerDocument.documentConfiguration!!,
                authenticationKey
            )
            val simpleCredentialRequest = SimpleCredentialRequest(
                authenticationKey,
                CredentialFormat.MDOC_MSO,
                presentationData,
            )
            issuerDocument.simpleCredentialRequests.add(simpleCredentialRequest)
        }
        updateIssuerDocument(env, state.documentId, issuerDocument)
    }

    @FlowMethod
    suspend fun getCredentials(env: FlowEnvironment, documentId: String): List<CredentialData> {
        val now = Clock.System.now()
        val validFrom = now
        val validUntil = now + 30.days
        val issuerDocument = loadIssuerDocument(env, documentId)
        val availableCPOs = mutableListOf<CredentialData>()
        for (cpoRequest in issuerDocument.simpleCredentialRequests) {
            availableCPOs.add(
                CredentialData(
                    cpoRequest.authenticationKey,
                    validFrom,
                    validUntil,
                    cpoRequest.format,
                    cpoRequest.data,
                )
            )
        }
        issuerDocument.simpleCredentialRequests.clear()
        updateIssuerDocument(env, documentId, issuerDocument)
        return availableCPOs
    }

    @FlowMethod
    suspend fun developerModeRequestUpdate(
        env: FlowEnvironment,
        documentId: String,
        requestRemoteDeletion: Boolean,
        notifyApplicationOfUpdate: Boolean
    ) {
        val settings = WalletServerSettings(env.getInterface(Configuration::class)!!)
        val prefix = "issuingAuthority.$authorityId"
        val type = settings.getString("$prefix.type") ?: TYPE_DRIVING_LICENSE

        if (!settings.developerMode) {
            throw IllegalStateException("Must be in developer mode for this feature to work")
        }

        if (requestRemoteDeletion) {
            deleteIssuerDocument(env, documentId, notifyApplicationOfUpdate)
        } else {
            val issuerDocument = loadIssuerDocument(env, documentId)
            issuerDocument.state = DocumentCondition.CONFIGURATION_AVAILABLE

            // The update consists of just slapping an extra 0 at the end of `administrative_number`
            val namespace = if (type == TYPE_EU_PID) EUPID_NAMESPACE else MDL_NAMESPACE
            val newAdministrativeNumber = try {
                issuerDocument.documentConfiguration!!.mdocConfiguration!!.staticData
                    .getDataElementString(namespace, "administrative_number")
            } catch (e: Throwable) {
                ""
            } + "0"

            val builder = NameSpacedData.Builder(
                issuerDocument.documentConfiguration!!.mdocConfiguration!!.staticData
            )
            builder.putEntryString(
                namespace,
                "administrative_number",
                newAdministrativeNumber
            )

            issuerDocument.documentConfiguration = DocumentConfiguration(
                issuerDocument.documentConfiguration!!.displayName,
                issuerDocument.documentConfiguration!!.typeDisplayName,
                issuerDocument.documentConfiguration!!.cardArt,
                issuerDocument.documentConfiguration!!.requireUserAuthenticationToViewDocument,
                MdocDocumentConfiguration(
                    issuerDocument.documentConfiguration!!.mdocConfiguration!!.docType,
                    builder.build()
                ),
                issuerDocument.documentConfiguration!!.sdJwtVcDocumentConfiguration,
            )
            updateIssuerDocument(env, documentId, issuerDocument, notifyApplicationOfUpdate)
        }
    }

    suspend fun administrativeActionUpdateAdministrativeNumber(
        env: FlowEnvironment,
        documentId: String,
        administrativeNumber: String
    ) {
        val settings = WalletServerSettings(env.getInterface(Configuration::class)!!)
        val prefix = "issuingAuthority.$authorityId"
        val type = settings.getString("$prefix.type") ?: TYPE_DRIVING_LICENSE

        val issuerDocument = loadIssuerDocument(env, documentId)
        issuerDocument.state = DocumentCondition.CONFIGURATION_AVAILABLE

        val builder = NameSpacedData.Builder(
            issuerDocument.documentConfiguration!!.mdocConfiguration!!.staticData
        )
        val namespace = if (type == TYPE_EU_PID) EUPID_NAMESPACE else MDL_NAMESPACE
        builder.putEntryString(
            namespace,
            "administrative_number",
            administrativeNumber
        )
        issuerDocument.documentConfiguration = DocumentConfiguration(
            issuerDocument.documentConfiguration!!.displayName,
            issuerDocument.documentConfiguration!!.typeDisplayName,
            issuerDocument.documentConfiguration!!.cardArt,
            issuerDocument.documentConfiguration!!.requireUserAuthenticationToViewDocument,
            MdocDocumentConfiguration(
                issuerDocument.documentConfiguration!!.mdocConfiguration!!.docType,
                builder.build()
            ),
            issuerDocument.documentConfiguration!!.sdJwtVcDocumentConfiguration,
        )
        updateIssuerDocument(env, documentId, issuerDocument, true)
    }

    private fun createPresentationData(
        env: FlowEnvironment,
        documentConfiguration: DocumentConfiguration,
        authenticationKey: EcPublicKey
    ): ByteArray {
        val now = Clock.System.now()

        val settings = WalletServerSettings(env.getInterface(Configuration::class)!!)
        val prefix = "issuingAuthority.$authorityId"
        val type = settings.getString("$prefix.type") ?: TYPE_DRIVING_LICENSE

        // Create AuthKeys and MSOs, make sure they're valid for a long time
        val timeSigned = now
        val validFrom = now
        val validUntil = Instant.fromEpochMilliseconds(validFrom.toEpochMilliseconds() + 365*24*3600*1000L)

        // Generate an MSO and issuer-signed data for this authentication key.
        val docType = if (type == TYPE_EU_PID) EUPID_DOCTYPE else MDL_DOCTYPE
        val msoGenerator = MobileSecurityObjectGenerator(
            "SHA-256",
            docType,
            authenticationKey
        )
        msoGenerator.setValidityInfo(timeSigned, validFrom, validUntil, null)
        val randomProvider = Random.Default
        val issuerNameSpaces = MdocUtil.generateIssuerNameSpaces(
            documentConfiguration.mdocConfiguration!!.staticData,
            randomProvider,
            16,
            null
        )
        for (nameSpaceName in issuerNameSpaces.keys) {
            val digests = MdocUtil.calculateDigestsForNameSpace(
                nameSpaceName,
                issuerNameSpaces,
                Algorithm.SHA256
            )
            msoGenerator.addDigestIdsForNamespace(nameSpaceName, digests)
        }

        val resources = env.getInterface(Resources::class)!!
        val documentSigningKeyCert = X509Certificate.fromPem(
            resources.getStringResource("ds_certificate.pem")!!)
        val documentSigningKey = EcPrivateKey.fromPem(
            resources.getStringResource("ds_private_key.pem")!!,
            documentSigningKeyCert.ecPublicKey
        )

        val mso = msoGenerator.generate()
        val taggedEncodedMso = Cbor.encode(Tagged(Tagged.ENCODED_CBOR, Bstr(mso)))
        val protectedHeaders = mapOf<CoseLabel, DataItem>(Pair(
            CoseNumberLabel(Cose.COSE_LABEL_ALG),
            Algorithm.ES256.coseAlgorithmIdentifier.toDataItem
        ))
        val unprotectedHeaders = mapOf<CoseLabel, DataItem>(Pair(
            CoseNumberLabel(Cose.COSE_LABEL_X5CHAIN),
            X509CertificateChain(listOf(
                X509Certificate(documentSigningKeyCert.encodedCertificate))
            ).toDataItem
        ))
        val encodedIssuerAuth = Cbor.encode(
            Cose.coseSign1Sign(
                documentSigningKey,
                taggedEncodedMso,
                true,
                Algorithm.ES256,
                protectedHeaders,
                unprotectedHeaders
            ).toDataItem
        )

        val issuerProvidedAuthenticationData = StaticAuthDataGenerator(
            issuerNameSpaces,
            encodedIssuerAuth
        ).generate()

        return issuerProvidedAuthenticationData
    }

    private fun generateDocumentConfiguration(
        env: FlowEnvironment,
        collectedEvidence: Map<String, EvidenceResponse>
    ): DocumentConfiguration {
        val settings = WalletServerSettings(env.getInterface(Configuration::class)!!)
        val prefix = "issuingAuthority.$authorityId"
        val type = settings.getString("$prefix.type") ?: TYPE_DRIVING_LICENSE
        return if (type == TYPE_EU_PID) {
            generateEuPidDocumentConfiguration(env, collectedEvidence)
        } else {
            generateMdlDocumentConfiguration(env, collectedEvidence)
        }
    }

    private fun generateEuPidDocumentConfiguration(
        env: FlowEnvironment,
        collectedEvidence: Map<String, EvidenceResponse>
    ): DocumentConfiguration {
        val now = Clock.System.now()
        val issueDate = now
        val resources = env.getInterface(Resources::class)!!
        val settings = WalletServerSettings(env.getInterface(Configuration::class)!!)
        val expiryDate = now + 365.days * 5

        val prefix = "issuingAuthority.$authorityId"
        val artPath = settings.getString("${prefix}.cardArt") ?: "default/card_art.png"
        val issuingAuthorityName = settings.getString("${prefix}.name") ?: "Default Issuer"
        val art = resources.getRawResource(artPath)!!

        val credType = documentTypeRepository.getDocumentTypeForMdoc(EUPID_DOCTYPE)!!
        val staticData: NameSpacedData

        val path = (collectedEvidence["path"] as EvidenceResponseQuestionMultipleChoice).answerId
        if (path == "hardcoded") {
            val imageFormat = collectedEvidence["devmode_image_format"]
            val jpeg2k = imageFormat is EvidenceResponseQuestionMultipleChoice &&
                    imageFormat.answerId == "devmode_image_format_jpeg2000"
            staticData = fillInSampleData(resources, jpeg2k, credType).build()
        } else { // todo
            val icaoPassiveData = collectedEvidence["passive"]
            val icaoTunnelData = collectedEvidence["tunnel"]
            val mrtdData = if (icaoTunnelData is EvidenceResponseIcaoNfcTunnelResult)
                MrtdNfcData(icaoTunnelData.dataGroups, icaoTunnelData.securityObject)
            else if (icaoPassiveData is EvidenceResponseIcaoPassiveAuthentication)
                MrtdNfcData(icaoPassiveData.dataGroups, icaoPassiveData.securityObject)
            else
                throw IllegalStateException("Should not happen")
            val decoded = MrtdNfcDataDecoder().decode(mrtdData)
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
                .putEntryString(EUPID_NAMESPACE, "family_name", lastName)
                .putEntryString(EUPID_NAMESPACE, "given_name", firstName)
                .putEntry(EUPID_NAMESPACE, "birth_date",
                    Cbor.encode(dateOfBirth.toDataItemFullDate))
                .putEntryNumber(EUPID_NAMESPACE, "age_in_years", dateOfBirth.yearsUntil(now.toLocalDateTime(timeZone).date).toLong())
                .putEntryNumber(EUPID_NAMESPACE, "age_birth_year", dateOfBirth.year.toLong())
                .putEntryBoolean(EUPID_NAMESPACE, "age_over_18", ageOver18)
                .putEntryBoolean(EUPID_NAMESPACE, "age_over_21", ageOver21)
                // not included: family_name_birth, given_name_birth, birth_place, birth_country,
                // birth_state, birth_city, resident_address, resident_country, resident_state,
                // resident_city, resident_postal_code, resident_street, resident_house_number,
                // issuing_jurisdiction,
                .putEntryNumber(EUPID_NAMESPACE, "gender", sex)
                .putEntryString(EUPID_NAMESPACE, "nationality", "UT")
                .putEntry(EUPID_NAMESPACE, "issuance_date",
                    Cbor.encode(issueDate.toDataItemDateTimeString))
                .putEntry(EUPID_NAMESPACE, "expiry_date",
                    Cbor.encode(expiryDate.toDataItemDateTimeString)
                )
                .putEntryString(EUPID_NAMESPACE, "issuing_authority",
                    issuingAuthorityName)
                .putEntryString(EUPID_NAMESPACE, "document_number", "1234567890")
                .putEntryString(EUPID_NAMESPACE, "administrative_number", "123456789")
                .putEntryString(EUPID_NAMESPACE, "issuing_country", "UT")
                .build()
        }

        val firstName = staticData.getDataElementString(EUPID_NAMESPACE, "given_name")
        return DocumentConfiguration(
            displayName = "$firstName's Personal ID",
            typeDisplayName = "EU Personal ID",
            cardArt = art.toByteArray(),
            requireUserAuthenticationToViewDocument =
                settings.getBool("${prefix}.requireUserAuthenticationToViewDocument"),
            mdocConfiguration = MdocDocumentConfiguration(
                docType = EUPID_DOCTYPE,
                staticData = staticData,
            ),
            sdJwtVcDocumentConfiguration = null,
        )
    }

    private fun generateMdlDocumentConfiguration(
        env: FlowEnvironment,
        collectedEvidence: Map<String, EvidenceResponse>
    ): DocumentConfiguration {
        val now = Clock.System.now()
        val issueDate = now
        val resources = env.getInterface(Resources::class)!!
        val settings = WalletServerSettings(env.getInterface(Configuration::class)!!)
        val expiryDate = now + 365.days * 5

        val prefix = "issuingAuthority.$authorityId"
        val artPath = settings.getString("${prefix}.cardArt") ?: "default/card_art.png"
        val issuingAuthorityName = settings.getString("${prefix}.name") ?: "Default Issuer"
        val art = resources.getRawResource(artPath)!!

        val credType = documentTypeRepository.getDocumentTypeForMdoc(MDL_DOCTYPE)!!
        val staticData: NameSpacedData

        val path = (collectedEvidence["path"] as EvidenceResponseQuestionMultipleChoice).answerId
        if (path == "hardcoded") {
            val imageFormat = collectedEvidence["devmode_image_format"]
            val jpeg2k = imageFormat is EvidenceResponseQuestionMultipleChoice &&
                    imageFormat.answerId == "devmode_image_format_jpeg2000"
            staticData = fillInSampleData(resources, jpeg2k, credType).build()
        } else {
            val icaoPassiveData = collectedEvidence["passive"]
            val icaoTunnelData = collectedEvidence["tunnel"]
            val mrtdData = if (icaoTunnelData is EvidenceResponseIcaoNfcTunnelResult)
                MrtdNfcData(icaoTunnelData.dataGroups, icaoTunnelData.securityObject)
            else if (icaoPassiveData is EvidenceResponseIcaoPassiveAuthentication)
                MrtdNfcData(icaoPassiveData.dataGroups, icaoPassiveData.securityObject)
            else
                throw IllegalStateException("Should not happen")
            val decoded = MrtdNfcDataDecoder().decode(mrtdData)
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
            val portrait = decoded.photo ?: resources.getRawResource("img_erika_portrait.jpg")!!
            val signatureOrUsualMark = decoded.signature ?: resources.getRawResource("img_erika_signature.jpg")!!

            // Make sure we set at least all the mandatory data elements
            //
            staticData = NameSpacedData.Builder()
                .putEntryString(MDL_NAMESPACE, "given_name", firstName)
                .putEntryString(MDL_NAMESPACE, "family_name", lastName)
                .putEntry(MDL_NAMESPACE, "birth_date",
                    Cbor.encode(dateOfBirth.toDataItemFullDate))
                .putEntryByteString(MDL_NAMESPACE, "portrait", portrait.toByteArray())
                .putEntryByteString(MDL_NAMESPACE, "signature_usual_mark",
                    signatureOrUsualMark.toByteArray())
                .putEntryNumber(MDL_NAMESPACE, "sex", sex)
                .putEntry(MDL_NAMESPACE, "issue_date",
                    Cbor.encode(issueDate.toDataItemDateTimeString))
                .putEntry(MDL_NAMESPACE, "expiry_date",
                    Cbor.encode(expiryDate.toDataItemDateTimeString)
                )
                // TODO
                .putEntryString(MDL_NAMESPACE, "issuing_authority",
                    issuingAuthorityName)
                .putEntryString(MDL_NAMESPACE, "issuing_country", "UT")
                .putEntryString(MDL_NAMESPACE, "un_distinguishing_sign", "UTO")
                .putEntryString(MDL_NAMESPACE, "document_number", "1234567890")
                .putEntryString(MDL_NAMESPACE, "administrative_number", "123456789")
                .putEntry(MDL_NAMESPACE, "driving_privileges",
                    Cbor.encode(CborArray.builder().end().build()))

                .putEntryBoolean(MDL_NAMESPACE, "age_over_18", ageOver18)
                .putEntryBoolean(MDL_NAMESPACE, "age_over_21", ageOver21)

                .putEntryString(AAMVA_NAMESPACE, "DHS_compliance", "F")
                .putEntryNumber(AAMVA_NAMESPACE, "EDL_credential", 1)
                .putEntryNumber(AAMVA_NAMESPACE, "sex", sex)
                .build()
        }

        val firstName = staticData.getDataElementString(MDL_NAMESPACE, "given_name")
        return DocumentConfiguration(
            displayName = "$firstName's Driving License",
            typeDisplayName = "Driving License",
            cardArt = art.toByteArray(),
            requireUserAuthenticationToViewDocument =
                settings.getBool("${prefix}.requireUserAuthenticationToViewDocument"),
            mdocConfiguration = MdocDocumentConfiguration(
                docType = MDL_DOCTYPE,
                staticData = staticData,
            ),
            sdJwtVcDocumentConfiguration = null,
        )
    }

    private fun fillInSampleData(
        resources: Resources,
        jpeg2k: Boolean,
        documentType: DocumentType
    ): NameSpacedData.Builder {
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

        if (documentType == documentTypeRepository.getDocumentTypeForMdoc(MDL_DOCTYPE)!!) {
            val portrait = resources.getRawResource(if (jpeg2k) {
                "img_erika_portrait.jpf"
            } else {
                "img_erika_portrait.jpg"
            })!!.toByteArray()
            val signatureOrUsualMark = resources.getRawResource(if (jpeg2k) {
                "img_erika_signature.jpf"
            } else {
                "img_erika_signature.jpg"
            })!!.toByteArray()
            builder
                .putEntryByteString(MDL_NAMESPACE, "portrait", portrait)
                .putEntryByteString(MDL_NAMESPACE, "signature_usual_mark", signatureOrUsualMark)
        }

        return builder
    }

    private fun checkEvidence(evidence: Map<String, EvidenceResponse>): Boolean {
        return true
    }

    private fun hasCpoRequestForAuthenticationKey(
        issuerDocument: IssuerDocument,
        authenticationKey: EcPublicKey
    ) : Boolean {
        for (cpoRequest in issuerDocument.simpleCredentialRequests) {
            if (cpoRequest.authenticationKey.equals(authenticationKey)) {
                return true
            }
        }
        return false
    }

    private suspend fun issuerDocumentExists(env: FlowEnvironment, documentId: String): Boolean {
        if (clientId.isEmpty()) {
            throw IllegalStateException("Client not authenticated")
        }
        val storage = env.getInterface(Storage::class)!!
        val encodedCbor = storage.get("IssuerDocument", clientId, documentId)
        if (encodedCbor != null) {
            return true
        }
        return false
    }

    private suspend fun loadIssuerDocument(env: FlowEnvironment, documentId: String): IssuerDocument {
        if (clientId.isEmpty()) {
            throw IllegalStateException("Client not authenticated")
        }
        val storage = env.getInterface(Storage::class)!!
        val encodedCbor = storage.get("IssuerDocument", clientId, documentId)
        if (encodedCbor == null) {
            // TODO: We need to figure out if we need to support throwing exceptions across
            //  the network. For example we would throw UnknownDocumentException here if this
            //  was supported by the Flow library/processor.
            throw Error("No such document")
        }
        return IssuerDocument.fromDataItem(Cbor.decode(encodedCbor.toByteArray()))
    }

    private suspend fun createIssuerDocument(env: FlowEnvironment, document: IssuerDocument): String {
        if (clientId.isEmpty()) {
            throw IllegalStateException("Client not authenticated")
        }
        val storage = env.getInterface(Storage::class)!!
        val bytes = Cbor.encode(document.toDataItem)
        return storage.insert("IssuerDocument", clientId, ByteString(bytes))
    }

    private suspend fun deleteIssuerDocument(env: FlowEnvironment,
                                             documentId: String,
                                             emitNotification: Boolean = true) {
        if (clientId.isEmpty()) {
            throw IllegalStateException("Client not authenticated")
        }
        val storage = env.getInterface(Storage::class)!!
        storage.delete("IssuerDocument", clientId, documentId)
        if (emitNotification) {
            emit(env, IssuingAuthorityNotification(documentId))
        }
    }

    private suspend fun updateIssuerDocument(
        env: FlowEnvironment,
        documentId: String,
        document: IssuerDocument,
        emitNotification: Boolean = true,
    ) {
        if (clientId.isEmpty()) {
            throw IllegalStateException("Client not authenticated")
        }
        val storage = env.getInterface(Storage::class)!!
        val bytes = Cbor.encode(document.toDataItem)
        storage.update("IssuerDocument", clientId, documentId, ByteString(bytes))
        if (emitNotification) {
            emit(env, IssuingAuthorityNotification(documentId))
        }
    }
}
