package com.android.identity.sdjwt.presentation

import com.android.identity.sdjwt.util.JwtJsonObject
import com.android.identity.sdjwt.util.getLong
import com.android.identity.sdjwt.util.getString
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
            val nonce = jsonObj.getString("nonce")
            val audience = jsonObj.getString("aud")
            val creationTime = jsonObj.getLong("iat")
            val sdHash = jsonObj.getString("_sd_hash")
            return KeyBindingBody(nonce, audience, Instant.fromEpochSeconds(creationTime), sdHash)
        }
    }
}