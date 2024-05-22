package com.android.identity.issuance.hardcoded

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborMap
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
import com.android.identity.crypto.Certificate
import com.android.identity.crypto.CertificateChain
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPublicKey
import com.android.identity.document.NameSpacedData
import com.android.identity.documenttype.knowntypes.DrivingLicense
import com.android.identity.flow.annotation.FlowJoin
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.environment.Configuration
import com.android.identity.flow.environment.Resources
import com.android.identity.flow.environment.Storage
import com.android.identity.flow.environment.FlowEnvironment
import com.android.identity.issuance.CredentialConfiguration
import com.android.identity.issuance.CredentialData
import com.android.identity.issuance.CredentialFormat
import com.android.identity.issuance.DocumentCondition
import com.android.identity.issuance.DocumentConfiguration
import com.android.identity.issuance.DocumentState
import com.android.identity.issuance.IssuingAuthority
import com.android.identity.issuance.MdocDocumentConfiguration
import com.android.identity.issuance.RegistrationResponse
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseQuestionString
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator
import com.android.identity.mdoc.mso.StaticAuthDataGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.securearea.KeyPurpose
import com.android.identity.util.Timestamp
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.bytestring.ByteString
import java.util.UUID
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

private const val MDL_DOCTYPE = DrivingLicense.MDL_DOCTYPE
private const val MDL_NAMESPACE = DrivingLicense.MDL_NAMESPACE
private const val AAMVA_NAMESPACE = DrivingLicense.AAMVA_NAMESPACE

/**
 * State of [IssuingAuthority] RPC implementation.
 */
@FlowState(
    flowInterface = IssuingAuthority::class
)
@CborSerializable
class IssuingAuthorityState(
    val clientId: String = "",
    val authorityId: String = "",
    // Only needed when Storage interface is not available in environment
    val documents: MutableMap<String, IssuerDocument> = mutableMapOf()
) {
    companion object

    @FlowMethod
    suspend fun register(env: FlowEnvironment): RegistrationState {
        val documentId = createIssuerDocument(env,
            IssuerDocument(
                RegistrationResponse(false),
                Instant.fromEpochMilliseconds(0),
                DocumentCondition.PROOFING_REQUIRED,
                mutableMapOf(),
                null,
                mutableListOf()
            )
        )
        return RegistrationState(documentId)
    }

    @FlowJoin
    suspend fun completeRegistration(env: FlowEnvironment, registrationState: RegistrationState) {
        updateIssuerDocument(
            env,
            registrationState.documentId,
            IssuerDocument(
                registrationState.response!!,
                Instant.fromEpochMilliseconds(0),
                DocumentCondition.PROOFING_REQUIRED,
                mutableMapOf(),           // collectedEvidence - initially empty
                null,  // no initial document configuration
                mutableListOf()           // cpoRequests - initially empty
            ))
    }

    @FlowMethod
    suspend fun getState(env: FlowEnvironment, documentId: String): DocumentState {
        val now = Clock.System.now()

        val issuerDocument = loadIssuerDocument(env, documentId)

        // Evaluate proofing, if deadline has passed...
        if (issuerDocument.state == DocumentCondition.PROOFING_PROCESSING) {
            if (now >= issuerDocument.proofingDeadline) {
                issuerDocument.state =
                    if (checkEvidence(issuerDocument.collectedEvidence)) {
                        DocumentCondition.CONFIGURATION_AVAILABLE
                    } else {
                        DocumentCondition.PROOFING_FAILED
                    }
                updateIssuerDocument(env, documentId, issuerDocument)
            }
        }

        // Calculate pending/available depending on deadline
        var numPendingCredentialRequests = 0
        var numAvailableCredentialRequests = 0
        for (cpoRequest in issuerDocument.simpleCredentialRequests) {
            if (now >= cpoRequest.deadline) {
                numAvailableCredentialRequests += 1
            } else {
                numPendingCredentialRequests += 1
            }
        }
        return DocumentState(
            now,
            issuerDocument.state,
            numPendingCredentialRequests,
            numAvailableCredentialRequests
        )
    }

    @FlowMethod
    suspend fun proof(env: FlowEnvironment, documentId: String): ProofingState {
        val issuerDocument = loadIssuerDocument(env, documentId)
        issuerDocument.state = DocumentCondition.PROOFING_PROCESSING
        issuerDocument.collectedEvidence.clear()
        return ProofingState(documentId)
    }

    @FlowJoin
    suspend fun completeProof(env: FlowEnvironment, state: ProofingState) {
        val issuerDocument = loadIssuerDocument(env, state.documentId)
        issuerDocument.state = DocumentCondition.PROOFING_PROCESSING
        issuerDocument.collectedEvidence.putAll(state.evidence)
        issuerDocument.proofingDeadline = Clock.System.now()  // could add a delay
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

        val credentialConfiguration = createCredentialConfiguration(issuerDocument.collectedEvidence)
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
                    request.secureAreaBoundKeyAttestation.certificates.first().publicKey)) {
                continue
            }
            val authenticationKey = request.secureAreaBoundKeyAttestation.certificates.first().publicKey
            val presentationData = createPresentationData(
                env,
                issuerDocument.documentConfiguration!!,
                authenticationKey
            )
            val simpleCredentialRequest = SimpleCredentialRequest(
                authenticationKey,
                CredentialFormat.MDOC_MSO,
                presentationData,
                now,
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
        val pendingCPOs = mutableListOf<SimpleCredentialRequest>()
        for (cpoRequest in issuerDocument.simpleCredentialRequests) {
            if (now >= cpoRequest.deadline) {
                availableCPOs.add(
                    CredentialData(
                        cpoRequest.authenticationKey,
                        validFrom,
                        validUntil,
                        cpoRequest.format,
                        cpoRequest.data,
                    )
                )
            } else {
                pendingCPOs.add(cpoRequest)
            }
        }
        issuerDocument.simpleCredentialRequests = pendingCPOs
        updateIssuerDocument(env, documentId, issuerDocument)
        return availableCPOs
    }

    @FlowMethod
    fun developerModeRequestUpdate(
        env: FlowEnvironment,
        documentId: String,
        requestRemoteDeletion: Boolean,
        notifyApplicationOfUpdate: Boolean
    ) {
        // TODO: implement this
    }


    private fun createPresentationData(
        env: FlowEnvironment,
        documentConfiguration: DocumentConfiguration,
        authenticationKey: EcPublicKey
    ): ByteArray {
        val now = Timestamp.now()

        // Create AuthKeys and MSOs, make sure they're valid for a long time
        val timeSigned = now
        val validFrom = now
        val validUntil = Timestamp.ofEpochMilli(validFrom.toEpochMilli() + 365*24*3600*1000L)

        // Generate an MSO and issuer-signed data for this authentication key.
        val msoGenerator = MobileSecurityObjectGenerator(
            "SHA-256",
            MDL_DOCTYPE,
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
        val documentSigningKeyCert = Certificate.fromPem(
            resources.getStringResource("ds_certificate.pem")!!)
        val documentSigningKey = EcPrivateKey.fromPem(
            resources.getStringResource("ds_private_key.pem")!!,
            documentSigningKeyCert.publicKey
        )

        val mso = msoGenerator.generate()
        val taggedEncodedMso = Cbor.encode(Tagged(Tagged.ENCODED_CBOR, Bstr(mso)))
        val protectedHeaders = mapOf<CoseLabel, DataItem>(Pair(
            CoseNumberLabel(Cose.COSE_LABEL_ALG),
            Algorithm.ES256.coseAlgorithmIdentifier.toDataItem
        ))
        val unprotectedHeaders = mapOf<CoseLabel, DataItem>(Pair(
            CoseNumberLabel(Cose.COSE_LABEL_X5CHAIN),
            CertificateChain(listOf(Certificate(documentSigningKeyCert.encodedCertificate))).toDataItem
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

    private fun createCredentialConfiguration(collectedEvidence: Map<String, EvidenceResponse>): CredentialConfiguration {
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

    private fun generateDocumentConfiguration(
        env: FlowEnvironment,
        collectedEvidence: Map<String, EvidenceResponse>
    ): DocumentConfiguration {
        val firstName = (collectedEvidence["first_name"] as EvidenceResponseQuestionString).answer
        val now = Clock.System.now()
        val timeZone = TimeZone.currentSystemDefault()
        val issueDate = now
        val validUntil = now.plus(365 * 24, DateTimeUnit.HOUR)

        val dateOfBirth = LocalDate.parse(input = "900704",
            format = LocalDate.Format {
                // date of birth cannot be in future
                yearTwoDigits(baseYear = now.toLocalDateTime(timeZone).year - 99)
                monthNumber()
                dayOfMonth()
            })
        val dateOfBirthInstant = dateOfBirth.atStartOfDayIn(timeZone)
        // over 18/21 is calculated purely based on calendar date (not based on the birth time zone)
        val ageOver18 = now > dateOfBirthInstant.plus(18, DateTimeUnit.YEAR, timeZone)
        val ageOver21 = now > dateOfBirthInstant.plus(21, DateTimeUnit.YEAR, timeZone)

        val resources = env.getInterface(Resources::class)!!
        val portrait = resources.getRawResource("img_erika_portrait.jpf")!!
        val signature = resources.getRawResource("img_erika_signature.jpf")!!
        val prefix = "issuing_authorities.$authorityId"
        val configuration = env.getInterface(Configuration::class)
        val artPath = configuration?.getProperty("$prefix.card_art") ?: "default/card_art.png"
        val art = resources.getRawResource(artPath)!!

        val data = NameSpacedData.Builder()
            .putEntryString(MDL_NAMESPACE, "given_name", firstName)
            .putEntryString(MDL_NAMESPACE, "family_name", "Nuller")
            .putEntry(MDL_NAMESPACE, "birth_date",
                Cbor.encode(dateOfBirth.toDataItemFullDate))
            .putEntryByteString(MDL_NAMESPACE, "portrait", portrait.toByteArray())
            .putEntryByteString(MDL_NAMESPACE, "signature_usual_mark", signature.toByteArray())
            .putEntryNumber(MDL_NAMESPACE, "sex", 2L)
            .putEntry(MDL_NAMESPACE, "issue_date",
                Cbor.encode(issueDate.toDataItemDateTimeString))
            .putEntry(MDL_NAMESPACE, "expiry_date",
                Cbor.encode(validUntil.toDataItemDateTimeString)
            )
            .putEntryString(MDL_NAMESPACE, "issuing_authority",
                "Elbonia DMV")
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
            .putEntryNumber(AAMVA_NAMESPACE, "sex", 2L)

        return DocumentConfiguration(
            displayName = "$firstName's Driving License",
            typeDisplayName = "Driving License",
            cardArt = art.toByteArray(),
            requireUserAuthenticationToViewDocument = true,
            mdocConfiguration = MdocDocumentConfiguration(
                docType = MDL_DOCTYPE,
                staticData = data.build(),
            ),
            sdJwtVcDocumentConfiguration = null,
        )
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

    private suspend fun loadIssuerDocument(env: FlowEnvironment, documentId: String): IssuerDocument {
        if (clientId.isEmpty()) {
            throw IllegalStateException("Client not authenticated")
        }
        val storage = env.getInterface(Storage::class)
        if (storage == null) {
            return documents[documentId]!!
        } else {
            val encodedCbor = storage.get("IssuerDocument", clientId, documentId)!!
            return IssuerDocument.fromDataItem(Cbor.decode(encodedCbor.toByteArray()))
        }
    }

    private suspend fun createIssuerDocument(env: FlowEnvironment, document: IssuerDocument): String {
        if (clientId.isEmpty()) {
            throw IllegalStateException("Client not authenticated")
        }
        val storage = env.getInterface(Storage::class)
        if (storage == null) {
            val documentId = UUID.randomUUID().toString()
            check(!documents.containsKey(documentId))
            documents[documentId] = document
            return documentId
        } else {
            val bytes = Cbor.encode(document.toDataItem)
            return storage.insert("IssuerDocument", clientId, ByteString(bytes))
        }
    }

    private suspend fun updateIssuerDocument(env: FlowEnvironment, documentId: String, document: IssuerDocument) {
        if (clientId.isEmpty()) {
            throw IllegalStateException("Client not authenticated")
        }
        val storage = env.getInterface(Storage::class)
        if (storage == null) {
            check(documents.containsKey(documentId))
            documents[documentId] = document
        } else {
            val bytes = Cbor.encode(document.toDataItem)
            storage.update("IssuerDocument", clientId, documentId, ByteString(bytes))
        }
    }
}