package org.multipaz.sdjwt.vc

import org.multipaz.crypto.Algorithm
import org.multipaz.sdjwt.util.JsonWebKey
import org.multipaz.sdjwt.util.JwtJsonObject
import org.multipaz.sdjwt.util.getJsonArray
import org.multipaz.sdjwt.util.getJsonObjectOrNull
import org.multipaz.sdjwt.util.getLongOrNull
import org.multipaz.sdjwt.util.getString
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

class JwtBody(
    val disclosureHashes: List<String>,
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
            val disclosureHashes = jsonObj.getJsonArray("_sd")
                .map { it.jsonPrimitive.content }
            val sdHashAlg = jsonObj.getString("_sd_alg")
            val issuer = jsonObj.getString("iss")
            val docType = jsonObj.getString("vct")
            val timeSigned = jsonObj.getLongOrNull("iat")?.let { Instant.fromEpochSeconds(it)}
            val timeValidityBegin = jsonObj.getLongOrNull("nbf")?.let { Instant.fromEpochSeconds(it)}
            val timeValidityEnd = jsonObj.getLongOrNull("exp")?.let { Instant.fromEpochSeconds(it)}
            val publicKey = jsonObj.getJsonObjectOrNull("cnf")?.let { JsonWebKey(it)}

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