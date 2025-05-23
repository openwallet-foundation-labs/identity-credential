package org.multipaz.openid4vci.util

import io.ktor.http.ContentType
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.uri
import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.Uint
import org.multipaz.crypto.EcPublicKey
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Configuration
import org.multipaz.rpc.handler.InvalidRequestException
import org.multipaz.rpc.handler.SimpleCipher
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
    OPENID4VP_PRESENTATION  // for use in presentation_during_issuance_session
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
    dpopNonce: String?,
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
    val dpop = request.headers["DPoP"]
        ?: throw InvalidRequestException("DPoP header required")
    val parts = dpop.split('.')
    if (parts.size != 3) {
        throw InvalidRequestException("DPoP invalid")
    }
    checkJwtSignature(publicKey, dpop)
    val json = Json.parseToJsonElement(String(parts[1].fromBase64Url())) as JsonObject
    val jsonNonce = json["nonce"]?.jsonPrimitive?.content
    if (jsonNonce != dpopNonce) {
        throw InvalidRequestException("Stale or invalid DPoP nonce: '$jsonNonce'")
    }
    val baseUrl = BackendEnvironment.getInterface(Configuration::class)!!.getValue("base_url")
    // NB: cannot use req.requestURL, as it does not take into account potential frontends.
    val expectedUrl = "$baseUrl${request.uri}"
    val actualUrl = json["htu"]?.jsonPrimitive?.content
    if (actualUrl != expectedUrl) {
        throw InvalidRequestException("Incorrect request URI: $expectedUrl vs $actualUrl")
    }
}
