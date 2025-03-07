package org.multipaz.server.openid4vci

import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.EcSignature
import org.multipaz.util.fromBase64Url

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
