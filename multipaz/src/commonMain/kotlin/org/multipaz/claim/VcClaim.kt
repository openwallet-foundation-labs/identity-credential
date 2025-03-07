package org.multipaz.claim

import org.multipaz.datetime.formatLocalized
import org.multipaz.documenttype.DocumentAttribute
import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.util.fromBase64Url
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/**
 * A claim in a VC credential.
 *
 * @property claimName the claim name.
 * @property value the value of the claim
 */
data class VcClaim(
    override val displayName: String,
    override val attribute: DocumentAttribute?,
    val claimName: String,
    val value: JsonElement
) : Claim(displayName, attribute) {

    override fun render(timeZone: TimeZone): String {
        val str = try {
            if (attribute == null) {
                if (value is JsonPrimitive) {
                    value.jsonPrimitive.content
                } else {
                    value.toString()
                }
            } else {
                when (attribute!!.type) {
                    // TODO: translations
                    DocumentAttributeType.Boolean -> {
                        if (value.jsonPrimitive.boolean) {
                            "True"
                        } else {
                            "False"
                        }
                    }

                    DocumentAttributeType.Blob -> {
                        "Blob of ${value.jsonPrimitive.content.length} bytes"
                    }

                    DocumentAttributeType.ComplexType -> {
                        value.toString()
                    }

                    DocumentAttributeType.Date -> {
                        val d = LocalDate.parse(value.jsonPrimitive.content)
                        d.formatLocalized()
                    }

                    DocumentAttributeType.DateTime -> {
                        val pointInTime = Instant.parse(value.jsonPrimitive.content)
                        val dt = pointInTime.toLocalDateTime(timeZone)
                        dt.formatLocalized()
                    }

                    is DocumentAttributeType.IntegerOptions -> {
                        val type = attribute!!.type as DocumentAttributeType.IntegerOptions
                        val option = type.options.find { it.value == value.jsonPrimitive.int }
                        option?.displayName ?: value.jsonPrimitive.content
                    }
                    DocumentAttributeType.Number -> {
                        value.jsonPrimitive.content
                    }
                    DocumentAttributeType.Picture -> {
                        "Image (${value.jsonPrimitive.content.fromBase64Url().size} bytes)"
                    }
                    DocumentAttributeType.String -> {
                        value.jsonPrimitive.content
                    }
                    is DocumentAttributeType.StringOptions -> {
                        val type = attribute!!.type as DocumentAttributeType.StringOptions
                        val option = type.options.find { it.value == value.jsonPrimitive.content }
                        option?.displayName ?: value.jsonPrimitive.content
                    }
                    else -> {
                        value.toString()
                    }
                }

            }
        } catch (e: Throwable) {
            val fallback = value.toString()
            "$fallback (fallback, error occurred during rendering: ${e.message})"
        }
        return str
    }

    companion object
}