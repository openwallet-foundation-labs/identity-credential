package com.android.identity.server.openid4vci

import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.EcSignature
import com.android.identity.util.fromBase64Url

fun checkJwtSignature(publicKey: EcPublicKey, jwt: String) {
    val index = jwt.lastIndexOf('.')
    val signature = EcSignature.fromCoseEncoded(jwt.substring(index+1).fromBase64Url())
    val algorithm = when (publicKey.curve) {
        EcCurve.P256 -> Algorithm.ES256
        EcCurve.P384 -> Algorithm.ES384
        EcCurve.P521 -> Algorithm.ES512
        else -> throw IllegalArgumentException("Unsupported public key")
    }
    if (!Crypto.checkSignature(publicKey, jwt.substring(0, index).toByteArray(),
            algorithm, signature)) {
        throw IllegalArgumentException("Invalid JWT signature")
    }
}
