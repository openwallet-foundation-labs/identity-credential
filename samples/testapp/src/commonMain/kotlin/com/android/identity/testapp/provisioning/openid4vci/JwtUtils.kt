package com.android.identity.testapp.provisioning.openid4vci

import org.multipaz.crypto.EcPublicKey
import org.multipaz.sdjwt.util.JsonWebKey
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put

internal fun EcPublicKey.toJson(keyId: String?): JsonObject {
    return JsonWebKey(this).toRawJwk {
        if (keyId != null) {
            put("kid", keyId)
        }
        put("alg", curve.defaultSigningAlgorithmFullySpecified.joseAlgorithmIdentifier)
        put("use", "sig")
    }
}