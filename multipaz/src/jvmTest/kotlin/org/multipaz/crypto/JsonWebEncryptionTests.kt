package org.multipaz.crypto

import org.multipaz.util.toBase64Url
import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.crypto.ECDHDecrypter
import com.nimbusds.jose.crypto.ECDHEncrypter
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jwt.EncryptedJWT
import com.nimbusds.jwt.JWTClaimsSet
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import kotlin.test.assertEquals

class JsonWebEncryptionTests {

    // TODO: Check for other curves once JsonWebEncryption is implemented in a multiplatform fashion.

    @Test fun testEncryptEcdhEs_A128GCM() = testEncryptEcdhEs(Algorithm.A128GCM)
    @Test fun testEncryptEcdhEs_A192GCM() = testEncryptEcdhEs(Algorithm.A192GCM)
    @Test fun testEncryptEcdhEs_A256GCM() = testEncryptEcdhEs(Algorithm.A256GCM)

    private fun testEncryptEcdhEs(encAlg: Algorithm) {
        val recipientKey = Crypto.createEcPrivateKey(EcCurve.P256)

        val claims = buildJsonObject {
            put("vp_token", buildJsonObject {
                put("credential", buildJsonObject {
                    put("foo", JsonPrimitive("blah"))
                })
            })
        }
        val encryptedJwt = JsonWebEncryption.encrypt(
            claimsSet = claims,
            recipientPublicKey = recipientKey.publicKey,
            encAlg = encAlg,
            apu = byteArrayOf(1, 2, 3).toBase64Url(),
            apv = byteArrayOf(4, 5, 6).toBase64Url()
        )

        // Use Nimbus to decrypt
        val encryptedJWT = EncryptedJWT.parse(encryptedJwt.jsonPrimitive.content)
        val encKey = ECKey(
            Curve.P_256,
            recipientKey.publicKey.javaPublicKey as ECPublicKey,
            recipientKey.javaPrivateKey as ECPrivateKey,
            null, null, null, null, null, null, null, null, null
        )
        val decrypter = ECDHDecrypter(encKey)
        encryptedJWT.decrypt(decrypter)
        val decryptedClaims = Json.decodeFromString<JsonObject>(encryptedJWT.jwtClaimsSet.toString())

        assertEquals(claims, decryptedClaims)
    }

    @Test fun testDecryptEcdhEs_A128GCM() = testEncryptEcdhEs(Algorithm.A128GCM)
    @Test fun testDecryptEcdhEs_A192GCM() = testEncryptEcdhEs(Algorithm.A192GCM)
    @Test fun testDecryptEcdhEs_A256GCM() = testEncryptEcdhEs(Algorithm.A256GCM)

    private fun testDecryptEcdhEs(encAlg: Algorithm) {
        val recipientKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val claims = buildJsonObject {
            put("vp_token", buildJsonObject {
                put("credential", buildJsonObject {
                    put("foo", JsonPrimitive("blah"))
                })
            })
        }

        val apu = ByteString(1, 2, 3)
        val apv = ByteString(4, 5, 6)

        // Build the Encrypted JWT with Nimbus
        val responseEncryptionAlg = JWEAlgorithm.parse("ECDH-ES")
        val responseEncryptionMethod = EncryptionMethod.parse(encAlg.jwseAlgorithmIdentifier)
        val jweHeader = JWEHeader.Builder(responseEncryptionAlg, responseEncryptionMethod)
            .agreementPartyUInfo(Base64URL(apu.toByteArray().toBase64Url()))
            .agreementPartyUInfo(Base64URL(apv.toByteArray().toBase64Url()))
            .build()
        val keySet = JWKSet(JWK.parseFromPEMEncodedObjects(recipientKey.publicKey.toPem()))
        val claimSet = JWTClaimsSet.parse(claims.toString())
        val eJwt = EncryptedJWT(jweHeader, claimSet)
        eJwt.encrypt(ECDHEncrypter(keySet.keys[0] as ECKey))
        val encryptedJwt = JsonPrimitive(eJwt.serialize())

        val decryptedClaims = JsonWebEncryption.decrypt(
            encryptedJwt = encryptedJwt,
            recipientKey = recipientKey
        )

        assertEquals(claims, decryptedClaims)
    }
}
