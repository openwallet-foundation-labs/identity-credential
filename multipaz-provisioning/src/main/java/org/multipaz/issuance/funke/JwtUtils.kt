package org.multipaz.issuance.funke

import org.multipaz.crypto.EcPublicKey
import org.multipaz.sdjwt.util.JsonWebKey
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