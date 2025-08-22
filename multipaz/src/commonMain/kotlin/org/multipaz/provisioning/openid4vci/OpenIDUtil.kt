package org.multipaz.provisioning.openid4vci

import io.ktor.http.Url
import io.ktor.http.authority
import io.ktor.http.protocolWithAuthority
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.KeyInfo
import org.multipaz.securearea.SecureAreaProvider
import org.multipaz.util.Logger
import org.multipaz.util.toBase64Url
import kotlin.random.Random
import kotlin.time.Clock

internal object OpenIDUtil {
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
            put("jwk", publicKey.toJwk(additionalClaims = buildJsonObject { put("kid", JsonPrimitive(clientId)) }))
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

    suspend fun createClientAssertion(endpoint: String): String {
        val backend = BackendEnvironment.getInterface(OpenID4VCIBackend::class)!!
        return backend.createJwtClientAssertion(endpoint)
    }

    suspend fun createWalletAttestation(
        clientId: String,
        endpoint: String,
        nonce: String? = null
    ): WalletAttestation {
        val secureArea = BackendEnvironment.getInterface(SecureAreaProvider::class)!!.get()
        // https://datatracker.ietf.org/doc/html/draft-ietf-oauth-attestation-based-client-auth-01
        // Section 6.1. "Client Instance Tracking Across Authorization Servers" recommends using
        // different keys for different servers. Keys are stored in the secure area using alias in
        // the following format: server address prefixed by "openid-wallet-attestation:".
        val endpointUrl = Url(endpoint)
        val targetServerAddress = endpointUrl.authority
        val keyAlias = "openid-wallet-attestation:$targetServerAddress"
        val keyInfo = try {
            secureArea.getKeyInfo(keyAlias)
        } catch (err: IllegalArgumentException) {
            Logger.e(TAG, "Client attestation key not found, creating a new one", err)
            secureArea.createKey(
                alias = keyAlias,
                createKeySettings = CreateKeySettings(
                    nonce = targetServerAddress.encodeToByteString()
                )
            )
        }
        val backend = BackendEnvironment.getInterface(OpenID4VCIBackend::class)!!
        val clientAttestation = backend.createJwtWalletAttestation(keyInfo.attestation)

        val alg = keyInfo.publicKey.curve.defaultSigningAlgorithmFullySpecified
        val header = buildJsonObject {
            put("typ", "oauth-client-attestation-pop+jwt")
            put("alg", alg.joseAlgorithmIdentifier)
        }.toString().encodeToByteArray().toBase64Url()
        val body = buildJsonObject {
            put("iss", clientId)
            put("aud", endpointUrl.protocolWithAuthority)
            put("iat", Clock.System.now().epochSeconds)
            put("jti", Random.Default.nextBytes(15).toBase64Url())
            if (nonce != null) {
                put("nonce", nonce)
            }
        }.toString().encodeToByteArray().toBase64Url()
        val message = "$header.$body"
        val sig = secureArea.sign(keyInfo.alias, message.encodeToByteArray(), null)
        val clientAttestationPoP = "$message.${sig.toCoseEncoded().toBase64Url()}"

        return WalletAttestation(clientAttestation, clientAttestationPoP)
    }

    class WalletAttestation(val attestationJwt: String, val attestationPopJwt: String)
}