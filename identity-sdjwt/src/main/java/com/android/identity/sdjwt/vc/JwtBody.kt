package com.android.identity.sdjwt.vc

import com.android.identity.crypto.Algorithm
import com.android.identity.sdjwt.util.JsonWebKey
import com.android.identity.sdjwt.util.JwtJsonObject
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

class JwtBody(
    val disclosureHashes: Array<String>,
    val sdHashAlg: Algorithm,
    val issuer: String,
    val docType: String,
    val timeSigned: Instant? = null,
    val timeValidityBegin: Instant? = null,
    val timeValidityEnd: Instant? = null,
    val publicKey: JsonWebKey? = null): JwtJsonObject() {
    override fun buildJson(): JsonObjectBuilder.() -> Unit = {
        put("_sd", JsonArray(disclosureHashes.map { disclosure -> JsonPrimitive(disclosure) }))
        put("_sd_alg", JsonPrimitive(sdHashAlg.hashAlgorithmIdentifier))
        put("iss", JsonPrimitive(issuer))
        put("vct", JsonPrimitive(docType))
        timeSigned?.let { put("iat", JsonPrimitive(it.epochSeconds)) }
        timeValidityBegin?.let { put("nbf", JsonPrimitive(it.epochSeconds)) }
        timeValidityEnd?.let { put("exp", JsonPrimitive(it.epochSeconds)) }
        publicKey?.let { put("cnf", publicKey.asJwk) }
    }

    companion object {
        fun fromString(input: String): JwtBody {
            val jsonObj = parse(input)
            val disclosureHashes = getJsonArray(jsonObj, "_sd")
                .map { it.jsonPrimitive.content }
                .toTypedArray()
            val sdHashAlg = getString(jsonObj, "_sd_alg")
            val issuer = getString(jsonObj, "iss")
            val docType = getString(jsonObj, "vct")
            val timeSigned = getLongOrNull(jsonObj, "iat")?.let { Instant.fromEpochSeconds(it)}
            val timeValidityBegin = getLongOrNull(jsonObj, "nbf")?.let { Instant.fromEpochSeconds(it)}
            val timeValidityEnd = getLongOrNull(jsonObj, "exp")?.let { Instant.fromEpochSeconds(it)}
            val publicKey = getJsonObjectOrNull(jsonObj, "cnf")?.let { JsonWebKey(it)}

            return JwtBody(
                disclosureHashes,
                Algorithm.fromHashAlgorithmIdentifier(sdHashAlg),
                issuer,
                docType,
                timeSigned,
                timeValidityBegin,
                timeValidityEnd,
                publicKey
            )
        }
    }
}