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
import com.android.identity.issuance.IssuingAuthorityException
import com.android.identity.issuance.IssuingAuthorityNotification
import com.android.identity.issuance.MdocDocumentConfiguration
import com.android.identity.issuance.RegistrationResponse
import com.android.identity.issuance.SdJwtVcDocumentConfiguration
import com.android.identity.issuance.WalletApplicationCapabilities
import com.android.identity.issuance.common.AbstractIssuingAuthorityState
import com.android.identity.issuance.common.cache
import com.android.identity.issuance.fromCbor
import com.android.identity.securearea.KeyPurpose
import com.android.identity.util.Logger
import com.android.identity.util.fromBase64Url
import com.android.identity.util.toBase64Url
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
import kotlin.time.Duration.Companion.seconds

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
            val variant = when (credentialFormat) {
                CredentialFormat.SD_JWT_VC -> "SD-JWT"
                CredentialFormat.MDOC_MSO -> "mDoc"
            }
            return env.cache(IssuingAuthorityConfiguration::class, id) { configuration, resources ->
                val logoPath = "funke/logo.png"
                val logo = resources.getRawResource(logoPath)!!
                val artPath = "funke/card_art_funke_generic.png"
                val art = resources.getRawResource(artPath)!!
                val requireUserAuthenticationToViewDocument = false
                IssuingAuthorityConfiguration(
                    identifier = id,
                    issuingAuthorityName = "SPRIND Funke EUDI Wallet Prototype PID Issuer",
                    issuingAuthorityLogo = logo.toByteArray(),
                    issuingAuthorityDescription = "Personal ID - $variant",
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
                null, // no evidence yet
                null,
                null,  // no initial document configuration
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

        if (issuerDocument.state == DocumentCondition.PROOFING_PROCESSING) {
            issuerDocument.state = if (issuerDocument.access != null) {
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
        val pkceCodeVerifier = Random.Default.nextBytes(32).toBase64Url()
        val tcTokenUrl = performPushedAuthorizationRequest(env, pkceCodeVerifier)
        val storage = env.getInterface(Storage::class)!!
        val applicationCapabilities = storage.get(
            "WalletApplicationCapabilities",
            "",
            clientId
        )?.let {
            WalletApplicationCapabilities.fromCbor(it.toByteArray())
        } ?: throw IllegalStateException("WalletApplicationCapabilities not found")
        return FunkeProofingState(clientId, documentId, tcTokenUrl, pkceCodeVerifier,
            applicationCapabilities)
    }

    @FlowJoin
    suspend fun completeProof(env: FlowEnvironment, state: FunkeProofingState) {
        val issuerDocument = loadIssuerDocument(env, state.documentId)
        issuerDocument.state = DocumentCondition.PROOFING_PROCESSING
        issuerDocument.access = state.access
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
            val isCloudSecureArea = issuerDocument.secureAreaIdentifier!!.startsWith("CloudSecureArea?")
            issuerDocument.documentConfiguration = generateDocumentConfiguration(env, isCloudSecureArea)
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

        refreshAccessIfNeeded(env, documentId, document)

        val cNonce = document.access!!.cNonce!!
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
                        .put("useStrongBox", true)
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
                request["doctype"] = JsonPrimitive(FunkeUtil.EU_PID_MDOC_DOCTYPE)
            }
            null -> throw IllegalStateException("Credential format was not specified")
        }

        val credentialUrl = "${FunkeUtil.BASE_URL}/credential"
        val access = document.access!!
        val dpop = FunkeUtil.generateDPoP(
            env,
            clientId,
            credentialUrl,
            access.dpopNonce,
            access.accessToken
        )
        val httpClient = env.getInterface(HttpClient::class)!!
        val credentialResponse = httpClient.post(credentialUrl) {
            headers {
                append("Authorization", "DPoP ${access.accessToken}")
                append("DPoP", dpop)
                append("Content-Type", "application/json")
            }
            setBody(JsonObject(request).toString())
        }
        access.cNonce = null  // used up

        if (credentialResponse.headers.contains("DPoP-Nonce")) {
            access.dpopNonce = credentialResponse.headers["DPoP-Nonce"]!!
        }

        if (credentialResponse.status != HttpStatusCode.OK) {
            val errResponseText = String(credentialResponse.readBytes())
            Logger.e(
                TAG,
                "Credential request error: ${credentialResponse.status} $errResponseText"
            )

            // Currently in Funke case this gets document in permanent bad state, notification
            // is not needed as an exception will generate notification on the client side.
            document.state = DocumentCondition.DELETION_REQUESTED
            updateIssuerDocument(env, state.documentId, document, false)

            throw IssuingAuthorityException("Error getting a credential issued")
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
                    CredentialData(publicKey, now, expiration, CredentialFormat.MDOC_MSO, credential.fromBase64Url())
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
        val codeChallenge = Crypto.digest(Algorithm.SHA256, pkceCodeVerifier.toByteArray()).toBase64Url()
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
        val response = httpClient.post("${FunkeUtil.BASE_URL}/par") {
            headers {
                append("Content-Type", "application/x-www-form-urlencoded")
            }
            setBody(req.toString())
        }
        if (response.status != HttpStatusCode.Created) {
            Logger.e(TAG, "PAR request error: ${response.status}")
            throw IssuingAuthorityException("Error establishing authenticated channel with issuer")
        }
        val parsedResponse = Json.parseToJsonElement(String(response.readBytes())) as JsonObject
        val requestUri = parsedResponse["request_uri"]
        if (requestUri !is JsonPrimitive) {
            Logger.e(TAG, "PAR response error")
            throw IllegalStateException("PAR response syntax error")
        }
        Logger.i(TAG, "Request uri: $requestUri")
        return "${FunkeUtil.BASE_URL}/authorize?" + FormUrlEncoder {
            add("client_id", FunkeUtil.CLIENT_ID)
            add("request_uri", requestUri.content)
        }
    }

    private suspend fun generateDocumentConfiguration(
        env: FlowEnvironment,
        isCloudSecureArea: Boolean
    ): DocumentConfiguration {
        val resources = env.getInterface(Resources::class)!!
        return when (credentialFormat) {
            CredentialFormat.SD_JWT_VC -> {
                val art = resources.getRawResource(
                    if (isCloudSecureArea) {
                        "funke/card_art_funke_sdjwt_c1.png"
                    } else {
                        "funke/card_art_funke_sdjwt_c.png"
                    })!!
                DocumentConfiguration(
                    "Funke",
                    "Personal ID (SD-JWT)",
                    art.toByteArray(),
                    false,
                    null,
                    SdJwtVcDocumentConfiguration(FunkeUtil.SD_JWT_VCT)
                )
            }
            CredentialFormat.MDOC_MSO -> {
                val art = resources.getRawResource(
                    if (isCloudSecureArea) {
                        "funke/card_art_funke_mdoc_c1.png"
                    } else {
                        "funke/card_art_funke_mdoc_c.png"
                    })!!
                DocumentConfiguration(
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

    private suspend fun refreshAccessIfNeeded(
        env: FlowEnvironment,
        documentId: String,
        document: FunkeIssuerDocument
    ) {
        var access = document.access!!
        val nowPlusSlack = Clock.System.now() + 10.seconds
        if (access.cNonce != null && nowPlusSlack < access.accessTokenExpiration) {
            // No need to refresh.
            return
        }
        val tokenUrl = "${FunkeUtil.BASE_URL}/token"
        val httpClient = env.getInterface(HttpClient::class)!!
        // NB: currently, refresh token is only issued for Option C'
        val refreshToken = access.refreshToken
            ?: throw IssuingAuthorityException("No refresh token, new credentials cannot be issued")
        var dpopNonce: String? = null
        // This loop will run twice, first request will return with error, but will provide fresh
        // dpop nonce and the second request will get fresh access data.
        while (true) {
            val dpop = FunkeUtil.generateDPoP(env, clientId, tokenUrl, dpopNonce, null)
            val tokenRequest = FormUrlEncoder {
                add("grant_type", "refresh_token")
                add("refresh_token", refreshToken)
                add("client_id", FunkeUtil.CLIENT_ID)
                // TODO: It's arbitrary in our case, right?
                add("redirect_uri", "https://secure.redirect.com")
            }
            val tokenResponse = httpClient.post(tokenUrl) {
                headers {
                    if (dpopNonce != null) {
                        append("Authorization", "DPoP ${access.accessToken}")
                    }
                    append("DPoP", dpop)
                    append("Content-Type", "application/x-www-form-urlencoded")
                }
                setBody(tokenRequest.toString())
            }
            if (tokenResponse.status != HttpStatusCode.OK) {
                val errResponseText = String(tokenResponse.readBytes())
                if (dpopNonce == null && tokenResponse.headers.contains("DPoP-Nonce")) {
                    Logger.e(TAG, "DPoP nonce refreshed: $errResponseText")
                    dpopNonce = tokenResponse.headers["DPoP-Nonce"]!!
                    continue
                }
                Logger.e(TAG, "Token request error: ${tokenResponse.status} $errResponseText")
                throw IssuingAuthorityException("Refresh token (seed credential) rejected by the issuer")
            }
            access = try {
                FunkeAccess.parseResponse(tokenResponse)
            } catch (err: IllegalArgumentException) {
                val tokenString = String(tokenResponse.readBytes())
                Logger.e(TAG, "Invalid token response: ${err.message}: $tokenString")
                throw IssuingAuthorityException("Invalid response from the issuer")
            }
            check(access.cNonce != null)
            document.access = access
            Logger.i(TAG, "Refreshed access tokens")
            if (access.refreshToken == null) {
                Logger.w(TAG, "Kept original refresh token (no updated refresh token received)")
                access.refreshToken = refreshToken
            }
            updateIssuerDocument(env, documentId, document, false)
            break
        }
    }
}