package com.android.identity.issuance.funke

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.document.NameSpacedData
import com.android.identity.documenttype.DocumentType
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.documenttype.knowntypes.EUPersonalID
import com.android.identity.flow.annotation.FlowJoin
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.flow.server.Resources
import com.android.identity.flow.server.Storage
import com.android.identity.issuance.CredentialConfiguration
import com.android.identity.issuance.CredentialData
import com.android.identity.issuance.CredentialFormat
import com.android.identity.issuance.DocumentCondition
import com.android.identity.issuance.DocumentConfiguration
import com.android.identity.issuance.DocumentState
import com.android.identity.issuance.IssuingAuthority
import com.android.identity.issuance.IssuingAuthorityConfiguration
import com.android.identity.issuance.IssuingAuthorityNotification
import com.android.identity.issuance.MdocDocumentConfiguration
import com.android.identity.issuance.RegistrationResponse
import com.android.identity.issuance.SdJwtVcDocumentConfiguration
import com.android.identity.issuance.common.AbstractIssuingAuthorityState
import com.android.identity.issuance.common.cache
import com.android.identity.securearea.KeyPurpose
import com.android.identity.util.Logger
import com.android.identity.util.fromBase64
import com.android.identity.util.toBase64
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

@FlowState(
    flowInterface = IssuingAuthority::class
)
@CborSerializable
class FunkeIssuingAuthorityState(
    val clientId: String,
    val credentialFormat: CredentialFormat
) : AbstractIssuingAuthorityState() {
    companion object {
        private const val TAG = "FunkeIssuingAuthorityState"

        private const val DOCUMENT_TABLE = "FunkeIssuerDocument"

        fun getConfiguration(env: FlowEnvironment, credentialFormat: CredentialFormat): IssuingAuthorityConfiguration {
            val id = when (credentialFormat) {
                CredentialFormat.SD_JWT_VC -> "funkeSdJwtVc"
                CredentialFormat.MDOC_MSO -> "funkeMdocMso"
            }
            val issuingAuthorityName = when (credentialFormat) {
                CredentialFormat.SD_JWT_VC -> "Funke PID Issuer (SD-JWT)"
                CredentialFormat.MDOC_MSO -> "Funke PID Issuer (MDOC)"
            }
            return env.cache(IssuingAuthorityConfiguration::class, id) { configuration, resources ->
                val logoPath = "funke/logo.png"
                val logo = resources.getRawResource(logoPath)!!
                val artPath = "funke/card_art.png"
                val art = resources.getRawResource(artPath)!!
                val requireUserAuthenticationToViewDocument = false
                IssuingAuthorityConfiguration(
                    identifier = id,
                    issuingAuthorityName = issuingAuthorityName,
                    issuingAuthorityLogo = logo.toByteArray(),
                    issuingAuthorityDescription = "Funke",
                    pendingDocumentInformation = DocumentConfiguration(
                        displayName = "Pending",
                        typeDisplayName = "EU Personal ID",
                        cardArt = art.toByteArray(),
                        requireUserAuthenticationToViewDocument = requireUserAuthenticationToViewDocument,
                        mdocConfiguration = null,
                        sdJwtVcDocumentConfiguration = null
                    ),
                    numberOfCredentialsToRequest = 1,
                    minCredentialValidityMillis = 30 * 24 * 3600L,
                    maxUsesPerCredentials = Int.MAX_VALUE,
                )
            }
        }

        val documentTypeRepository = DocumentTypeRepository().apply {
            addDocumentType(EUPersonalID.getDocumentType())
        }
    }

    @FlowMethod
    fun getConfiguration(env: FlowEnvironment): IssuingAuthorityConfiguration {
        return getConfiguration(env, credentialFormat)
    }

    @FlowMethod
    suspend fun register(env: FlowEnvironment): FunkeRegistrationState {
        val documentId = createIssuerDocument(env,
            FunkeIssuerDocument(
                RegistrationResponse(false),
                DocumentCondition.PROOFING_REQUIRED,
                null,
                null,
                null,
                null,
                mutableListOf(),
                mutableListOf()
            )
        )
        return FunkeRegistrationState(documentId)
    }

    @FlowJoin
    suspend fun completeRegistration(env: FlowEnvironment, registrationState: FunkeRegistrationState) {
        updateIssuerDocument(
            env,
            registrationState.documentId,
            FunkeIssuerDocument(
                registrationState.response!!,
                DocumentCondition.PROOFING_REQUIRED,
                null,
                null, // no evidence yet
                null,  // no initial document configuration
                null,
                mutableListOf(),
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

        val token = issuerDocument.token
        if (issuerDocument.state == DocumentCondition.PROOFING_PROCESSING && token != null) {
            issuerDocument.state = if (token.isNotEmpty()) {
                DocumentCondition.CONFIGURATION_AVAILABLE
            } else {
                DocumentCondition.PROOFING_FAILED
            }
            updateIssuerDocument(env, documentId, issuerDocument)
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

    @FlowMethod
    suspend fun proof(env: FlowEnvironment, documentId: String): FunkeProofingState {
        val pkceCodeVerifier = Random.Default.nextBytes(32).toBase64()
        val tcTokenUrl = performPushedAuthorizationRequest(env, pkceCodeVerifier)
        return FunkeProofingState(clientId, documentId, tcTokenUrl, pkceCodeVerifier)
    }

    @FlowJoin
    suspend fun completeProof(env: FlowEnvironment, state: FunkeProofingState) {
        val issuerDocument = loadIssuerDocument(env, state.documentId)
        issuerDocument.state = DocumentCondition.PROOFING_PROCESSING
        issuerDocument.dpopNonce = state.dpopNonce
        issuerDocument.token = state.token
        issuerDocument.secureAreaIdentifier = state.secureAreaIdentifier
        updateIssuerDocument(env, state.documentId, issuerDocument)
    }

    @FlowMethod
    suspend fun getDocumentConfiguration(
        env: FlowEnvironment,
        documentId: String
    ): DocumentConfiguration {
        val issuerDocument = loadIssuerDocument(env, documentId)
        check(issuerDocument.state == DocumentCondition.CONFIGURATION_AVAILABLE)
        issuerDocument.state = DocumentCondition.READY
        if (issuerDocument.documentConfiguration == null) {
            issuerDocument.documentConfiguration = generateDocumentConfiguration(env)
        }
        updateIssuerDocument(env, documentId, issuerDocument)
        return issuerDocument.documentConfiguration!!
    }

    @FlowMethod
    suspend fun requestCredentials(
        env: FlowEnvironment,
        documentId: String
    ): FunkeRequestCredentialsState {
        val document = loadIssuerDocument(env, documentId)
        val token = Json.parseToJsonElement(document.token!!) as JsonObject
        val cNonce = token["c_nonce"]!!.jsonPrimitive.content
        val configuration = if (document.secureAreaIdentifier!!.startsWith("CloudSecureArea?")) {
            val purposes = setOf(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY)
            CredentialConfiguration(
                cNonce.toByteArray(),
                document.secureAreaIdentifier!!,
                Cbor.encode(
                    CborMap.builder()
                        .put("passphraseRequired", true)
                        .put("userAuthenticationRequired", true)
                        .put("userAuthenticationTimeoutMillis", 0L)
                        .put("userAuthenticationTypes", 3 /* LSKF + Biometrics */)
                        .put("purposes", KeyPurpose.encodeSet(purposes))
                        .end().build()
                )
            )
        } else {
            CredentialConfiguration(
                cNonce.toByteArray(),
                document.secureAreaIdentifier!!,
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
        return FunkeRequestCredentialsState(documentId, configuration, cNonce)
    }

    @FlowJoin
    suspend fun completeRequestCredentials(env: FlowEnvironment, state: FunkeRequestCredentialsState) {
        val document = loadIssuerDocument(env, state.documentId)

        val proofs = state.credentialRequests!!.map {
            JsonPrimitive(it.proofOfPossessionJwtHeaderAndBody + "." + it.proofOfPossessionJwtSignature)
        }

        val request = mutableMapOf<String, JsonElement>(
            if (proofs.size == 1) {
                "proof" to JsonObject(
                    mapOf(
                        "jwt" to proofs[0],
                        "proof_type" to JsonPrimitive("jwt")
                    )
                )
            } else {
                "proofs" to JsonObject(
                    mapOf(
                        "jwt" to JsonArray(proofs)
                    )
                )
            }
        )

        val format = state.format
        when (format) {
            CredentialFormat.SD_JWT_VC -> {
                request["format"] = JsonPrimitive("vc+sd-jwt")
                request["vct"] = JsonPrimitive(FunkeUtil.SD_JWT_VCT)
            }
            CredentialFormat.MDOC_MSO -> {
                request["format"] = JsonPrimitive("mso_mdoc")
                request["doctype"] = JsonPrimitive("eu.europa.ec.eudi.pid.1")
            }
            null -> throw IllegalStateException("Credential format was not specified")
        }

        val token = Json.parseToJsonElement(document.token!!) as JsonObject
        val accessToken = token["access_token"]!!.jsonPrimitive.content
        val credentialUrl = "${FunkeUtil.BASE_URL}/c/credential"
        val dpop = FunkeUtil.generateDPoP(env, clientId, credentialUrl, document.dpopNonce!!, accessToken)
        val httpClient = env.getInterface(HttpClient::class)!!
        val credentialResponse = httpClient.post(credentialUrl) {
            headers {
                append("Authorization", "DPoP $accessToken")
                append("DPoP", dpop)
                append("Content-Type", "application/json")
            }
            setBody(JsonObject(request).toString())
        }
        if (credentialResponse.status != HttpStatusCode.OK) {
            val responseText = String(credentialResponse.readBytes())
            Logger.e(TAG, "Credential request error: ${credentialResponse.status} $responseText")
            throw IllegalStateException("Credential request error")
        }
        Logger.i(TAG, "Got successful response for credential request")
        val responseText = String(credentialResponse.readBytes())

        val response = Json.parseToJsonElement(responseText) as JsonObject
        val credentials = if (proofs.size == 1) {
            JsonArray(listOf(response["credential"]!!))
        } else {
            response["credentials"] as JsonArray
        }
        check(credentials.size == state.credentialRequests!!.size)
        document.credentials.addAll(credentials.zip(state.credentialRequests!!).map {
            val credential = it.first.jsonPrimitive.content
            val publicKey = it.second.request.secureAreaBoundKeyAttestation.publicKey
            val now = Clock.System.now()
            // TODO: where do we get this in SD-JWT world?
            val expiration = Clock.System.now() + 14.days
            when (format) {
                CredentialFormat.SD_JWT_VC ->
                    CredentialData(publicKey, now, expiration, CredentialFormat.SD_JWT_VC, credential.toByteArray())
                CredentialFormat.MDOC_MSO ->
                    CredentialData(publicKey, now, expiration, CredentialFormat.MDOC_MSO, credential.fromBase64())
            }
        })
        updateIssuerDocument(env, state.documentId, document, true)
    }

    @FlowMethod
    suspend fun getCredentials(env: FlowEnvironment, documentId: String): List<CredentialData> {
        return loadIssuerDocument(env, documentId).credentials
    }

    @FlowMethod
    suspend fun developerModeRequestUpdate(
        env: FlowEnvironment,
        documentId: String,
        requestRemoteDeletion: Boolean,
        notifyApplicationOfUpdate: Boolean
    ) {
    }

    private suspend fun issuerDocumentExists(env: FlowEnvironment, documentId: String): Boolean {
        if (clientId.isEmpty()) {
            throw IllegalStateException("Client not authenticated")
        }
        val storage = env.getInterface(Storage::class)!!
        val encodedCbor = storage.get(DOCUMENT_TABLE, clientId, documentId)
        return encodedCbor != null
    }

    private suspend fun loadIssuerDocument(env: FlowEnvironment, documentId: String): FunkeIssuerDocument {
        if (clientId.isEmpty()) {
            throw IllegalStateException("Client not authenticated")
        }
        val storage = env.getInterface(Storage::class)!!
        val encodedCbor = storage.get(DOCUMENT_TABLE, clientId, documentId)
            ?: throw Error("No such document")
        return FunkeIssuerDocument.fromCbor(encodedCbor.toByteArray())
    }

    private suspend fun createIssuerDocument(env: FlowEnvironment, document: FunkeIssuerDocument): String {
        if (clientId.isEmpty()) {
            throw IllegalStateException("Client not authenticated")
        }
        val storage = env.getInterface(Storage::class)!!
        val bytes = document.toCbor()
        return storage.insert(DOCUMENT_TABLE, clientId, ByteString(bytes))
    }

    private suspend fun deleteIssuerDocument(env: FlowEnvironment,
                                             documentId: String,
                                             emitNotification: Boolean = true) {
        if (clientId.isEmpty()) {
            throw IllegalStateException("Client not authenticated")
        }
        val storage = env.getInterface(Storage::class)!!
        storage.delete(DOCUMENT_TABLE, clientId, documentId)
        if (emitNotification) {
            emit(env, IssuingAuthorityNotification(documentId))
        }
    }

    private suspend fun updateIssuerDocument(
        env: FlowEnvironment,
        documentId: String,
        document: FunkeIssuerDocument,
        emitNotification: Boolean = true,
    ) {
        if (clientId.isEmpty()) {
            throw IllegalStateException("Client not authenticated")
        }
        val storage = env.getInterface(Storage::class)!!
        val bytes = document.toCbor()
        storage.update(DOCUMENT_TABLE, clientId, documentId, ByteString(bytes))
        if (emitNotification) {
            emit(env, IssuingAuthorityNotification(documentId))
        }
    }

    private suspend fun performPushedAuthorizationRequest(env: FlowEnvironment, pkceCodeVerifier: String): String {
        val codeChallenge = Crypto.digest(Algorithm.SHA256, pkceCodeVerifier.toByteArray()).toBase64()
        val assertion = FunkeUtil.createParJwtAssertion(env, clientId)
        val req = FormUrlEncoder {
            add("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-client-attestation")
            add("scope", "pid")
            add("response_type", "code")
            add("code_challenge_method", "S256")
            add("redirect_uri", "https://secure.redirect.com")  // TODO: It's arbitrary in our case, right?
            add("client_assertion", assertion)
            add("code_challenge", codeChallenge)
            add("client_id", FunkeUtil.CLIENT_ID)
        }
        val httpClient = env.getInterface(HttpClient::class)!!
        val response = httpClient.post("${FunkeUtil.BASE_URL}/c/par") {
            headers {
                append("Content-Type", "application/x-www-form-urlencoded")
            }
            setBody(req.toString())
        }
        if (response.status != HttpStatusCode.Created) {
            Logger.e(TAG, "PAR request error: ${response.status}")
            throw IllegalStateException("PAR request error")
        }
        val parsedResponse = Json.parseToJsonElement(String(response.readBytes())) as JsonObject
        val requestUri = parsedResponse["request_uri"]
        if (requestUri !is JsonPrimitive) {
            Logger.e(TAG, "PAR response error")
            throw IllegalStateException("PAR response error")
        }
        Logger.i(TAG, "Request uri: $requestUri")
        return "${FunkeUtil.BASE_URL}/c/authorize?" + FormUrlEncoder {
            add("client_id", FunkeUtil.CLIENT_ID)
            add("request_uri", requestUri.content)
        }
    }

    private suspend fun generateDocumentConfiguration(
        env: FlowEnvironment
    ): DocumentConfiguration {
        val artPath = "funke/card_art.png"
        val art = env.getInterface(Resources::class)!!.getRawResource(artPath)!!

        return when (credentialFormat) {
            CredentialFormat.SD_JWT_VC ->
                DocumentConfiguration(
                    "Funke",
                    "Personal ID (SD-JWT)",
                    art.toByteArray(),
                    false,
                    null,
                    SdJwtVcDocumentConfiguration(FunkeUtil.SD_JWT_VCT)
                )
            CredentialFormat.MDOC_MSO -> DocumentConfiguration(
                "Funke",
                "Personal ID (MDOC)",
                art.toByteArray(),
                false,
                MdocDocumentConfiguration(
                    EUPersonalID.EUPID_DOCTYPE,
                    staticData = fillInSampleData(
                        documentTypeRepository.getDocumentTypeForMdoc(EUPersonalID.EUPID_DOCTYPE)!!
                    ).build()
                ),
                null
            )
        }
    }

    private fun fillInSampleData(documentType: DocumentType): NameSpacedData.Builder {
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