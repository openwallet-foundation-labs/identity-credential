package org.multipaz.cose

import org.multipaz.cbor.CborMap
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.toDataItem
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPublicKeyDoubleCoordinate
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.KeyPurpose
import org.multipaz.securearea.software.SoftwareSecureArea
import org.multipaz.storage.EphemeralStorageEngine
import org.multipaz.util.fromHex
import kotlin.test.BeforeTest

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoseTests {

    @Test
    fun coseKeyDecode() {
        // This checks we can decode the first key from the Example set in
        //
        //   https://datatracker.ietf.org/doc/html/rfc9052#name-public-keys
        //
        val x = "65eda5a12577c2bae829437fe338701a10aaa375e1bb5b5de108de439c08551d".fromHex()
        val y = "1e52ed75701163f7f9e40ddf9f341b3dc9ba860af7e0ca7ca7e9eecd0084d19c".fromHex()
        val id = "meriadoc.brandybuck@buckland.example".encodeToByteArray()
        val item = CborMap.builder()
            .put(-1, 1)
            .put(-2, x)
            .put(-3, y)
            .put(1, 2)
            .put(2, id)
            .end()
            .build()
        val coseKey = item.asCoseKey
        assertEquals(Cose.COSE_KEY_TYPE_EC2, coseKey.keyType.asNumber)
        assertContentEquals(id, coseKey.labels[Cose.COSE_KEY_KID.toCoseLabel]!!.asBstr)
        assertContentEquals(x, coseKey.labels[Cose.COSE_KEY_PARAM_X.toCoseLabel]!!.asBstr)
        assertContentEquals(y, coseKey.labels[Cose.COSE_KEY_PARAM_Y.toCoseLabel]!!.asBstr)

        // Also check we can get an EcPublicKey from this
        val key = coseKey.ecPublicKey as EcPublicKeyDoubleCoordinate
        assertEquals(EcCurve.P256, key.curve)
        assertContentEquals(x, key.x)
        assertContentEquals(y, key.y)
    }

    @Test
    fun coseSign1() {
        val key = Crypto.createEcPrivateKey(EcCurve.P256)
        val dataToSign = "This is the data to sign.".encodeToByteArray()
        val coseSignature = Cose.coseSign1Sign(
            key,
            dataToSign,
            true,
            Algorithm.ES256,
            emptyMap(),
            emptyMap(),
        )

        assertTrue(
            Cose.coseSign1Check(
                key.publicKey,
                null,
                coseSignature,
                Algorithm.ES256
            )
        )

    }

    @Test
    fun coseSign1TestVector() {
        // This is the COSE_Sign1 example from
        //
        //  https://datatracker.ietf.org/doc/html/rfc9052#appendix-C.2.1
        //
        // The key being signed with is the one with kid '11`, see
        //
        //  https://datatracker.ietf.org/doc/html/rfc9052#name-public-keys
        //
        val x = "bac5b11cad8f99f9c72b05cf4b9e26d244dc189f745228255a219a86d6a09eff".fromHex()
        val y = "20138bf82dc1b6d562be0fa54ab7804a3a64b6d72ccfed6b6fb6ed28bbfc117e".fromHex()
        val coseKey = CborMap.builder()
            .put(-1, 1)
            .put(-2, x)
            .put(-3, y)
            .put(1, 2)
            .end().build().asCoseKey

        val coseSign1 = CoseSign1(
            mutableMapOf(
                Pair(1L.toCoseLabel, (-7).toDataItem())
            ),
            mutableMapOf(
                Pair(11L.toCoseLabel, byteArrayOf(1, 1).toDataItem())
            ),
            ("8eb33e4ca31d1c465ab05aac34cc6b23d58fef5c083106c4" +
                    "d25a91aef0b0117e2af9a291aa32e14ab834dc56ed2a223444547e01f11d3b0916e5" +
                    "a4c345cacb36").fromHex(),
            "This is the content.".encodeToByteArray()
        )

        assertTrue(
            Cose.coseSign1Check(
                coseKey.ecPublicKey,
                null,
                coseSign1,
                Algorithm.ES256
            )
        )
    }


    fun coseSign1_helper(curve: EcCurve) {
        // TODO: use assumeTrue() when available in kotlin-test
        if (!Crypto.supportedCurves.contains(curve)) {
            println("Curve $curve not supported on platform")
            return
        }

        val privateKey = Crypto.createEcPrivateKey(curve)
        val signatureAlgorithm = privateKey.curve.defaultSigningAlgorithm
        val protectedHeaders = mapOf<CoseLabel, DataItem>(
            Pair(
                Cose.COSE_LABEL_ALG.toCoseLabel,
                signatureAlgorithm.coseAlgorithmIdentifier.toDataItem()
            )
        )
        val message = "Hello World".encodeToByteArray()
        val coseSignature = Cose.coseSign1Sign(
            privateKey,
            message,
            true,
            signatureAlgorithm,
            protectedHeaders,
            mapOf()
        )

        assertTrue(
            Cose.coseSign1Check(
                privateKey.publicKey,
                null,
                coseSignature,
                signatureAlgorithm
            )
        )
    }

    @Test fun coseSign1_P256() = coseSign1_helper(EcCurve.P256)
    @Test fun coseSign1_P384() = coseSign1_helper(EcCurve.P384)
    @Test fun coseSign1_P521() = coseSign1_helper(EcCurve.P521)
    @Test fun coseSign1_BRAINPOOLP256R1() = coseSign1_helper(EcCurve.BRAINPOOLP256R1)
    @Test fun coseSign1_BRAINPOOLP320R1() = coseSign1_helper(EcCurve.BRAINPOOLP320R1)
    @Test fun coseSign1_BRAINPOOLP384R1() = coseSign1_helper(EcCurve.BRAINPOOLP384R1)
    @Test fun coseSign1_BRAINPOOLP512R1() = coseSign1_helper(EcCurve.BRAINPOOLP512R1)
    @Test fun coseSign1_ED25519() = coseSign1_helper(EcCurve.ED25519)
    @Test fun coseSign1_ED448() = coseSign1_helper(EcCurve.ED448)
}
