package com.android.identity.issuance.funke

import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.EcPublicKeyDoubleCoordinate
import com.android.identity.crypto.EcPublicKeyOkp
import com.android.identity.securearea.KeyInfo
import com.android.identity.util.toBase64
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun EcCurve.toJsonCurveId(): JsonPrimitive {
    return JsonPrimitive(when(this) {
        EcCurve.P256 -> "P-256"
        EcCurve.P384 -> "P-384"
        EcCurve.P521 -> "P-521"
        else -> throw IllegalArgumentException()
    })
}

internal fun EcCurve.toJsonSignatureId(): JsonPrimitive {
    return JsonPrimitive(when(this) {
        EcCurve.P256 -> "ES256"
        EcCurve.P384 -> "ES384"
        EcCurve.P521 -> "ES521"
        else -> throw IllegalArgumentException()
    })
}

internal fun EcPublicKey.toJson(keyId: String?): JsonObject {
    val values = mutableMapOf(
        "kty" to JsonPrimitive("EC"),
        "alg" to curve.toJsonSignatureId(),
        "use" to JsonPrimitive("sig"),
    )
    if (keyId != null) {
        values["kid"] = JsonPrimitive(keyId)
    }
    when (this) {
        is EcPublicKeyDoubleCoordinate -> {
            values["crv"] = curve.toJsonCurveId()
            values["x"] = JsonPrimitive(x.toBase64())
            values["y"] = JsonPrimitive(y.toBase64())
        }
        is EcPublicKeyOkp -> {
            values["crv"] = curve.toJsonCurveId()
            values["x"] = JsonPrimitive(x.toBase64())
        }
    }
    return JsonObject(values)
}