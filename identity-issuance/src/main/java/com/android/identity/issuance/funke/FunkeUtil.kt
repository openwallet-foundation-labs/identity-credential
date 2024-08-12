package com.android.identity.issuance.funke

import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.sdjwt.util.JsonWebKey
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.KeyInfo
import com.android.identity.securearea.SecureArea
import com.android.identity.util.toBase64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

internal object FunkeUtil {
    const val BASE_URL = "https://demo.pid-issuer.bundesdruckerei.de"
    const val CLIENT_ID = "fed79862-af36-4fee-8e64-89e3c91091ed"
    const val SD_JWT_VCT = "urn:eu.europa.ec.eudi:pid:1"

    private val keyCreationMutex = Mutex()

    suspend fun communicationKey(env: FlowEnvironment, clientId: String): KeyInfo {
        val secureArea = env.getInterface(SecureArea::class)!!
        val alias = "FunkeComm_" + clientId
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

    fun communicationSign(env: FlowEnvironment, clientId: String, message: ByteArray): ByteArray {
        val secureArea = env.getInterface(SecureArea::class)!!
        val alias = "FunkeComm_" + clientId
        val sig = secureArea.sign(alias, Algorithm.ES256, message, null)
        return sig.toCoseEncoded()
    }

    suspend fun createParJwtAssertion(env: FlowEnvironment, clientId: String): String {
        val now = Clock.System.now()
        val notBefore = now - 1.seconds
        val exp = now + 1.days
        val keyInfo = communicationKey(env, clientId).publicKey.toJson(clientId)
        val payload = JsonObject(mapOf(
            "iss" to JsonPrimitive(CLIENT_ID),
            "sub" to JsonPrimitive(clientId),
            "cnf" to JsonObject(mapOf(
                "jwk" to keyInfo
            )),
            "nbf" to JsonPrimitive(notBefore.epochSeconds),
            "exp" to JsonPrimitive(exp.epochSeconds),
            "iat" to JsonPrimitive(now.epochSeconds)
        )).toString().toByteArray().toBase64()
        // TODO: get our JWT signed by our server once it is implemented and keys are issued
        val head = "eyJ0eXAiOiJKV1QiLCJhbGciOiJQUzI1NiIsImp3ayI6eyJrdHkiOiJSU0EiLCJlIjoiQVFBQiIsIm4iOiJpOHVFQXFFNUFoYnJmcjZLUWdfRDJTenJoOENuS2VxUUx2czNWVFRwNTdQYlZoV3l0MkhjOUV3Uzl6MnFiNHNZX1lpOVRQX24zZlBMc3M1UUtGSzZNUDcwN2hQSjlZNDlaZ3Y0cGV5ak9lWHlrYnNIWFN0ZHNkNXd0QmpoMmJoOHdMdVlTREhtekdJQ3hXWDc0QVFlS25LRTVObC15TUhoWW5PUWwwdW5OWWd6LUQteGZLRGZFR0E0LVdmQXVQQ013Uks5eGNudkM1Q0ZUZngyaTlRS0lYM25ZcWp6MFhETGVobExScGFrQ3RGS1Jjb2ZMeWlXZFN4MUVRazhfX0xCZHZBUV81R1ZtSGROU2RXQ2Z6bmlrQzVndFZGenV4cTY3dFB1ZGtVa1VKNEIxOGRRclI1dnpTaWlYYnVwc19TOWRsbW8zUm8zN3NjV2hkbUZuLVlNR1EifX0"
        val signature = "CLpyn3ZLAWC3yQsZH62zZt0w0ITeM22zwJuDC4RnGqdFbXGRi3UUYACdA02kFNIvy4lfoA1bveLFq6Y7J0D0TvGT1Ixcr353F6JqO2AjR1zSFDs-f9aYKyCrMSJ2-MfQzzL-sT0j3Pl4mV2JhUy35T08zCRxydKzHlBzS2vX8cctso-kqPVIagbo2f7JyZt68mDAAxzEfeglGlmaOEsomNDc8vegodKE6IgX7CmrEqQhntVkV2e6QvEOoEtzUbSmh5319eseUYQCtmSqO76g4tMVtVj-oE1izdtG_1sk9NdfMddeNCaNIh576cAnSg5lqYT"
        return "$head.$payload.$signature"
    }

    suspend fun generateDPoP(
        env: FlowEnvironment,
        clientId: String,
        requestUrl: String,
        dpopNonce: String,
        accessToken: String? = null
    ): String {
        val keyInfo = communicationKey(env, clientId)
        val header = buildJsonObject {
            put("typ", JsonPrimitive("dpop+jwt"))
            put("alg", JsonPrimitive(keyInfo.publicKey.curve.defaultSigningAlgorithm.jwseAlgorithmIdentifier))
            put("jwk", keyInfo.publicKey.toJson(clientId))
        }.toString().toByteArray().toBase64()
        val bodyMap = mutableMapOf(
            "htm" to JsonPrimitive("POST"),
            "htu" to JsonPrimitive(requestUrl),
            "iat" to JsonPrimitive(Clock.System.now().epochSeconds),
            "nonce" to JsonPrimitive(dpopNonce),
            "jti" to JsonPrimitive(Random.Default.nextBytes(15).toBase64())
        )
        if (accessToken != null) {
            bodyMap["ath"] = JsonPrimitive(Crypto.digest(Algorithm.SHA256, accessToken.toByteArray()).toBase64())
        }
        val body = JsonObject(bodyMap).toString().toByteArray().toBase64()
        val message = "$header.$body"
        val signature = communicationSign(env, clientId, message.toByteArray()).toBase64()
        return "$message.$signature"
    }
}