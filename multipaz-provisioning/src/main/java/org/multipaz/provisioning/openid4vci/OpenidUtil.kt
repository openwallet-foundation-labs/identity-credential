package org.multipaz.provisioning.openid4vci

import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.provisioning.IssuingAuthorityException
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.KeyInfo
import org.multipaz.securearea.SecureAreaProvider
import org.multipaz.util.Logger
import org.multipaz.util.toBase64Url
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.random.Random

internal object OpenidUtil {
    const val TAG = "OpenidUtil"

    private val keyCreationMutex = Mutex()

    suspend fun communicationKey(clientId: String): KeyInfo {
        val secureArea = BackendEnvironment.getInterface(SecureAreaProvider::class)!!.get()
        val alias = "OpenidComm_" + clientId
        return try {
            secureArea.getKeyInfo(alias)
        } catch (_: Exception) {
            keyCreationMutex.withLock {
                try {
                    secureArea.getKeyInfo(alias)
                } catch (_: Exception) {
                    secureArea.createKey(alias, CreateKeySettings())
                    secureArea.getKeyInfo(alias)
                }
            }
        }
    }

    suspend fun communicationSign(clientId: String, message: ByteArray): ByteArray {
        val secureArea = BackendEnvironment.getInterface(SecureAreaProvider::class)!!.get()
        val alias = "OpenidComm_" + clientId
        val sig = secureArea.sign(alias, message, null)
        return sig.toCoseEncoded()
    }

    suspend fun generateDPoP(
        clientId: String,
        requestUrl: String,
        dpopNonce: String?,
        accessToken: String? = null
    ): String {
        val keyInfo = communicationKey(clientId)
        val header = buildJsonObject {
            put("typ", JsonPrimitive("dpop+jwt"))
            put("alg", JsonPrimitive(keyInfo.publicKey.curve.defaultSigningAlgorithm.joseAlgorithmIdentifier))
            put("jwk", keyInfo.publicKey.toJwk(additionalClaims = buildJsonObject { put("kid", JsonPrimitive(clientId)) }))
        }.toString().toByteArray().toBase64Url()
        val bodyObj = buildJsonObject {
            put("htm", JsonPrimitive("POST"))
            put("htu", JsonPrimitive(requestUrl))
            put("iat", JsonPrimitive(Clock.System.now().epochSeconds))
            if (dpopNonce != null) {
                put("nonce", JsonPrimitive(dpopNonce))
            }
            put("jti", JsonPrimitive(Random.Default.nextBytes(15).toBase64Url()))
            if (accessToken != null) {
                put("ath", JsonPrimitive(Crypto.digest(Algorithm.SHA256, accessToken.toByteArray()).toBase64Url()))
            }
        }
        val body = bodyObj.toString().toByteArray().toBase64Url()
        val message = "$header.$body"
        val signature = communicationSign(clientId, message.toByteArray()).toBase64Url()
        return "$message.$signature"
    }

    suspend fun obtainToken(
        tokenUrl: String,
        clientId: String,
        issuanceClientId: String,
        refreshToken: String? = null,
        accessToken: String? = null,
        authorizationCode: String? = null,
        preauthorizedCode: String? = null,
        txCode: String? = null,  // pin or other transaction code
        codeVerifier: String? = null,
        dpopNonce: String? = null
    ): OpenidAccess {
        if (refreshToken == null && authorizationCode == null && preauthorizedCode == null) {
            throw IllegalArgumentException("No authorizations provided")
        }
        val httpClient = BackendEnvironment.getInterface(HttpClient::class)!!
        var currentDpopNonce = dpopNonce
        // When dpop nonce is null, this loop will run twice, first request will return with error,
        // but will provide fresh, dpop nonce and the second request will get fresh access data.
        while (true) {
            val dpop = generateDPoP(clientId, tokenUrl, currentDpopNonce, null)
            val tokenRequest = FormUrlEncoder {
                if (refreshToken != null) {
                    add("grant_type", "refresh_token")
                    add("refresh_token", refreshToken)
                }
                if (authorizationCode != null) {
                    add("grant_type", "authorization_code")
                    add("code", authorizationCode)
                } else if (preauthorizedCode != null) {
                    add("grant_type", "urn:ietf:params:oauth:grant-type:pre-authorized_code")
                    add("pre-authorized_code", preauthorizedCode)
                    if (txCode != null) {
                        add("tx_code", txCode)
                    }
                }
                if (codeVerifier != null) {
                    add("code_verifier", codeVerifier)
                }
                add("client_id", issuanceClientId)
                // TODO: It's arbitrary in our case, right?
                add("redirect_uri", "https://secure.redirect.com")
            }
            val tokenResponse = httpClient.post(tokenUrl) {
                headers {
                    if (currentDpopNonce != null && accessToken != null) {
                        append("Authorization", "DPoP $accessToken")
                    }
                    append("DPoP", dpop)
                    append("Content-Type", "application/x-www-form-urlencoded")
                }
                setBody(tokenRequest.toString())
            }
            if (tokenResponse.status != HttpStatusCode.OK) {
                val errResponseText = String(tokenResponse.readBytes())
                if (currentDpopNonce == null && tokenResponse.headers.contains("DPoP-Nonce")) {
                    Logger.e(TAG, "DPoP nonce refreshed: $errResponseText")
                    currentDpopNonce = tokenResponse.headers["DPoP-Nonce"]!!
                    continue
                }
                Logger.e(TAG, "Token request error: ${tokenResponse.status} $errResponseText")
                throw IssuingAuthorityException(
                    if (authorizationCode != null) {
                        "Authorization code rejected by the issuer"
                    } else {
                        "Refresh token (seed credential) rejected by the issuer"
                    }
                )
            }
            return try {
                OpenidAccess.parseResponse(tokenUrl, tokenResponse)
            } catch (err: IllegalArgumentException) {
                val tokenString = String(tokenResponse.readBytes())
                Logger.e(TAG, "Invalid token response: ${err.message}: $tokenString")
                throw IssuingAuthorityException("Invalid response from the issuer")
            }
        }
    }
}