package org.multipaz.testapp.provisioning.openid4vci

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.crypto.EcPublicKey
import org.multipaz.document.NameSpacedData
import org.multipaz.documenttype.DocumentType
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.documenttype.knowntypes.EUCertificateOfResidence
import org.multipaz.documenttype.knowntypes.EUPersonalID
import org.multipaz.documenttype.knowntypes.PhotoID
import org.multipaz.documenttype.knowntypes.UtopiaMovieTicket
import org.multipaz.documenttype.knowntypes.UtopiaNaturalization
import org.multipaz.rpc.annotation.RpcState
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.provisioning.ApplicationSupport
import org.multipaz.provisioning.CredentialConfiguration
import org.multipaz.provisioning.CredentialData
import org.multipaz.provisioning.CredentialFormat
import org.multipaz.provisioning.DocumentCondition
import org.multipaz.provisioning.DocumentConfiguration
import org.multipaz.provisioning.DocumentState
import org.multipaz.provisioning.IssuingAuthority
import org.multipaz.provisioning.IssuingAuthorityConfiguration
import org.multipaz.provisioning.IssuingAuthorityException
import org.multipaz.provisioning.IssuingAuthorityNotification
import org.multipaz.provisioning.MdocDocumentConfiguration
import org.multipaz.provisioning.RegistrationResponse
import org.multipaz.provisioning.SdJwtVcDocumentConfiguration
import org.multipaz.securearea.config.SecureAreaConfigurationAndroidKeystore
import org.multipaz.securearea.config.SecureAreaConfigurationCloud
import org.multipaz.rpc.backend.getTable
import org.multipaz.mdoc.mso.MobileSecurityObjectParser
import org.multipaz.mdoc.mso.StaticAuthDataParser
import org.multipaz.storage.StorageTableSpec
import org.multipaz.util.Logger
import org.multipaz.util.fromBase64Url
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.multipaz.crypto.Algorithm
import org.multipaz.provisioning.Proofing
import org.multipaz.provisioning.Registration
import org.multipaz.provisioning.RequestCredentials
import org.multipaz.rpc.backend.RpcAuthBackendDelegate
import org.multipaz.rpc.handler.RpcAuthContext
import org.multipaz.rpc.handler.RpcAuthInspector
import org.multipaz.sdjwt.SdJwt
import kotlin.time.Duration.Companion.seconds

@RpcState(endpoint = "openid4vci")
@CborSerializable
class Openid4VciIssuingAuthorityState(
    // NB: since this object is used to emit notifications, it cannot change, as its state
    // serves as notification key. So it generally should not have var members, only val.
    // This is also a reason to keep clientId here (so that different clients get different keys)
    val clientId: String,
    val credentialIssuerUri: String, // credential offer issuing authority path
    val credentialConfigurationId: String,
    val issuanceClientId: String // client id in OpenID4VCI protocol,
) : IssuingAuthority, RpcAuthInspector by RpcAuthBackendDelegate {

    init {
        // It should not be possible, but double-check.
        check(credentialIssuerUri.indexOf('#') < 0)
        check(credentialConfigurationId.indexOf('#') < 0)
    }

    companion object {
        private const val TAG = "Openid4VciIssuingAuthorityState"

        val documentTableSpec = StorageTableSpec(
            name = "Openid4VciIssuerDocument",
            supportExpiration = false,
            supportPartitions = true
        )

        suspend fun getConfiguration(
            issuerUrl: String,
            credentialConfigurationId: String
        ): IssuingAuthorityConfiguration {
            val id = "openid4vci#$issuerUrl#$credentialConfigurationId"
            return BackendEnvironment.cache(IssuingAuthorityConfiguration::class, id) { _, resources ->
                val metadata = Openid4VciIssuerMetadata.get(issuerUrl)
                val config = metadata.credentialConfigurations[credentialConfigurationId]
                    ?: throw IllegalArgumentException("Unknown configuration '$credentialConfigurationId' at '$issuerUrl'")
                if (!config.isSupported) {
                    throw IllegalArgumentException("Unsupported configuration '$credentialConfigurationId' at '$issuerUrl'")
                }
                val httpClient = BackendEnvironment.getInterface(HttpClient::class)!!
                val display = if (metadata.display.isEmpty()) null else metadata.display[0]
                val configDisplay = if (config.display.isEmpty()) null else config.display[0]
                var logo: ByteArray? = null
                if (display?.logoUrl != null) {
                    val logoRequest = httpClient.get(display.logoUrl) {}
                    if (logoRequest.status == HttpStatusCode.OK &&
                        logoRequest.contentType()?.contentType == "image") {
                        logo = logoRequest.readBytes()
                    } else {
                        Logger.e(TAG, "Could not fetch logo from '${display.logoUrl}")
                    }
                }

                val docType = documentTypeRepository.documentTypes.firstOrNull { documentType ->
                    return@firstOrNull when (config.format) {
                        is Openid4VciFormatMdoc ->
                            documentType.mdocDocumentType?.docType == config.format.docType
                        is Openid4VciFormatSdJwt ->
                            documentType.jsonDocumentType?.vct == config.format.vct
                        null -> false
                    }
                }

                val documentType = docType?.displayName ?: "Generic EAA"
                val documentDescription = configDisplay?.text ?: documentType
                val issuingAuthorityDescription = "$documentDescription (${config.format?.id})"

                var cardArt: ByteArray? = null
                if (configDisplay?.logoUrl != null) {
                    val artRequest = httpClient.get(configDisplay.logoUrl) {}
                    if (artRequest.status == HttpStatusCode.OK &&
                        artRequest.contentType()?.contentType == "image") {
                        cardArt = artRequest.readBytes()
                    } else {
                        Logger.e(TAG,
                            "Could not fetch credential image from '${configDisplay.logoUrl}")
                    }
                }
                if (logo == null) {
                    val artPath = "generic/logo.png"
                    logo = resources.getRawResource(artPath)!!.toByteArray()
                }
                if (cardArt == null) {
                    val artPath = "generic/card_art.png"
                    cardArt = resources.getRawResource(artPath)!!.toByteArray()
                }
                val requireUserAuthenticationToViewDocument = false
                val keyAttestation = config.proofType is Openid4VciProofTypeKeyAttestation
                IssuingAuthorityConfiguration(
                    identifier = id,
                    issuingAuthorityName = display?.text ?: issuerUrl,
                    issuingAuthorityLogo = logo,
                    issuingAuthorityDescription = issuingAuthorityDescription,
                    pendingDocumentInformation = DocumentConfiguration(
                        displayName = documentDescription,
                        typeDisplayName = documentType,
                        cardArt = cardArt,
                        requireUserAuthenticationToViewDocument = requireUserAuthenticationToViewDocument,
                        mdocConfiguration = null,
                        sdJwtVcDocumentConfiguration = null,
                        directAccessConfiguration = null
                    ),
                    // Without key attestation user has to do biometrics approval for every
                    // credential being issued, so request only one. With key attestation we can
                    // use batch issuance (useful for unlinkability).
                    numberOfCredentialsToRequest =
                        if (keyAttestation) { 3 } else { 1 },
                    // With key attestation credentials can be requested in the background as they
                    // are used up, so direct wallet to avoid reusing them (for unlinkability).
                    maxUsesPerCredentials =
                        if (keyAttestation) { 1 } else { Int.MAX_VALUE },
                    minCredentialValidityMillis = 30 * 24 * 3600L,
                )
            }
        }

        val documentTypeRepository = DocumentTypeRepository().apply {
            addDocumentType(EUPersonalID.getDocumentType())
            addDocumentType(DrivingLicense.getDocumentType())
            addDocumentType(PhotoID.getDocumentType())
            addDocumentType(EUCertificateOfResidence.getDocumentType())
            addDocumentType(UtopiaNaturalization.getDocumentType())
            addDocumentType(UtopiaMovieTicket.getDocumentType())
        }
    }

    override suspend fun getConfiguration(): IssuingAuthorityConfiguration {
        checkClientId()
        return getConfiguration(credentialIssuerUri, credentialConfigurationId)
    }

    override suspend fun register(): Registration {
        checkClientId()
        val documentId = createIssuerDocument(
            Openid4VciIssuerDocument(RegistrationResponse(false))
        )
        return Openid4VciRegistrationState(documentId)
    }

    override suspend fun completeRegistration(registration: Registration) {
        checkClientId()
        val registrationState = registration as Openid4VciRegistrationState
        updateIssuerDocument(
            registrationState.documentId,
            Openid4VciIssuerDocument(registrationState.response!!))
    }

    override suspend fun getState(documentId: String): DocumentState {
        checkClientId()
        val now = Clock.System.now()

        if (!issuerDocumentExists(documentId)) {
            return DocumentState(
                now,
                DocumentCondition.NO_SUCH_DOCUMENT,
                0,
                0
            )
        }

        val issuerDocument = loadIssuerDocument(documentId)

        if (issuerDocument.state == DocumentCondition.PROOFING_PROCESSING) {
            issuerDocument.state = if (issuerDocument.access != null) {
                DocumentCondition.CONFIGURATION_AVAILABLE
            } else {
                DocumentCondition.PROOFING_FAILED
            }
            updateIssuerDocument(documentId, issuerDocument)
        }

        // This information is helpful for when using the admin web interface.
        Logger.i(
            TAG, "Returning state for clientId=$clientId documentId=$documentId")

        // We create and sign Credential requests immediately so numPendingCredentialRequests is
        // always 0
        return DocumentState(
            now,
            issuerDocument.state,
            0,
            issuerDocument.credentials.size
        )
    }

    override suspend fun proof(documentId: String): Openid4VciProofingState {
        checkClientId()
        return Openid4VciProofingState(
            clientId = clientId,
            credentialConfigurationId = credentialConfigurationId,
            issuanceClientId = issuanceClientId,
            documentId = documentId,
            credentialIssuerUri = credentialIssuerUri
        )
    }

    override suspend fun completeProof(proofing: Proofing) {
        checkClientId()
        val state = proofing as Openid4VciProofingState
        val issuerDocument = loadIssuerDocument(state.documentId)
        issuerDocument.state = DocumentCondition.PROOFING_PROCESSING
        issuerDocument.access = state.access
        issuerDocument.secureAreaIdentifier = state.secureAreaIdentifier
        updateIssuerDocument(state.documentId, issuerDocument)
    }

    override suspend fun getDocumentConfiguration(
        documentId: String
    ): DocumentConfiguration {
        checkClientId()
        val issuerDocument = loadIssuerDocument(documentId)
        check(issuerDocument.state == DocumentCondition.CONFIGURATION_AVAILABLE)
        issuerDocument.state = DocumentCondition.READY
        if (issuerDocument.documentConfiguration == null) {
            issuerDocument.documentConfiguration = generateGenericDocumentConfiguration()
            val metadata = Openid4VciIssuerMetadata.get(credentialIssuerUri)
            val credentialConfiguration =
                metadata.credentialConfigurations[credentialConfigurationId]!!
            // For keyless credentials, just obtain them right away
            if (credentialConfiguration.proofType == Openid4VciNoProof &&
                issuerDocument.credentials.isEmpty()) {
                obtainCredentialsKeyless(documentId, issuerDocument)
            }
        }
        updateIssuerDocument(documentId, issuerDocument)
        return issuerDocument.documentConfiguration!!
    }

    override suspend fun requestCredentials(
        documentId: String
    ): RequestCredentials {
        checkClientId()
        val document = loadIssuerDocument(documentId)
        val metadata = Openid4VciIssuerMetadata.get(credentialIssuerUri)
        val credentialConfiguration = metadata.credentialConfigurations[credentialConfigurationId]!!

        refreshAccessIfNeeded(documentId, document)

        // obtain c_nonce (serves as challenge for the device-bound key)
        val httpClient = BackendEnvironment.getInterface(HttpClient::class)!!
        val nonceResponse = httpClient.post(metadata.nonceEndpoint) {}
        if (nonceResponse.status != HttpStatusCode.OK) {
            throw IssuingAuthorityException("Error getting a nonce")
        }
        Logger.i(TAG, "Got successful response for nonce request")
        val responseText = nonceResponse.readBytes().decodeToString()
        val cNonce = Json.parseToJsonElement(responseText)
                .jsonObject["c_nonce"]!!.jsonPrimitive.content

        val configuration = if (document.secureAreaIdentifier!!.startsWith("CloudSecureArea?")) {
            CredentialConfiguration(
                challenge = ByteString(cNonce.encodeToByteArray()),
                keyAssertionRequired = true,
                secureAreaConfiguration = SecureAreaConfigurationCloud(
                    algorithm = Algorithm.ESP256.name,
                    cloudSecureAreaId = document.secureAreaIdentifier!!,
                    passphraseRequired = true,
                    useStrongBox = true,
                    userAuthenticationRequired = true,
                    userAuthenticationTimeoutMillis = 0,
                    userAuthenticationTypes = 3, // LSKF + Biometrics
                )
            )
        } else {
            CredentialConfiguration(
                challenge = ByteString(cNonce.encodeToByteArray()),
                keyAssertionRequired = true,
                secureAreaConfiguration = SecureAreaConfigurationAndroidKeystore(
                    algorithm = Algorithm.ESP256.name,
                    useStrongBox = true,
                    userAuthenticationRequired = true,
                    userAuthenticationTimeoutMillis = 0,
                    userAuthenticationTypes = 3 // LSKF + Biometrics
                )
            )
        }
        return when (credentialConfiguration.proofType) {
            is Openid4VciProofTypeKeyAttestation ->
                RequestCredentialsUsingKeyAttestation(clientId, documentId, configuration)
            is Openid4VciProofTypeJwt ->
                RequestCredentialsUsingProofOfPossession(
                    clientId = clientId,
                    issuanceClientId = issuanceClientId,
                    documentId = documentId,
                    credentialConfiguration = configuration,
                    credentialIssuerId = metadata.credentialIssuerId
                )
            Openid4VciNoProof ->
                throw IllegalStateException("requestCredentials call is unexpected for keyless credentials")
            null -> throw IllegalStateException("Unexpected state")
        }
    }

    override suspend fun completeRequestCredentials(requestCredentials: RequestCredentials) {
        checkClientId()
        val state = requestCredentials as AbstractRequestCredentials
        // Create appropriate request to OpenID4VCI issuer to issue credential(s)
        val metadata = Openid4VciIssuerMetadata.get(credentialIssuerUri)
        val credentialConfiguration = metadata.credentialConfigurations[credentialConfigurationId]!!

        val credentialTasks = when (state) {
            is RequestCredentialsUsingProofOfPossession ->
                listOf(createRequestUsingProofOfPossession(state, credentialConfiguration))
            is RequestCredentialsUsingKeyAttestation ->
                createRequestUsingKeyAttestation(state, credentialConfiguration)
            else -> throw IllegalStateException("Unsupported RequestCredential type")
        }

        val document = loadIssuerDocument(state.documentId)

        for ((request, publicKeys) in credentialTasks) {
            // Send the request
            val credentials = obtainCredentials(metadata, request, state.documentId, document)

            check(credentials.size == publicKeys.size)
            document.credentials.addAll(credentials.zip(publicKeys).map {
                val credential = it.first.jsonObject["credential"]!!.jsonPrimitive.content
                val publicKey = it.second
                when (credentialConfiguration.format) {
                    is Openid4VciFormatSdJwt -> {
                        val sdJwt = SdJwt(credential)
                        CredentialData(
                            publicKey,
                            sdJwt.validFrom ?: sdJwt.issuedAt ?: Clock.System.now(),
                            sdJwt.validUntil ?: Instant.DISTANT_FUTURE,
                            CredentialFormat.SD_JWT_VC,
                            credential.encodeToByteArray()
                        )
                    }

                    is Openid4VciFormatMdoc -> {
                        val credentialBytes = credential.fromBase64Url()
                        val credentialData = StaticAuthDataParser(credentialBytes).parse()
                        val issuerAuthCoseSign1 = Cbor.decode(credentialData.issuerAuth).asCoseSign1
                        val encodedMsoBytes = Cbor.decode(issuerAuthCoseSign1.payload!!)
                        val encodedMso = Cbor.encode(encodedMsoBytes.asTaggedEncodedCbor)
                        val mso = MobileSecurityObjectParser(encodedMso).parse()
                        CredentialData(
                            publicKey,
                            mso.validFrom,
                            mso.validUntil,
                            CredentialFormat.MDOC_MSO,
                            credentialBytes
                        )
                    }

                    null -> throw IllegalStateException("Unexpected credential format")
                }
            })
        }

        updateIssuerDocument(state.documentId, document, true)
    }

    private suspend fun obtainCredentialsKeyless(
        documentId: String,
        issuerDocument: Openid4VciIssuerDocument,
    ) {
        val metadata = Openid4VciIssuerMetadata.get(credentialIssuerUri)
        val credentialConfiguration = metadata.credentialConfigurations[credentialConfigurationId]!!
        val request = buildJsonObject {
            put("credential_configuration_id", credentialConfiguration.id)
            putFormat(credentialConfiguration.format!!)
        }
        val credentials = obtainCredentials(
            metadata,
            request,
            documentId,
            issuerDocument
        )
        check(credentials.size == 1)
        val credential = credentials[0].jsonObject["credential"]!!.jsonPrimitive.content
        val sdJwt = SdJwt(credential)
        issuerDocument.credentials.add(
            CredentialData(
                null,
                sdJwt.validFrom ?: sdJwt.issuedAt ?: Clock.System.now(),
                sdJwt.validUntil ?: Instant.DISTANT_FUTURE,
                CredentialFormat.SD_JWT_VC,
                credential.encodeToByteArray()
            )
        )
    }

    private suspend fun obtainCredentials(
        metadata: Openid4VciIssuerMetadata,
        request: JsonObject,
        documentId: String,
        document: Openid4VciIssuerDocument
    ): JsonArray {
        val access = document.access!!
        val httpClient = BackendEnvironment.getInterface(HttpClient::class)!!

        // without a nonce we may need to retry
        var retry = access.dpopNonce == null
        var credentialResponse: HttpResponse
        while (true) {
            val dpop = OpenidUtil.generateDPoP(
                clientId,
                metadata.credentialEndpoint,
                access.dpopNonce,
                access.accessToken
            )
            Logger.e(TAG, "Credential request: $request")

            credentialResponse = httpClient.post(metadata.credentialEndpoint) {
                headers {
                    append("Authorization", "DPoP ${access.accessToken}")
                    append("DPoP", dpop)
                    append("Content-Type", "application/json")
                }
                setBody(request.toString())
            }
            if (credentialResponse.headers.contains("DPoP-Nonce")) {
                access.dpopNonce = credentialResponse.headers["DPoP-Nonce"]!!
                if (retry) {
                    retry = false // don't retry more than once
                    if (credentialResponse.status != HttpStatusCode.OK) {
                        Logger.e(TAG, "Retry with a fresh DPoP nonce")
                        continue  // retry with the nonce
                    }
                }
                updateIssuerDocument(documentId, document, false)
            }
            break
        }

        if (credentialResponse.status != HttpStatusCode.OK) {
            val errResponseText = credentialResponse.readBytes().decodeToString()
            Logger.e(TAG,"Credential request error: ${credentialResponse.status} $errResponseText")

            // In some issuers this gets document in permanent bad state, notification
            // is not needed as an exception will generate notification on the client side.
            document.state = DocumentCondition.DELETION_REQUESTED
            updateIssuerDocument(documentId, document, false)

            throw IssuingAuthorityException(
                "Error getting a credential issued: ${credentialResponse.status} $errResponseText")
        }
        Logger.i(TAG, "Got successful response for credential request")
        val responseText = credentialResponse.readBytes().decodeToString()

        val response = Json.parseToJsonElement(responseText) as JsonObject
        return response["credentials"]!!.jsonArray
    }

    private fun createRequestUsingProofOfPossession(
        state: RequestCredentialsUsingProofOfPossession,
        configuration: Openid4VciCredentialConfiguration
    ): Pair<JsonObject, List<EcPublicKey>> {
        val proofs = state.credentialRequests!!.map {
            JsonPrimitive(it.proofOfPossessionJwtHeaderAndBody + "." + it.proofOfPossessionJwtSignature)
        }
        val publicKeys = state.credentialRequests!!.map {
            it.request.secureAreaBoundKeyAttestation.publicKey
        }

        val request = buildJsonObject {
            put("credential_configuration_id", configuration.id)
            // this is obsolete:
            //putFormat(configuration.format!!)
            if (proofs.size == 1) {
                putJsonObject("proof") {
                    put("jwt", proofs[0])
                    put("proof_type", "jwt")
                }
            } else {
                putJsonObject("proofs") {
                    put("jwt", JsonArray(proofs))
                }
            }
        }

        return Pair(request, publicKeys)
    }

    private suspend fun createRequestUsingKeyAttestation(
        state: RequestCredentialsUsingKeyAttestation,
        configuration: Openid4VciCredentialConfiguration
    ): List<Pair<JsonObject, List<EcPublicKey>>> {
        val applicationSupport = BackendEnvironment.getInterface(ApplicationSupport::class)!!
        return state.credentialRequestSets.map { credentialRequestSet ->
            val jwtKeyAttestation = applicationSupport.createJwtKeyAttestation(
                keyAttestations = credentialRequestSet.keyAttestations,
                keysAssertion = credentialRequestSet.keysAssertion
            )
            val request = buildJsonObject {
                put("credential_configuration_id", configuration.id)
                put("proof", buildJsonObject {
                    put("attestation", JsonPrimitive(jwtKeyAttestation))
                    put("proof_type", JsonPrimitive("attestation"))
                })
                putFormat(configuration.format!!)
            }
            Pair(request, credentialRequestSet.keyAttestations.map { it.publicKey })
        }
    }

    override suspend fun getCredentials(documentId: String): List<CredentialData> {
        checkClientId()
        val document = loadIssuerDocument(documentId)
        val credentials = mutableListOf<CredentialData>()
        credentials.addAll(document.credentials)
        document.credentials.clear()
        updateIssuerDocument(documentId, document, false)
        return credentials
    }

    override suspend fun developerModeRequestUpdate(
        documentId: String,
        requestRemoteDeletion: Boolean,
        notifyApplicationOfUpdate: Boolean
    ) {
        checkClientId()
    }

    private suspend fun issuerDocumentExists(documentId: String): Boolean {
        val storage = BackendEnvironment.getTable(documentTableSpec)
        val encodedCbor = storage.get(partitionId = clientId, key = documentId)
        return encodedCbor != null
    }

    private suspend fun loadIssuerDocument(documentId: String): Openid4VciIssuerDocument {
        val storage = BackendEnvironment.getTable(documentTableSpec)
        val encodedCbor = storage.get(partitionId = clientId, key = documentId)
            ?: throw Error("No such document")
        return Openid4VciIssuerDocument.fromCbor(encodedCbor.toByteArray())
    }

    private suspend fun createIssuerDocument(document: Openid4VciIssuerDocument): String {
        val storage = BackendEnvironment.getTable(documentTableSpec)
        val bytes = document.toCbor()
        return storage.insert(key = null, partitionId = clientId, data = ByteString(bytes))
    }

    private suspend fun deleteIssuerDocument(
        documentId: String,
        emitNotification: Boolean = true
    ) {
        val storage = BackendEnvironment.getTable(documentTableSpec)
        storage.delete(partitionId = clientId, key = documentId)
        if (emitNotification) {
            emit(IssuingAuthorityNotification(documentId))
        }
    }

    private suspend fun updateIssuerDocument(
        documentId: String,
        document: Openid4VciIssuerDocument,
        emitNotification: Boolean = true,
    ) {
        val storage = BackendEnvironment.getTable(documentTableSpec)
        val bytes = document.toCbor()
        storage.update(partitionId = clientId, key = documentId, data = ByteString(bytes))
        if (emitNotification) {
            Logger.i(TAG, "Emitting notification for $documentId")
            emit(IssuingAuthorityNotification(documentId))
        }
    }

    private suspend fun generateGenericDocumentConfiguration(): DocumentConfiguration {
        val metadata = Openid4VciIssuerMetadata.get(credentialIssuerUri)
        val config = metadata.credentialConfigurations[credentialConfigurationId]!!
        val base = getConfiguration().pendingDocumentInformation
        return when (config.format) {
            is Openid4VciFormatSdJwt -> {
                DocumentConfiguration(
                    base.displayName,
                    base.typeDisplayName,
                    base.cardArt,
                    base.requireUserAuthenticationToViewDocument,
                    null,
                    SdJwtVcDocumentConfiguration(
                        vct = config.format.vct,
                        keyBound = config.proofType != Openid4VciNoProof
                    ),
                    null
                )
            }
            is Openid4VciFormatMdoc -> {
                val documentType = documentTypeRepository.getDocumentTypeForMdoc(config.format.docType)
                val staticData = if (documentType != null) {
                    fillInSampleData(documentType).build()
                } else {
                    NameSpacedData.Builder().build()
                }
                DocumentConfiguration(
                    base.displayName,
                    base.typeDisplayName,
                    base.cardArt,
                    base.requireUserAuthenticationToViewDocument,
                    MdocDocumentConfiguration(
                        config.format.docType,
                        staticData = staticData
                    ),
                    null,
                    null
                )
            }
            else -> throw IllegalStateException("Invalid credential format")
        }
    }

    private fun fillInSampleData(documentType: DocumentType): NameSpacedData.Builder {
        val builder = NameSpacedData.Builder()
        for ((namespaceName, namespace) in documentType.mdocDocumentType!!.namespaces) {
            for ((dataElementName, dataElement) in namespace.dataElements) {
                if (dataElement.attribute.sampleValueMdoc != null) {
                    builder.putEntry(
                        namespaceName,
                        dataElementName,
                        Cbor.encode(dataElement.attribute.sampleValueMdoc!!)
                    )
                }
            }
        }
        return builder
    }

    private suspend fun refreshAccessIfNeeded(
        documentId: String,
        document: Openid4VciIssuerDocument
    ) {
        var access = document.access!!
        val nowPlusSlack = Clock.System.now() + 30.seconds
        if (nowPlusSlack < access.accessTokenExpiration) {
            // No need to refresh.
            return
        }

        val refreshToken = access.refreshToken
        access = OpenidUtil.obtainToken(
            clientId = clientId,
            tokenUrl = access.tokenEndpoint,
            refreshToken = refreshToken,
            accessToken = access.accessToken,
            landingUrl = null,  // TODO: do we need to remember it for refresh?
            useClientAssertion = access.useClientAssertion
        )
        document.access = access
        Logger.i(TAG, "Refreshed access tokens")
        if (access.refreshToken == null) {
            Logger.w(TAG, "Kept original refresh token (no updated refresh token received)")
            access.refreshToken = refreshToken
        }
        updateIssuerDocument(documentId, document, false)
    }

    private suspend fun checkClientId() {
        check(clientId == RpcAuthContext.getClientId())
    }
}