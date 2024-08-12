package com.android.identity.issuance.funke

import com.android.identity.crypto.EcPublicKey
import com.android.identity.sdjwt.util.JsonWebKey
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun EcPublicKey.toJson(keyId: String?): JsonObject {
    return JsonWebKey(this).toRawJwk {
        if (keyId != null) {
            put("kid", JsonPrimitive(keyId))
        }
        put("alg", JsonPrimitive(curve.defaultSigningAlgorithm.jwseAlgorithmIdentifier))
        put("use", JsonPrimitive("sig"))
    }
}