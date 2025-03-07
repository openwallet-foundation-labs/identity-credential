package org.multipaz.sdjwt

import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

/**
 * Represents a disclosure. Its canonical representation is the base64-encoding of a
 * JSON array that has the salt, key, and value, e.g.:
 *
 * ["0438o8fdslkj", "name", "Jane Doe"], or
 * ["edfj34fdslkj", "address", {"street": "A Street", "country": "USA"}]
 *
 * (The disclosure would be the base64 encoding of the UTF-8 representation of the JSON Array)
 *
 * @param disclosure the disclosure, i.e., the base64-encoding of three-element JSON array
 * @param digestAlg the algorithm to use when the disclosure needs to be hashed
 */
class Disclosure(
    private val disclosure: String,
    private val digestAlg: Algorithm = Algorithm.SHA256
) {

    val key: String
    val value: JsonElement

    init {
        val contentsString = disclosure.fromBase64Url().decodeToString()
        val contents = Json.decodeFromString(JsonArray.serializer(), contentsString).jsonArray
        key = contents[1].jsonPrimitive.content
        value = contents[2]
    }

    /**
     * Public constructor for a new disclosure, given a key and value. Disclosures
     * encode key-value pairs. A salted hash of a disclosure is included in the signed
     * portion of an SD-JWT. The preimage of this hash (the actual disclosure) is also
     * appended in the SD-JWT. @see SdJwtVerifiableCredential
     *
     * @param key the key of the key-value pair. Must be a string.
     * @param value the value in the key-value pair. This can be any type of JSON element.
     * @param alg what algorithm to use for hashing this disclosure when including it in
     *        the body of a SD-JWT
     * @param random the random instance to use for generating random salts. Doesn't need
     *        to be provided - is injectable to ease testing.
     */
    constructor(key: String,
                value: JsonElement,
                alg: Algorithm = Algorithm.SHA256,
                random: Random = Random.Default) :
            this(calculateDisclosure(key, value, random), alg) {
    }
    override fun toString(): String = disclosure

    val hash
        get() = Crypto.digest(digestAlg, toString().encodeToByteArray()).toBase64Url()

    companion object {
        private fun calculateDisclosure(key: String, value: JsonElement, random: Random): String {
            val disclosureArray = buildJsonArray {
                add(JsonPrimitive(random.getRandomSalt()))
                add(JsonPrimitive(key))
                add(value)
            }
            return disclosureArray.toString().encodeToByteArray().toBase64Url()
        }
    }
}

private fun Random.getRandomSalt(): String {
    val bytes = ByteArray(20)
    this.nextBytes(bytes)
    return bytes.toBase64Url()
}