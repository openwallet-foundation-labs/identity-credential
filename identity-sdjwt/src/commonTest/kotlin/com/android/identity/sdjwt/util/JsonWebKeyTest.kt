package com.android.identity.sdjwt.util

import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Before
import org.junit.Test
import java.security.Security
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonWebKeyTest {

    @Before
    fun setup() {
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    @Test
    fun testP256() {
        parseAndEncode(EcCurve.P256)
    }

    @Test
    fun testP384() {
        parseAndEncode(EcCurve.P384)
    }

    @Test
    fun testP521() {
        parseAndEncode(EcCurve.P521)
    }

    @Test
    fun testEC_CURVE_ED25519() {
        parseAndEncode(EcCurve.ED25519)
    }

    @Test
    fun testEC_CURVE_ED448() {
        parseAndEncode(EcCurve.ED448)
    }

    @Test
    fun testEC_CURVE_X448() {
        parseAndEncode(EcCurve.X448)
    }

    @Test
    fun testEC_CURVE_X25519() {
        parseAndEncode(EcCurve.X25519)
    }

    @Test
    fun testEC_CURVE_BRAINPOOLP256R1() {
        parseAndEncode(EcCurve.BRAINPOOLP256R1)
    }

    @Test
    fun testEC_CURVE_BRAINPOOLP320R1() {
        parseAndEncode(EcCurve.BRAINPOOLP320R1)
    }

    @Test
    fun testEC_CURVE_BRAINPOOLP384R1() {
        parseAndEncode(EcCurve.BRAINPOOLP384R1)
    }

    @Test
    fun testEC_CURVE_BRAINPOOLP512R1() {
        parseAndEncode(EcCurve.BRAINPOOLP512R1)
    }

    private fun parseAndEncode(curve: EcCurve) {
        val pubKey = Crypto.createEcPrivateKey(curve).publicKey
        val jwk = JsonWebKey(pubKey).asJwk
        assertTrue("jwk" in jwk.keys)

        when(curve) {
            EcCurve.ED25519,
            EcCurve.ED448,
            EcCurve.X448,
            EcCurve.X25519 -> assertEquals("OKP", jwk["jwk"]!!.jsonObject["kty"]!!.jsonPrimitive.content)
            else -> assertEquals("EC", jwk["jwk"]!!.jsonObject["kty"]!!.jsonPrimitive.content)
        }

        val rehydratedPubKey = JsonWebKey(jwk).asEcPublicKey
        assertEquals(pubKey, rehydratedPubKey)
    }
}