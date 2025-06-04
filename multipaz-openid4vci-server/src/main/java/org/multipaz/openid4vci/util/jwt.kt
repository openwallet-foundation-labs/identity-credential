package org.multipaz.openid4vci.util

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.io.bytestring.buildByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.asn1.OID
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.EcSignature
import org.multipaz.crypto.SignatureVerificationException
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.getTable
import org.multipaz.rpc.cache
import org.multipaz.rpc.handler.InvalidRequestException
import org.multipaz.storage.KeyExistsStorageException
import org.multipaz.storage.StorageTableSpec
import org.multipaz.util.fromBase64Url
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

enum class JwtCheck(
    val fieldNameValueCheck: String? = null,
    val headerFiled: Boolean = false
) {
    JTI,  // value is jti partition name (typically clientId)
    X5C,  // value is CA name to use for x509 certificate chain validation
    NONCE("nonce"),
    TYP("typ", true),
    AUD("aud"),
    ISS("iss"),
    SUB("sub"),
    HTU("htu"),
    HTM("htm"),
    ATH("ath"),
}

suspend fun validateJwt(
    jwt: String,
    jwtName: String,
    publicKey: EcPublicKey?,
    checks: Map<JwtCheck, String> = mapOf(),
    algorithm: Algorithm? = publicKey?.curve?.defaultSigningAlgorithmFullySpecified,
    maxValidity: Duration = 10.hours
): JsonObject {
    val parts = jwt.split('.')
    if (parts.size != 3) {
        throw InvalidRequestException("$jwtName: invalid")
    }
    val header = Json.parseToJsonElement(
        parts[0].fromBase64Url().decodeToString()
    ).jsonObject
    val body = Json.parseToJsonElement(
        parts[1].fromBase64Url().decodeToString()
    ).jsonObject

    val expiration = if (body.containsKey("exp")) {
        val exp = body["exp"]
        if (exp !is JsonPrimitive || exp.isString) {
            throw InvalidRequestException("$jwtName: 'exp' is invalid")
        }
        exp.content.toLong()
    } else {
        val iat = body["iat"]
        if (iat !is JsonPrimitive || iat.isString) {
            throw InvalidRequestException("$jwtName: 'exp' is missing and 'iat' is missing or invalid")
        }
        iat.content.toLong() + maxValidity.inWholeSeconds - 5
    }

    val now = Clock.System.now().epochSeconds
    if (expiration < now) {
        throw InvalidRequestException("$jwtName: expired")
    }
    if (expiration > now + maxValidity.inWholeSeconds) {
        throw InvalidRequestException("$jwtName: expiration is too far in the future")
    }

    for ((check, expectedValue) in checks) {
        val fieldName = check.fieldNameValueCheck
        if (fieldName != null) {
            val part = if (check.headerFiled) header else body
            val fieldValue = part[fieldName]
            if (fieldValue !is JsonPrimitive || fieldValue.content != expectedValue) {
                throw InvalidRequestException("$jwtName: '$fieldName' is incorrect or missing")
            }
        }
    }

    val caName = checks[JwtCheck.X5C]
    val (key, alg) = if (caName == null) {
        Pair(publicKey!!, algorithm!!)
    } else {
        val issuer = body["iss"]?.jsonPrimitive?.content
        if (header.containsKey("x5c")) {
            val x5c = header["x5c"]!!
            val certificateChain = X509CertChain.fromX5c(x5c)
            if (!certificateChain.validate()) {
                throw InvalidRequestException("$jwtName: 'x5c' certificate chain")
            }
            val first = certificateChain.certificates.first()
            if (issuer != null) {
                // If 'iss' is specified, it should match leaf certificate subject CN.
                val certificateSubject = first.subject.components[OID.COMMON_NAME.oid]?.value
                    ?: throw InvalidRequestException("$jwtName: no CN entry in 'x5c' certificate")
                if (issuer != certificateSubject) {
                    throw InvalidRequestException("$jwtName: 'iss' does not match 'x5c' certificate subject CN")
                }
            }
            val caCertificate = certificateChain.certificates.last()
            val caKey = caPublicKey(
                keyId = caCertificate.issuer.components[OID.COMMON_NAME.oid]?.value
                    ?: throw InvalidRequestException("$jwtName: No CN entry in 'x5c' CA"),
                caName = caName
            )
            if (caCertificate.ecPublicKey != caKey) {
                throw InvalidRequestException("$jwtName: CA key mismatch")
            }
            Pair(first.ecPublicKey, first.signatureAlgorithm)
        } else {
            val keyId = header["kid"]?.jsonPrimitive?.content ?: issuer
                ?: throw InvalidRequestException(
                    "$jwtName: either 'iss', 'kid', or 'x5c' must be specified")
            val caKey = caPublicKey(keyId, caName)
            Pair(caKey, caKey.curve.defaultSigningAlgorithmFullySpecified)
        }
    }

    val signature = EcSignature.fromCoseEncoded(parts[2].fromBase64Url())
    try {
        val message = jwt.substring(0, jwt.length - parts[2].length - 1)
        Crypto.checkSignature(key, message.encodeToByteArray(), alg, signature)
    } catch (e: SignatureVerificationException) {
        throw IllegalArgumentException("Invalid JWT signature", e)
    }

    val jtiPartition = checks[JwtCheck.JTI]
    if (jtiPartition != null) {
        val jti = body["jti"]
        if (jti !is JsonPrimitive || !jti.isString) {
            throw InvalidRequestException("$jwtName: 'jti' is missing or invalid")
        }
        try {
            BackendEnvironment.getTable(jtiTableSpec).insert(
                key = jti.content,
                data = buildByteString { },
                partitionId = jtiPartition,
                expiration = Instant.fromEpochSeconds(expiration)
            )
        } catch (err: KeyExistsStorageException) {
            throw InvalidRequestException("$jwtName: given 'jti' value was used before")
        }
    }

    return body
}

private suspend fun caPublicKey(
    keyId: String,
    caName: String
): EcPublicKey {
    val escapedKeyId = keyId
        .replace("%", "%25")
        .replace("/", "%2F")
    val caPath = "$caName/$escapedKeyId"
    val caKey =
        BackendEnvironment.cache(
            CAPublicKey::class,
            caPath
        ) { configuration, resources ->
            val certificateStore = configuration.getValue("trustRoot")
            if (certificateStore != null) {
                val pemFile = File("$caPath.pem")
                if (pemFile.canRead()) {
                    return@cache CAPublicKey(X509Cert.fromPem(pemFile.readText()).ecPublicKey)
                }
                val jwkFile = File("$caPath.jwk")
                if (jwkFile.canRead()) {
                    return@cache CAPublicKey(EcPublicKey.fromJwk(
                        Json.parseToJsonElement(jwkFile.readText()).jsonObject))
                }
                throw InvalidRequestException("CA not registered: $caPath")
            }
            val cert = resources.getStringResource("trust/$caPath.pem")
            if (cert != null) {
                return@cache CAPublicKey(X509Cert.fromPem(cert).ecPublicKey)
            }
            val jwk = resources.getStringResource("trust/$caPath.jwk")
            if (jwk != null) {
                return@cache CAPublicKey(EcPublicKey.fromJwk(
                    Json.parseToJsonElement(jwk).jsonObject))
            }
            throw InvalidRequestException("CA not registered: $caPath")
        }
    return caKey.publicKey
}

private val jtiTableSpec = StorageTableSpec(
    name = "UsedJti",
    supportPartitions = true,
    supportExpiration = true
)

private data class CAPublicKey(val publicKey: EcPublicKey)
