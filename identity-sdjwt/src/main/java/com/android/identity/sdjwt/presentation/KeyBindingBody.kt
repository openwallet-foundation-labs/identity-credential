package com.android.identity.sdjwt.presentation

import com.android.identity.sdjwt.util.JwtJsonObject
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive

class KeyBindingBody(val nonce: String,
                     val audience: String,
                     val creationTime: Instant,
                     val sdHash: String): JwtJsonObject()  {

    override fun buildJson(): JsonObjectBuilder.() -> Unit = {
        put("nonce", JsonPrimitive(nonce))
        put("aud", JsonPrimitive(audience))
        put("iat", JsonPrimitive(creationTime.epochSeconds))
        put("_sd_hash", JsonPrimitive(sdHash))
    }

    companion object {
        fun fromString(input: String): KeyBindingBody {
            val jsonObj = parse(input)
            val nonce = getString(jsonObj, "nonce")
            val audience = getString(jsonObj, "aud")
            val creationTime = getLong(jsonObj, "iat")
            val sdHash = getString(jsonObj, "_sd_hash")
            return KeyBindingBody(nonce, audience, Instant.fromEpochSeconds(creationTime), sdHash)
        }
    }
}