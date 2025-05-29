package org.multipaz.sdjwt

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.JsonWebSignature
import org.multipaz.crypto.SignatureVerificationException
import org.multipaz.crypto.X509CertChain
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
import kotlin.random.Random

private const val TAG = "SdJwt"

/**
 * A SD-JWT according to
 * [draft-ietf-oauth-selective-disclosure-jwt](https://datatracker.ietf.org/doc/draft-ietf-oauth-selective-disclosure-jwt/).
 *
 * This class is immutable.
 *
 * @param compactSerialization the compact serialization of the SD-JWT or SD-JWT+KB.
 * @throws IllegalArgumentException if the given compact serialization is malformed.
 */
class SdJwt(
    val compactSerialization: String
) {
    private lateinit var header: String
    private lateinit var body: String
    private lateinit var signature: String

    private lateinit var hashToDisclosureString: Map<String, String>

    /** The digest algorithm used. */
    lateinit var digestAlg: Algorithm

    /** The header of the issuer-signed JWT. */
    val jwtHeader: JsonObject
        get() {
            return Json.decodeFromString(JsonObject.serializer(), header.fromBase64Url().decodeToString())
        }

    /** The body of the issuer-signed JWT. */
    lateinit var jwtBody: JsonObject

    /**
     * The `x5c` header element from the SD-JWT's header, if present.
     */
    val x5c: X509CertChain?
        get() {
            val info = JsonWebSignature.getInfo(JsonPrimitive("$header.$body.$signature"))
            return info.x5c
        }

    /** The disclosures in the SD-JWT. */
    val disclosures: List<String>
        get() {
            return hashToDisclosureString.values.toList()
        }

    init {
        if (!compactSerialization.endsWith('~')) {
            throw IllegalArgumentException("Given compact serialization doesn't end with ~")
        }

        val splits = compactSerialization.split("~")
        val jwtSplits = splits[0].split(".")
        if (jwtSplits.size != 3) {
            throw IllegalArgumentException("JWT in SD-JWT didn't consist of three parts: ${splits[0]}")
        }
        header = jwtSplits[0]
        body = jwtSplits[1]
        signature = jwtSplits[2]

        jwtBody = Json.decodeFromString(JsonObject.serializer(), body.fromBase64Url().decodeToString())
        digestAlg = jwtBody["_sd_alg"]?.let {
            Algorithm.fromHashAlgorithmIdentifier(it.jsonPrimitive.content)
        } ?: Algorithm.SHA256

        val htds = mutableMapOf<String, String>()
        for (n in IntRange(1, splits.size - 2)) {
            val disclosureString = splits[n]
            val hash = Crypto.digest(digestAlg, disclosureString.encodeToByteArray()).toBase64Url()
            htds.put(hash, disclosureString)
        }
        hashToDisclosureString = htds
    }

    /**
     * Verifies a SD-JWT according to Section 7.1 of the SD-JWT specification.
     *
     * @param issuerKey the issuer's key to use for verification.
     * @return the processed SD-JWT payload.
     * @throws SignatureVerificationException if the issuer signature or key-binding signature failed to validate.
     */
    fun verify(
        issuerKey: EcPublicKey,
    ): JsonObject {
        // TODO: make sure we perform all checks in Section 7.1
        try {
            JsonWebSignature.verify(JsonPrimitive("$header.$body.$signature"), issuerKey)
        } catch (e: Throwable) {
            throw SignatureVerificationException("Error validating issuer signature", e)
        }
        return processObject(
            obj = jwtBody,
            hashToDisclosureString = hashToDisclosureString,
            path = mutableListOf(),
            visitor = { path, value, disclosure -> }
        )
    }

    /**
     * Generates a new SD-JWT by filtering which claims should be included,
     *
     * @param pathsToInclude list of paths describing which claims to include.
     * @return the resulting [SdJwt].
     */
    fun filter(
        pathsToInclude: List<JsonArray>
    ): SdJwt {
        val pathToIncludeStrings = pathsToInclude.map { it.joinToString(".") }

        return filter { path: JsonArray, value: JsonElement ->
            val pathOfDisclosure = path.toList().joinToString(".")
            for (pathToIncludeString in pathToIncludeStrings) {
                if (pathOfDisclosure.startsWith(pathToIncludeString) ||
                    pathToIncludeString.startsWith(pathOfDisclosure)) {
                    return@filter true
                }
            }
            false
        }
    }

    /**
     * Generates a new SD-JWT by remove disclosures.
     *
     * @param includeDisclosure a function to determine if a given disclosure should be included.
     * @return the resulting [SdJwt].
     */
    fun filter(
        includeDisclosure: (path: JsonArray, value: JsonElement) -> Boolean
    ): SdJwt {
        val disclosureStringsToSkip = mutableSetOf<String>()
        processObject(
            obj = jwtBody,
            hashToDisclosureString = hashToDisclosureString,
            path = mutableListOf(),
            visitor = { path, value, disclosureString ->
                if (disclosureString != null) {
                    val include = includeDisclosure(path, value)
                    if (!include) {
                        disclosureStringsToSkip.add(disclosureString)
                    }
                }
            }
        )

        val sb = StringBuilder("$header.$body.$signature~")
        val splits = compactSerialization.split("~")
        for (n in IntRange(1, splits.size - 2)) {
            val disclosureString = splits[n]
            if (!disclosureStringsToSkip.contains(disclosureString)) {
                sb.append(disclosureString)
                sb.append('~')
            }
        }
        return SdJwt(sb.toString())
    }

    /**
     * Presents an SD-JWT to a verifier.
     *
     * This generates a SD-JWT+KB from the SD-JWT by simply appending a Key-Binding JWT.
     *
     * @param kbKey the private part of key corresponding to `cnf` claim in the body of the Issuer-signed JWT.
     * @param kbAlgorithm the algorithm to use for signing, e.g. [Algorithm.ESP256].
     * @param nonce the nonce, obtained from the verifier.
     * @param audience the audience, obtained from the verifier.
     * @param creationTime the time the presentation was made.
     */
    fun present(
        kbKey: EcPrivateKey,
        kbAlgorithm: Algorithm,
        nonce: String,
        audience: String,
        creationTime: Instant = Clock.System.now()
    ): SdJwtKb {
        val kbBody = buildJsonObject {
            put("nonce", JsonPrimitive(nonce))
            put("aud", JsonPrimitive(audience))
            put("iat", JsonPrimitive(creationTime.toEpochMilliseconds() / 1000))
            put("sd_hash", JsonPrimitive(Crypto.digest(digestAlg, compactSerialization.encodeToByteArray()).toBase64Url()))
        }
        val kbJwt = JsonWebSignature.sign(
            key = kbKey,
            signatureAlgorithm = kbAlgorithm,
            claimsSet = kbBody,
            type = "kb+jwt",
            x5c = null
        ).jsonPrimitive.content
        return SdJwtKb(compactSerialization + kbJwt)
    }

    companion object {
        /**
         * Creates a SD-JWT.
         *
         * This implementation uses recursive disclosures for all claims in the [claims] parameter.
         *
         * @param issuerKey the key to sign the issuerSigned JWT with.
         * @param issuerAlgorithm the algorithm to use for signing, e.g. [Algorithm.ESP256].
         * @param issuerCertChain if set, this will be included as a `x5c` header element in the Issuer-signed JWT.
         * @param kbKey if set, a `cnf` claim with this public key will be included in the Issuer-signed JWT.
         * @param claims the object with claims that can be selectively disclosed.
         * @param nonSdClaims claims to include in the Issuer-signed JWT which are always disclosed.
         * @param digestAlgorithm the hash algorithm to use, e.g. [Algorithm.SHA256].
         * @param random the [Random] to use to generate salts.
         * @param saltSizeNumBits number of bits to use for each salt.
         */
        fun create(
            issuerKey: EcPrivateKey,
            issuerAlgorithm: Algorithm,
            issuerCertChain: X509CertChain?,
            kbKey: EcPublicKey?,
            claims: JsonObject,
            nonSdClaims: JsonObject,
            digestAlgorithm: Algorithm = Algorithm.SHA256,
            random: Random = Random.Default,
            saltSizeNumBits: Int = 128
        ): SdJwt {
            // TODO: decoy digests

            val disclosures = mutableListOf<JsonArray>()

            val issuerSignedJwtBody = buildJsonObject {
                for (claim in nonSdClaims) {
                    put(claim.key, claim.value)
                }

                val hashes = mutableListOf<String>()
                for (claim in claims) {
                    insertClaim(
                        disclosures = disclosures,
                        hashes = hashes,
                        random = random,
                        saltSizeNumBits = saltSizeNumBits,
                        digestAlg = digestAlgorithm,
                        claimName = claim.key,
                        claimValue = claim.value
                    )
                }
                putJsonArray("_sd") {
                    for (hash in hashes) {
                        add(JsonPrimitive(hash))
                    }
                }

                put("_sd_alg", JsonPrimitive(digestAlgorithm.hashAlgorithmName))

                if (kbKey != null) {
                    putJsonObject("cnf") {
                        put("jwk", kbKey.toJwk())
                    }
                }
            }
            val sb = StringBuilder()
            sb.append(
                JsonWebSignature.sign(
                    key = issuerKey,
                    signatureAlgorithm = issuerAlgorithm,
                    claimsSet = issuerSignedJwtBody,
                    type = "dc+sd-jwt",
                    x5c = issuerCertChain
                ).jsonPrimitive.content
            )
            sb.append('~')
            for (disclosure in disclosures) {
                sb.append(disclosure.toString().encodeToByteArray().toBase64Url())
                sb.append('~')
            }
            return SdJwt(sb.toString())
        }


    }
}

private fun insertClaim(
    disclosures: MutableList<JsonArray>,
    hashes: MutableList<String>,
    random: Random,
    saltSizeNumBits: Int,
    digestAlg: Algorithm,
    claimName: String?,
    claimValue: JsonElement
) {
    if (claimValue is JsonPrimitive) {
        val disclosure = buildJsonArray {
            add(JsonPrimitive(random.getRandomSalt(saltSizeNumBits)))
            claimName?.let { add(JsonPrimitive(it)) }
            add(claimValue)
        }
        val disclosureString = disclosure.toString().encodeToByteArray().toBase64Url()
        val hash = Crypto.digest(digestAlg, disclosureString.encodeToByteArray()).toBase64Url()
        disclosures.add(disclosure)
        hashes.add(hash)
    } else if (claimValue is JsonObject) {
        val subClaimHashes = mutableListOf<String>()
        for ((subClaimName, subClaimValue) in claimValue.entries) {
            insertClaim(
                disclosures = disclosures,
                hashes = subClaimHashes,
                random = random,
                saltSizeNumBits = saltSizeNumBits,
                digestAlg = digestAlg,
                claimName = subClaimName,
                claimValue = subClaimValue
            )
        }
        val mappedClaimValue = buildJsonObject {
            putJsonArray("_sd") {
                subClaimHashes.forEach { add(JsonPrimitive(it)) }
            }
        }
        val disclosure = buildJsonArray {
            add(JsonPrimitive(random.getRandomSalt(saltSizeNumBits)))
            claimName?.let { add(JsonPrimitive(it)) }
            add(mappedClaimValue)
        }
        val disclosureString = disclosure.toString().encodeToByteArray().toBase64Url()
        val hash = Crypto.digest(digestAlg, disclosureString.encodeToByteArray()).toBase64Url()
        disclosures.add(disclosure)
        hashes.add(hash)
    } else if (claimValue is JsonArray) {
        val mappedClaimValue = buildJsonArray {
            for (arrayElemValue in claimValue) {
                val subClaimHashes = mutableListOf<String>()
                insertClaim(
                    disclosures = disclosures,
                    hashes = subClaimHashes,
                    random = random,
                    saltSizeNumBits = saltSizeNumBits,
                    digestAlg = digestAlg,
                    claimName = null,
                    claimValue = arrayElemValue
                )
                addJsonObject {
                    put("...", JsonPrimitive(subClaimHashes[0]))
                }
            }
        }
        val disclosure = buildJsonArray {
            add(JsonPrimitive(random.getRandomSalt(saltSizeNumBits)))
            claimName?.let { add(JsonPrimitive(it)) }
            add(mappedClaimValue)
        }
        val disclosureString = disclosure.toString().encodeToByteArray().toBase64Url()
        val hash = Crypto.digest(digestAlg, disclosureString.encodeToByteArray()).toBase64Url()
        disclosures.add(disclosure)
        hashes.add(hash)
    }
}

private fun Random.getRandomSalt(
    saltSizeNumBits: Int,
): String {
    val bytes = ByteArray(saltSizeNumBits/8)
    this.nextBytes(bytes)
    return bytes.toBase64Url()
}

private fun process(
    elem: JsonElement,
    hashToDisclosureString: Map<String, String>,
    path: List<JsonElement>,
    visitor: (path: JsonArray, value: JsonElement, disclosureString: String?) -> Unit,
): JsonElement {
    val processedElem = if (elem is JsonObject) {
        processObject(elem, hashToDisclosureString, path, visitor)
    } else if (elem is JsonArray) {
        processArray(elem, hashToDisclosureString, path, visitor)
    } else {
        elem
    }
    return processedElem
}

private fun processObject(
    obj: JsonObject,
    hashToDisclosureString: Map<String, String>,
    path: List<JsonElement>,
    visitor: (path: JsonArray, value: JsonElement, disclosureString: String?) -> Unit,
): JsonObject {
    return buildJsonObject {
        for (claimName in obj.keys) {
            if (claimName == "_sd" || claimName == "_sd_alg") {
                // skip
            } else {
                val claimValue = obj[claimName]!!
                val subPath = path + JsonPrimitive(claimName)
                visitor(JsonArray(subPath), claimValue, null)
                put(claimName, process(claimValue, hashToDisclosureString, subPath, visitor))
            }
        }

        val embeddedDigests = mutableListOf<String>()
        val _sd = obj["_sd"]
        if (_sd != null) {
            for (digest in _sd.jsonArray) {
                embeddedDigests.add(digest.jsonPrimitive.content)
            }
        }
        for (ed in embeddedDigests) {
            val disclosureString = hashToDisclosureString[ed]
            if (disclosureString != null) {
                val disclosure = Json.decodeFromString(
                    JsonArray.serializer(),
                    disclosureString.fromBase64Url().decodeToString()
                ).jsonArray
                val claimName = disclosure[1].jsonPrimitive.content
                val claimValue = disclosure[2]
                if (claimName == "_sd" || claimName == "...") {
                    throw IllegalArgumentException("Illegal disclosure claim name")
                }
                if (obj.get(claimName) != null) {
                    throw IllegalArgumentException("Claim $claimName already exists")
                }
                val subPath = path + JsonPrimitive(claimName)
                visitor(JsonArray(subPath), claimValue, disclosureString)
                put(claimName, process(claimValue, hashToDisclosureString, subPath, visitor))
            }
        }
    }
}

private fun processArray(
    array: JsonArray,
    hashToDisclosureString: Map<String, String>,
    path: List<JsonElement>,
    visitor: (path: JsonArray, value: JsonElement, disclosureString: String?) -> Unit,
): JsonArray {
    return buildJsonArray {
        for (n in IntRange(0, array.size - 1)) {
            var claimValue = array[n]
            if (claimValue is JsonObject && claimValue.keys.size == 1 && claimValue.keys.first() == "...") {
                val digest = claimValue["..."]!!.jsonPrimitive.content
                val disclosureString = hashToDisclosureString[digest]
                if (disclosureString != null) {
                    val disclosure = Json.decodeFromString(
                        JsonArray.serializer(),
                        disclosureString.fromBase64Url().decodeToString()
                    ).jsonArray
                    claimValue = disclosure[1]
                    val subPath = path + JsonPrimitive(n)
                    visitor(JsonArray(subPath), claimValue, disclosureString)
                    add(process(claimValue, hashToDisclosureString, subPath, visitor))
                }
            } else {
                val subPath = path + JsonPrimitive(n)
                visitor(JsonArray(subPath), claimValue, null)
                add(process(claimValue, hashToDisclosureString, subPath, visitor))
            }
        }
    }
}
