package org.multipaz.claim

import org.multipaz.documenttype.DocumentAttribute
import org.multipaz.request.RequestedClaim
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.multipaz.request.JsonRequestedClaim
import org.multipaz.request.MdocRequestedClaim

/**
 * Base class used for representing a claim.
 *
 * @property displayName a short human readable string describing the claim.
 * @property attribute a [DocumentAttribute], if the claim is for a well-known attribute.
 */
sealed class Claim(
    open val displayName: String,
    open val attribute: DocumentAttribute?
) {
    /**
     * Returns the value of a claim as a human readable string.
     *
     * If [Claim.attribute] is set, its type is used when rendering for example to resolve integer options to strings.
     *
     * @param timeZone the time zone to use for rendering dates and times.
     * @return textual representation of the claim.
     */
    abstract fun render(timeZone: TimeZone = TimeZone.currentSystemDefault()): String
}

/**
 * Resolve a claim request against a list of claims.
 *
 * @param requestedClaim the claim that is requested.
 * @return a [Claim] instance with the value or `null` if not available in the list of claims.
 */
fun List<Claim>.findMatchingClaim(requestedClaim: RequestedClaim): Claim? {
    return when (requestedClaim) {
        is MdocRequestedClaim -> {
            mdocFindMatchingClaimValue(this, requestedClaim)
        }
        is JsonRequestedClaim -> {
            jsonFindMatchingClaimValue(this, requestedClaim)
        }
    }
}

/**
 * Resolves a list of claim requests against a list of claims
 *
 * @param requestedClaims a list of claim requests.
 * @return a list of [Claim] for the resolved claim requests.
 */
fun List<Claim>.findMatchingClaims(requestedClaims: List<RequestedClaim>): List<Claim> {
    return requestedClaims.mapNotNull { findMatchingClaim(it) }
}


private fun Claim.filterValueMatch(
    values: JsonArray?,
): Claim? {
    if (values == null) {
        return this
    }
    when (this) {
        is JsonClaim -> if (values.contains(value)) return this
        is MdocClaim -> if (values.contains(value.toJson())) return this
    }
    return null
}

private fun mdocFindMatchingClaimValue(
    claims: List<Claim>,
    requestedClaim: RequestedClaim
): MdocClaim? {
    if (requestedClaim !is MdocRequestedClaim) {
        return null
    }
    for (credentialClaim in claims) {
        credentialClaim as MdocClaim
        if (credentialClaim.namespaceName == requestedClaim.namespaceName &&
            credentialClaim.dataElementName == requestedClaim.dataElementName) {
            return credentialClaim.filterValueMatch(requestedClaim.values) as MdocClaim?
        }
    }
    return null
}

private fun jsonFindMatchingClaimValue(
    claims: List<Claim>,
    requestedClaim: RequestedClaim,
): JsonClaim? {
    if (requestedClaim !is JsonRequestedClaim) {
        return null
    }
    check(requestedClaim.claimPath.size >= 1)
    check(requestedClaim.claimPath[0].isString)
    var ret: JsonClaim? = null
    for (credentialClaim in claims) {
        credentialClaim as JsonClaim
        if (credentialClaim.claimPath[0].jsonPrimitive.content == requestedClaim.claimPath[0].jsonPrimitive.content) {
            ret = credentialClaim
            break
        }
    }
    if (ret == null) {
        return null
    }
    if (requestedClaim.claimPath.size == 1) {
        return ret.filterValueMatch(requestedClaim.values) as JsonClaim?
    }

    // OK, path>1 so we descend into the object...
    var currentObject: JsonElement? = ret.value
    var currentAttribute: DocumentAttribute? = ret.attribute

    for (n in IntRange(1, requestedClaim.claimPath.size - 1)) {
        val pathComponent = requestedClaim.claimPath[n]
        if (pathComponent.isString) {
            if (currentObject is JsonArray) {
                val newObject = buildJsonArray {
                    for (element in currentObject.jsonArray) {
                        add(element.jsonObject[pathComponent.jsonPrimitive.content]!!)
                    }
                }
                currentObject = newObject
                currentAttribute = null
            } else if (currentObject is JsonObject) {
                currentObject = currentObject.jsonObject[pathComponent.jsonPrimitive.content]
                currentAttribute = currentAttribute?.embeddedAttributes?.find {
                    it.identifier == pathComponent.jsonPrimitive.content
                }
            } else {
                throw Error("Can only select from object or array of objects")
            }
        } else if (pathComponent.isNumber) {
            currentObject = currentObject!!.jsonArray[pathComponent.jsonPrimitive.int]
            currentAttribute = null
        } else if (pathComponent.isNull) {
            currentObject = currentObject!!.jsonArray
            currentAttribute = null
        }
    }
    if (currentObject == null) {
        return null
    }

    val (displayName, attribute) = if (currentAttribute != null) {
        Pair(currentAttribute.displayName, currentAttribute)
    } else {
        // Fall back, use path elements as the display name (e.g. address.street_address)
        val combinedPath = requestedClaim.claimPath.joinToString(".", transform = { it.jsonPrimitive.content })
        Pair(combinedPath, null)
    }
    return JsonClaim(
        displayName = displayName,
        attribute = attribute,
        claimPath = requestedClaim.claimPath,
        value = currentObject
    ).filterValueMatch(requestedClaim.values) as JsonClaim?
}


private val JsonElement.isNull: Boolean
    get() = this is JsonNull

private val JsonElement.isNumber: Boolean
    get() = this is JsonPrimitive && !isString && longOrNull != null

private val JsonElement.isString: Boolean
    get() = this is JsonPrimitive && isString
