package org.multipaz.openid4vci.util

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.protocolWithAuthority
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.Uint
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcPublicKey
import org.multipaz.openid4vci.credential.CredentialFactory
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Configuration
import org.multipaz.rpc.handler.InvalidRequestException
import org.multipaz.rpc.handler.SimpleCipher
import org.multipaz.server.getBaseUrl
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
import kotlin.time.Duration

const val OAUTH_REQUEST_URI_PREFIX = "urn:ietf:params:oauth:request_uri:"
const val OPENID4VP_REQUEST_URI_PREFIX = "https://rp.example.com/oidc/request/"

val AUTHZ_REQ = ContentType("application", "oauth-authz-req+jwt")

/**
 * Types of opaque session ids for client-server communication.
 */
enum class OpaqueIdType {
    PAR_CODE,
    AUTHORIZATION_STATE,
    ISSUER_STATE,
    REDIRECT,
    ACCESS_TOKEN,
    REFRESH_TOKEN,
    PID_READING,
    AUTH_SESSION,  // for use in /authorize_challenge
    OPENID4VP_CODE,  // send to /authorize when we want openid4vp request
    OPENID4VP_STATE,  // for state field in openid4vp
    OPENID4VP_PRESENTATION,  // for use in presentation_during_issuance_session
    RECORDS_STATE  // oauth state to authorize with System of Record
}

/**
 * Creates an opaque session id ("code") that can be safely given to the client. On the server
 * the session is just identified by its id, which stays the same. When referencing the session
 * from the client, we do not want the client to be able to play any games, thus the actual
 * server-side id and a small amount of metadata is encrypted using server secret key.
 *
 * We use these codes for many purposes (identified by [OpaqueIdType]) and always validate
 * that the code we get is actually created for its intended purpose. Also a code contains
 * expiration time, a code can only be used until it expires.
 */
suspend fun idToCode(type: OpaqueIdType, id: String, expiresIn: Duration): String {
    val buf = ByteStringBuilder()
    buf.append(type.ordinal.toByte())
    val idBytes = id.toByteArray()
    check(idBytes.size <= 255)
    buf.append(idBytes.size.toByte())
    buf.append(idBytes)
    val expiration = Clock.System.now() + expiresIn
    buf.append(Cbor.encode(Uint(expiration.epochSeconds.toULong())))
    val cipher = BackendEnvironment.getInterface(SimpleCipher::class)!!
    return cipher.encrypt(buf.toByteString().toByteArray()).toBase64Url()
}

/**
 * Decodes opaque session id ("code") into server-side id, validating code purpose (type)
 * and expiration time.
 */
suspend fun codeToId(type: OpaqueIdType, code: String): String {
    val cipher = BackendEnvironment.getInterface(SimpleCipher::class)!!
    val buf = cipher.decrypt(code.fromBase64Url())
    if (buf[0].toInt() != type.ordinal) {
        throw IllegalArgumentException(
            "Not required code/token type, need ${type.ordinal}, got ${buf[0].toInt()}")
    }
    val len = buf[1].toInt()
    val offsetAndExpirationTimeEpochSeconds = Cbor.decode(buf, 2 + len)
    // returned offset should be at the end of the string
    if (offsetAndExpirationTimeEpochSeconds.first != buf.size) {
        throw IllegalArgumentException("Decoding error")
    }
    // expiration time should not be in the past
    if (offsetAndExpirationTimeEpochSeconds.second.asNumber < Clock.System.now().epochSeconds) {
        throw IllegalArgumentException("Code/token expired")
    }
    return String(buf, 2, len)
}

/**
 * DPoP Authorization validation.
 */
suspend fun authorizeWithDpop(
    request: ApplicationRequest,
    publicKey: EcPublicKey,
    clientId: String,
    dpopNonce: ByteString?,
    accessToken: String? = null
) {
    val auth = request.headers["Authorization"]
    if (accessToken == null) {
        if (auth != null) {
            throw InvalidRequestException("Unexpected authorization header")
        }
    } else {
        if (auth == null) {
            throw InvalidRequestException("Authorization header required")
        }
        if (auth.substring(0, 5).lowercase() != "dpop ") {
            throw InvalidRequestException("DPoP authorization required")
        }
        if (auth.substring(5) != accessToken) {
            throw InvalidRequestException("Stale or invalid access token")
        }
    }

    val nonce = dpopNonce?.toByteArray()?.toBase64Url()
    validateDPoPJwt(request, publicKey, clientId, nonce, accessToken)
}

/**
 * Ensures Oauth client attestation attached to the given HTTP request is valid.
 *
 * See https://drafts.oauth.net/draft-ietf-oauth-attestation-based-client-auth/draft-ietf-oauth-attestation-based-client-auth.html
 *
 * @throws InvalidRequestException request is syntactically incorrect
 * @throws IllegalArgumentException attestation or attestation proof-of-possession signature is not valid
 * @return attestation public key
 */
suspend fun validateClientAttestation(
    request: ApplicationRequest,
    clientId: String
): EcPublicKey? {
    val clientAttestationJwt = request.headers["OAuth-Client-Attestation"]
        ?: return null

    val attestationBody = validateJwt(
        jwt = clientAttestationJwt,
        jwtName = "Client Attestation",
        publicKey = null,
        checks = mapOf(
            JwtCheck.TRUST to "client_attestation",  // where to find CA
            JwtCheck.TYP to "oauth-client-attestation+jwt",
            JwtCheck.SUB to clientId
        )
    )

    return EcPublicKey.fromJwk(attestationBody["cnf"]!!.jsonObject["jwk"]!!.jsonObject)
}

/**
 * Ensures Oauth client attestation proof-of-possession attached to the given HTTP request is
 * valid.
 */
suspend fun validateClientAttestationPoP(
    request: ApplicationRequest,
    clientId: String,
    attestationKey: EcPublicKey
) {
    val popJwt = request.headers["OAuth-Client-Attestation-PoP"]
        ?: throw InvalidRequestException("OAuth-Client-Attestation-PoP header required")

    val baseUrl = BackendEnvironment.getBaseUrl()
    val serverUrl = Url(baseUrl).protocolWithAuthority

    validateJwt(
        jwt = popJwt,
        jwtName = "Client attestation PoP",
        publicKey = attestationKey,
        checks = mapOf(
            JwtCheck.JTI to clientId,
            JwtCheck.TYP to "oauth-client-attestation-pop+jwt",
            JwtCheck.ISS to clientId,
            JwtCheck.AUD to serverUrl,
        )
    )
}

/**
 * Creates issuance session based on the given HTTP request and returns a unique id for it.
 */
suspend fun createSession(request: ApplicationRequest, parameters: Parameters): String {
    // Read all parameters
    val clientId = parameters["client_id"]
        ?: throw InvalidRequestException("missing parameter 'client_id'")
    val attestationKey = validateClientAttestation(request, clientId)
    if (attestationKey != null) {
        validateClientAttestationPoP(request, clientId, attestationKey)
    }
    val scope = parameters["scope"] ?: ""
    if (!CredentialFactory.supportedScopes.contains(scope)) {
        throw InvalidRequestException("invalid parameter 'scope'")
    }
    if (parameters["response_type"] != "code") {
        throw InvalidRequestException("invalid parameter 'response_type'")
    }
    if (parameters["code_challenge_method"] != "S256") {
        throw InvalidRequestException("invalid parameter 'code_challenge_method'")
    }
    val redirectUri = parameters["redirect_uri"]
        ?: throw InvalidRequestException("missing parameter 'redirect_uri'")
    val clientState = parameters["state"]
    if (!redirectUri.matches(plausibleUrl)) {
        throw InvalidRequestException("invalid parameter value 'redirect_uri'")
    }
    val codeChallenge = try {
        ByteString(parameters["code_challenge"]!!.fromBase64Url())
    } catch (err: Exception) {
        throw InvalidRequestException("invalid parameter 'code_challenge'")
    }
    val clientAuthenticated = attestationKey != null || validateClientAssertion(parameters, clientId)

    // Validate DPoP if any
    val dpopKey = processInitialDPoP(request)
    if (dpopKey != null) {
        validateDPoPJwt(request, dpopKey, clientId)
    } else {
        // DPoP is not supplied. We are OK with that as long as clientId is authenticated
        // one way or the other.
        if (!clientAuthenticated) {
            throw InvalidRequestException("client is not authenticated and DPoP is missing")
        }
    }

    // Create a session
    return IssuanceState.createIssuanceState(
        IssuanceState(clientId, scope, attestationKey,
            dpopKey, redirectUri, codeChallenge, clientState)
    )
}

/**
 * Process the initial DPoP header (that establishes the key for the rest of the session).
 */
fun processInitialDPoP(request: ApplicationRequest): EcPublicKey? {
    val dpopJwt = request.headers["DPoP"] ?: return null
    val dpopParts = dpopJwt.split('.')
    if (dpopParts.size != 3) {
        throw InvalidRequestException("invalid DPoP JWT")
    }
    val dpopHeader = Json.parseToJsonElement(
        dpopParts[0].fromBase64Url().decodeToString()
    ).jsonObject
    return EcPublicKey.fromJwk(
        jwk = (dpopHeader["jwk"] as? JsonObject)
            ?: throw InvalidRequestException("no jwk in DPoP header")
    )
}

/**
 * Validates Oauth client assertion.
 */
suspend fun validateClientAssertion(parameters: Parameters, clientId: String): Boolean {
    if (parameters["client_assertion_type"] != "urn:ietf:params:oauth:client-assertion-type:jwt-bearer") {
        return false
    }
    val clientAssertionJwt = parameters["client_assertion"] ?: return false
    validateClientAssertionJwt(clientAssertionJwt, clientId)
    return true
}

private suspend fun validateDPoPJwt(
    request: ApplicationRequest,
    publicKey: EcPublicKey,
    clientId: String,
    dpopNonce: String? = null,
    accessToken: String? = null
) {
    val dpop = request.headers["DPoP"] ?: throw InvalidRequestException("DPoP header required")
    val baseUrl = BackendEnvironment.getBaseUrl()
    val body = validateJwt(
        jwt = dpop,
        jwtName = "DPoP JWT",
        publicKey = publicKey,
        checks = buildMap {
            put(JwtCheck.JTI, clientId)
            put(JwtCheck.HTM, request.httpMethod.value)
            // NB: cannot use req.requestURL, as it does not take into account potential frontends.
            put(JwtCheck.HTU, "$baseUrl${request.uri}")
            if (accessToken != null) {
                val athHash = Crypto.digest(Algorithm.SHA256, accessToken.encodeToByteArray())
                put(JwtCheck.ATH, athHash.toBase64Url())
            }
        }
    )
    // validate dpop nonce manually, as we need special handling for the client to retry
    if (dpopNonce != body["nonce"]?.jsonPrimitive?.content) {
        throw DPoPNonceException()
    }
    if (accessToken == null && body.containsKey("ath")) {
        throw InvalidRequestException("DPoP JWT: 'ath' specified, but not expected")
    }
}

/**
 * Extract access token for a DPoP-protected requests.
 *
 * DPoP headers must be already validated by this point.
 */
fun extractAccessToken(request: ApplicationRequest): String {
    val authorization = request.headers["Authorization"]
    if (authorization == null || authorization.substring(0, 5).lowercase() != "dpop ") {
        throw InvalidRequestException("Authorization header invalid or missing")
    }
    return authorization.substring(5)
}

private suspend fun validateClientAssertionJwt(clientAssertionJwt: String, clientId: String) {
    validateJwt(
        jwt = clientAssertionJwt,
        jwtName = "client_assertion",
        publicKey = null,
        checks = mapOf(
            JwtCheck.TRUST to "client_assertion",  // where to find CA
            JwtCheck.JTI to clientId,
            JwtCheck.SUB to clientId,
            JwtCheck.ISS to clientId,
            JwtCheck.AUD to BackendEnvironment.getBaseUrl()
        )
    )
}

// We do not allow "#", "&" and "?" characters as they belong to query/fragment part of the
// URL which must not be present
private val plausibleUrl = Regex("^[^\\s'\"#&?]+\$")

