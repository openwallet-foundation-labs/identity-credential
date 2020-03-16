/*
 * Copyright (C) 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package com.ul.ims.gmdl.security.cryptoUtils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.identity.IdentityCredentialException
import com.ul.ims.gmdl.cbordata.cryptoUtils.CryptoUtils
import org.junit.Assert
import org.junit.Test
import java.security.InvalidAlgorithmParameterException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.interfaces.ECPublicKey

class CryptoUtilsTest {

    @Test
    fun testDecodeEncodedPublicKey() {
        val publicKey = generatePubKey()

        // https://docs.oracle.com/javase/tutorial/security/apisign/step4.html
        val pkEncoded = publicKey?.encoded
        val pk = CryptoUtils.decodeEncodedPubKey(pkEncoded)

        Assert.assertNotNull(pk)
        Assert.assertEquals(publicKey?.w?.affineX, pk?.w?.affineX)
        Assert.assertEquals(publicKey?.w?.affineY, pk?.w?.affineY)
        Assert.assertEquals(publicKey?.algorithm, pk?.algorithm)
        Assert.assertEquals(publicKey?.format, pk?.format)
    }

    @Test
    fun testDecodePubKey() {
        var publicKeyBytes : ByteArray? = null
        val publicKey = generatePubKey()

        publicKey?.let {
            publicKeyBytes = CryptoUtils.toUncompressedPoint(
                publicKey.w.affineX.toByteArray(),
                publicKey.w.affineY.toByteArray()
            )
        }

        publicKeyBytes?.let {
            val pk = CryptoUtils.decodeUncompressedPoint(it)

            Assert.assertNotNull(pk)
            Assert.assertEquals(publicKey?.w?.affineX, pk.w.affineX)
            Assert.assertEquals(publicKey?.w?.affineY, pk.w.affineY)
            Assert.assertEquals(publicKey?.algorithm, pk.algorithm)
            Assert.assertEquals(publicKey?.format, pk.format)
        }
    }

    private fun generatePubKey() : ECPublicKey? {
        var mEphemeralKeyPair : KeyPair? = null

        //Create a Public Key
        try {
            val kpg: KeyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                "AndroidKeyStore"
            )
            val parameterSpec: KeyGenParameterSpec = KeyGenParameterSpec.Builder(
                "key1",
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).run {
                setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                build()
            }

            kpg.initialize(parameterSpec)

            mEphemeralKeyPair = kpg.generateKeyPair()

        } catch (e: NoSuchAlgorithmException) {
            throw IdentityCredentialException("Error generating ephemeral key", e)
        } catch (e: InvalidAlgorithmParameterException) {
            throw IdentityCredentialException("Error generating ephemeral key", e)
        }

        return mEphemeralKeyPair?.public as? ECPublicKey
    }

    @Test
    fun toByteArrayUnsignedTest() {
        val pubKey = generatePubKey()

        pubKey?.w?.let {ecPoint ->
            val unsignedX = CryptoUtils.toByteArrayUnsigned(ecPoint.affineX)
            val unsignedY = CryptoUtils.toByteArrayUnsigned(ecPoint.affineY)

            Assert.assertNotNull(unsignedX)
            Assert.assertNotNull(unsignedY)

            Assert.assertTrue(unsignedX[0].toInt() != 0)
            Assert.assertTrue(unsignedY[0].toInt() != 0)

        }
    }
}