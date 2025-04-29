package com.android.identity.testapp.provisioning.openid4vci

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
import io.ktor.http.Url
import io.ktor.http.protocolWithAuthority
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.multipaz.device.AssertionPoPKey
import org.multipaz.device.DeviceAssertionMaker
import org.multipaz.provisioning.ApplicationSupport
import kotlin.random.Random

internal object OpenidUtil {
    const val TAG = "OpenidUtil"

    private val keyCreationMutex = Mutex()

    private suspend fun getKey(alias: String): KeyInfo {
        val secureArea = BackendEnvironment.getInterface(SecureAreaProvider::class)!!.get()
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

    private suspend fun sign(alias: String, message: ByteArray): ByteArray {
        val secureArea = BackendEnvironment.getInterface(SecureAreaProvider::class)!!.get()
        val sig = secureArea.sign(alias, message, null)
        return sig.toCoseEncoded()
    }

    suspend fun dpopKey(clientId: String) = getKey("dpop:$clientId")
    suspend fun dpopSign(clientId: String, message: ByteArray) = sign("dpop:$clientId", message)

    suspend fun generateDPoP(
        clientId: String,
        requestUrl: String,
        dpopNonce: String?,
        accessToken: String? = null
    ): String {
        val publicKey = dpopKey(clientId).publicKey
        val alg = publicKey.curve.defaultSigningAlgorithmFullySpecified.joseAlgorithmIdentifier
        val header = buildJsonObject {
            put("typ", "dpop+jwt")
            put("alg", alg)
            put("jwk", publicKey.toJson(clientId))
        }.toString().encodeToByteArray().toBase64Url()
        val body = buildJsonObject {
            put("htm", "POST")
            put("htu", requestUrl)
            put("iat", Clock.System.now().epochSeconds)
            if (dpopNonce != null) {
                put("nonce", dpopNonce)
            }
            put("jti", Random.Default.nextBytes(15).toBase64Url())
            if (accessToken != null) {
                val hash = Crypto.digest(Algorithm.SHA256, accessToken.encodeToByteArray())
                put("ath", hash.toBase64Url())
            }
        }.toString().encodeToByteArray().toBase64Url()
        val message = "$header.$body"
        val signature = dpopSign(clientId, message.encodeToByteArray()).toBase64Url()
        return "$message.$signature"
    }

    suspend fun obtainToken(
        tokenUrl: String,
        clientId: String,
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
            // TODO: should we use dpop nonce as nonce here?
            val clientAttestation = createEphemeralWalletAttestation(
                clientId = clientId,
                endpoint = tokenUrl
            )
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
                add("client_id", clientId)
                // TODO: It's arbitrary in our case, right?
                add("redirect_uri", "https://secure.redirect.com")
            }
            val tokenResponse = httpClient.post(tokenUrl) {
                headers {
                    if (currentDpopNonce != null && accessToken != null) {
                        append("Authorization", "DPoP $accessToken")
                    }
                    append("DPoP", dpop)
                    append("OAuth-Client-Attestation", clientAttestation.attestationJwt)
                    append("OAuth-Client-Attestation-PoP", clientAttestation.attestationPopJwt)
                    append("Content-Type", "application/x-www-form-urlencoded")
                }
                setBody(tokenRequest.toString())
            }
            if (tokenResponse.status != HttpStatusCode.OK) {
                val errResponseText = tokenResponse.readBytes().decodeToString()
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
                val tokenString = tokenResponse.readBytes().decodeToString()
                Logger.e(TAG, "Invalid token response: ${err.message}: $tokenString")
                throw IssuingAuthorityException("Invalid response from the issuer")
            }
        }
    }

    suspend fun createEphemeralWalletAttestation(
        clientId: String,
        endpoint: String,
        nonce: String? = null
    ): ClientAttestation {
        val secureArea = BackendEnvironment.getInterface(SecureAreaProvider::class)!!.get()
        val keyInfo = secureArea.createKey(alias = null, createKeySettings = CreateKeySettings())
        try {
            val targetServer = Url(endpoint).protocolWithAuthority
            val applicationSupport = BackendEnvironment.getInterface(ApplicationSupport::class)!!
            val assertionMaker = BackendEnvironment.getInterface(DeviceAssertionMaker::class)!!
            val clientAttestation = applicationSupport.createJwtClientAttestation(
                keyInfo.attestation,
                assertionMaker.makeDeviceAssertion {
                    AssertionPoPKey(keyInfo.publicKey, targetServer)
                }
            )

            val alg = keyInfo.publicKey.curve.defaultSigningAlgorithmFullySpecified
            val header = buildJsonObject {
                put("typ", "oauth-client-attestation-pop+jwt")
                put("alg", alg.joseAlgorithmIdentifier)
            }.toString().encodeToByteArray().toBase64Url()
            val body = buildJsonObject {
                put("iss", clientId)
                put("aud", targetServer)
                put("iat", Clock.System.now().epochSeconds)
                put("jti", Random.Default.nextBytes(15).toBase64Url())
                if (nonce != null) {
                    put("nonce", nonce)
                }
            }.toString().encodeToByteArray().toBase64Url()
            val message = "$header.$body"
            val sig = secureArea.sign(keyInfo.alias, message.encodeToByteArray(), null)
            val clientAttestationPoP = "$message.${sig.toCoseEncoded().toBase64Url()}"

            return ClientAttestation(clientAttestation, clientAttestationPoP)
        } finally {
            secureArea.deleteKey(keyInfo.alias)
        }
    }

    class ClientAttestation(val attestationJwt: String, val attestationPopJwt: String)
}