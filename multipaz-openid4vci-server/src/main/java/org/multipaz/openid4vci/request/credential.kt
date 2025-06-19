package org.multipaz.openid4vci.request

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import kotlinx.io.bytestring.ByteString
import org.multipaz.rpc.handler.InvalidRequestException
import org.multipaz.rpc.backend.Configuration
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.multipaz.crypto.EcPublicKey
import org.multipaz.openid4vci.credential.CredentialFactory
import org.multipaz.openid4vci.credential.Openid4VciFormat
import org.multipaz.openid4vci.util.DPoPNonceException
import org.multipaz.openid4vci.util.IssuanceState
import org.multipaz.openid4vci.util.JwtCheck
import org.multipaz.openid4vci.util.OpaqueIdType
import org.multipaz.openid4vci.util.authorizeWithDpop
import org.multipaz.openid4vci.util.codeToId
import org.multipaz.openid4vci.util.extractAccessToken
import org.multipaz.openid4vci.util.validateJwt
import org.multipaz.server.getBaseUrl
import kotlin.random.Random

/**
 * Issues a credential based on DPoP authentication with access token.
 */
suspend fun credential(call: ApplicationCall) {
    val accessToken = extractAccessToken(call.request)
    val id = codeToId(OpaqueIdType.ACCESS_TOKEN, accessToken)
    val state = IssuanceState.getIssuanceState(id)
    try {
        authorizeWithDpop(
            call.request,
            state.dpopKey!!,
            state.clientId,
            state.dpopNonce,
            accessToken
        )
    } catch (err: DPoPNonceException) {
        val dpopNonce = Random.nextBytes(15)
        state.dpopNonce = ByteString(dpopNonce)
        IssuanceState.updateIssuanceState(id, state)
        call.response.header("DPoP-Nonce", dpopNonce.toBase64Url())
        call.response.header("WWW-Authenticate", "DPoP error=\"use_dpop_nonce\"")
        call.respondText(status = HttpStatusCode.Unauthorized, text = "")
        return
    }
    val nonce = state.cNonce?.toByteArray()?.toBase64Url()  // credential nonce
    state.dpopNonce = null
    state.cNonce = null
    IssuanceState.updateIssuanceState(id, state)
    val requestString = call.receiveText()
    val json = Json.parseToJsonElement(requestString) as JsonObject
    val format = Openid4VciFormat.fromJson(json)
    val factory = CredentialFactory.byOfferId.values.find { factory ->
        (format == null || factory.format == format) && factory.scope == state.scope
    }
    if (factory == null) {
        throw IllegalStateException(
            "No credential can be created for scope '${state.scope}' and the given format")
    }
    if (state.clientAttestationKey == null && factory.requireClientAttestation) {
        throw InvalidRequestException("this credential type requires client attestation")
    }
    if (factory.cryptographicBindingMethods.isEmpty()) {
        // Keyless credential: no need for proof/proofs parameter.
        val credential = factory.makeCredential(state, null)
        call.respondText(
            text = buildJsonObject {
                putJsonArray("credentials") {
                    addJsonObject {
                        put("credential", credential)
                    }
                }
            }.toString(),
            contentType = ContentType.Application.Json
        )
        return
    }

    if (nonce == null) {
        throw InvalidRequestException("fresh c_nonce was not requested by the client")
    }

    val proofsObj = json["proofs"]?.jsonObject
    val proofs: JsonArray
    val proofType: String
    if (proofsObj == null) {
        val proof = json["proof"]
            ?: throw InvalidRequestException("neither 'proof' or 'proofs' parameter provided")
        proofType = proof.jsonObject["proof_type"]?.jsonPrimitive?.content!!
        proofs = buildJsonArray { add(proof.jsonObject[proofType]!!) }
    } else {
        proofType = if (proofsObj.containsKey("attestation")) {
            // prefer attestation
            "attestation"
        } else if (proofsObj.containsKey("jwt")) {
            "jwt"
        } else {
            throw InvalidRequestException("Unsupported proof type")
        }
        proofs = proofsObj[proofType]!!.jsonArray
        if (proofs.size == 0) {
            throw InvalidRequestException("'proofs' is empty")
        }
    }

    val authenticationKeys = when (proofType) {
        "attestation" -> {
            proofs.flatMap { proof ->
                val body = validateJwt(
                    jwt = proof.jsonPrimitive.content,
                    jwtName = "Key attestation",
                    publicKey = null,
                    checks = mapOf(
                        JwtCheck.TYP to "keyattestation+jwt",
                        JwtCheck.TRUST to "key_attestation",
                        JwtCheck.NONCE to nonce
                    )
                )
                body["attested_keys"]!!.jsonArray.map { key ->
                    EcPublicKey.fromJwk(key.jsonObject)
                }
            }
        }
        "jwt" -> {
            if (factory.requireKeyAttestation) {
                throw InvalidRequestException("jwt proof cannot be used for this credential")
            }
            val baseUrl = BackendEnvironment.getBaseUrl()
            proofs.map { proof ->
                val jwt = proof.jsonPrimitive.content
                val parts = jwt.split(".")
                if (parts.size != 3) {
                    throw InvalidRequestException("invalid value for 'proof.jwt' parameter")
                }
                val head = Json.parseToJsonElement(String(parts[0].fromBase64Url())) as JsonObject
                val authenticationKey = EcPublicKey.fromJwk(head["jwk"]!!.jsonObject)
                validateJwt(
                    jwt = proof.jsonPrimitive.content,
                    jwtName = "Key attestation",
                    publicKey = authenticationKey,
                    checks = mapOf(
                        JwtCheck.TYP to "openid4vci-proof+jwt",
                        JwtCheck.NONCE to nonce,
                        JwtCheck.AUD to baseUrl
                    )
                )
                authenticationKey
            }
        }
        else -> {
            throw InvalidRequestException("unsupported proof type")
        }
    }

    val credentials = authenticationKeys.map { key -> factory.makeCredential(state, key) }

    val result =
        buildJsonObject {
            putJsonArray("credentials") {
                for (credential in credentials) {
                    addJsonObject {
                        put("credential", credential)
                    }
                }
            }
        }
    call.respondText(
        text = Json.encodeToString(result),
        contentType = ContentType.Application.Json
    )
}

