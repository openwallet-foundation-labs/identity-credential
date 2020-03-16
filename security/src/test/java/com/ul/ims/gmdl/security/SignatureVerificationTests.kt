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

package com.ul.ims.gmdl.security

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert
import org.junit.Test
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class SignatureVerificationTests {

    private val dataToBeSigned = byteArrayOf(
        0x01.toByte(), 0x02.toByte(), 0x03.toByte()
    )
    private val signature = byteArrayOf(
        0x30.toByte(), 0x45.toByte(), 0x02.toByte(), 0x20.toByte(), 0x78.toByte(), 0x60.toByte(), 0x8E.toByte(), 0x42.toByte(), 0x2E.toByte(), 0xB1.toByte(), 0xBE.toByte(), 0x22.toByte(), 0x01.toByte(), 0x7A.toByte(), 0x66.toByte(), 0x24.toByte(), 0x3A.toByte(), 0x86.toByte(), 0x8D.toByte(), 0x10.toByte(), 0xAC.toByte(), 0xA7.toByte(), 0x54.toByte(), 0x4C.toByte(), 0x5D.toByte(), 0x20.toByte(), 0xA9.toByte(), 0xA1.toByte(), 0x55.toByte(), 0x8B.toByte(), 0xBD.toByte(), 0x19.toByte(), 0x3F.toByte(), 0x20.toByte(), 0x92.toByte(), 0xDA.toByte(), 0x02.toByte(), 0x21.toByte(), 0x00.toByte(), 0xF9.toByte(), 0x51.toByte(), 0x34.toByte(), 0x43.toByte(), 0x25.toByte(), 0x7D.toByte(), 0x59.toByte(), 0x85.toByte(), 0x48.toByte(), 0x0C.toByte(), 0x35.toByte(), 0x9A.toByte(), 0xD1.toByte(), 0x8B.toByte(), 0xCD.toByte(), 0x5B.toByte(), 0xFE.toByte(), 0xB8.toByte(), 0x48.toByte(), 0x31.toByte(), 0xAB.toByte(), 0x76.toByte(), 0x14.toByte(), 0x8F.toByte(), 0x90.toByte(), 0x4D.toByte(), 0x05.toByte(), 0x6A.toByte(), 0x18.toByte(), 0x60.toByte(), 0x66.toByte(), 0x93.toByte()
    )
    private val privateKeyBytes = byteArrayOf(
        0x30.toByte(), 0x81.toByte(), 0x93.toByte(), 0x02.toByte(), 0x01.toByte(), 0x00.toByte(), 0x30.toByte(), 0x13.toByte(), 0x06.toByte(), 0x07.toByte(), 0x2A.toByte(), 0x86.toByte(), 0x48.toByte(), 0xCE.toByte(), 0x3D.toByte(), 0x02.toByte(), 0x01.toByte(), 0x06.toByte(), 0x08.toByte(), 0x2A.toByte(), 0x86.toByte(), 0x48.toByte(), 0xCE.toByte(), 0x3D.toByte(), 0x03.toByte(), 0x01.toByte(), 0x07.toByte(), 0x04.toByte(), 0x79.toByte(), 0x30.toByte(), 0x77.toByte(), 0x02.toByte(), 0x01.toByte(), 0x01.toByte(), 0x04.toByte(), 0x20.toByte(), 0xB4.toByte(), 0x38.toByte(), 0xAA.toByte(), 0xEF.toByte(), 0x0E.toByte(), 0x4D.toByte(), 0xA4.toByte(), 0x00.toByte(), 0x9A.toByte(), 0x85.toByte(), 0x0F.toByte(), 0x34.toByte(), 0x36.toByte(), 0xEA.toByte(), 0x50.toByte(), 0xFE.toByte(), 0xC0.toByte(), 0x06.toByte(), 0x12.toByte(), 0xBA.toByte(), 0x37.toByte(), 0x94.toByte(), 0x08.toByte(), 0x58.toByte(), 0x34.toByte(), 0x60.toByte(), 0xBC.toByte(), 0x41.toByte(), 0x88.toByte(), 0xA0.toByte(), 0xBE.toByte(), 0x10.toByte(), 0xA0.toByte(), 0x0A.toByte(), 0x06.toByte(), 0x08.toByte(), 0x2A.toByte(), 0x86.toByte(), 0x48.toByte(), 0xCE.toByte(), 0x3D.toByte(), 0x03.toByte(), 0x01.toByte(), 0x07.toByte(), 0xA1.toByte(), 0x44.toByte(), 0x03.toByte(), 0x42.toByte(), 0x00.toByte(), 0x04.toByte(), 0x8A.toByte(), 0x9B.toByte(), 0x22.toByte(), 0x46.toByte(), 0x74.toByte(), 0x65.toByte(), 0x1C.toByte(), 0x8A.toByte(), 0x99.toByte(), 0x0F.toByte(), 0x8B.toByte(), 0x42.toByte(), 0xE8.toByte(), 0x23.toByte(), 0xC1.toByte(), 0x8B.toByte(), 0x2F.toByte(), 0x3F.toByte(), 0xE6.toByte(), 0xDA.toByte(), 0xEC.toByte(), 0x59.toByte(), 0x5A.toByte(), 0x3F.toByte(), 0xFE.toByte(), 0xE7.toByte(), 0x62.toByte(), 0xF2.toByte(), 0xBE.toByte(), 0x16.toByte(), 0x18.toByte(), 0x10.toByte(), 0x24.toByte(), 0x46.toByte(), 0x2C.toByte(), 0x45.toByte(), 0x89.toByte(), 0x27.toByte(), 0x89.toByte(), 0x0A.toByte(), 0xA8.toByte(), 0x30.toByte(), 0x6C.toByte(), 0xF8.toByte(), 0xEF.toByte(), 0x12.toByte(), 0x8C.toByte(), 0xEE.toByte(), 0x40.toByte(), 0x81.toByte(), 0x20.toByte(), 0x54.toByte(), 0xE9.toByte(), 0x2C.toByte(), 0x6E.toByte(), 0xCB.toByte(), 0xD3.toByte(), 0x16.toByte(), 0x1C.toByte(), 0x95.toByte(), 0xD9.toByte(), 0x14.toByte(), 0x42.toByte(), 0x69.toByte()
    )
    private val publicKeyBytes = byteArrayOf(
        0x30.toByte(), 0x59.toByte(), 0x30.toByte(), 0x13.toByte(), 0x06.toByte(), 0x07.toByte(), 0x2A.toByte(), 0x86.toByte(), 0x48.toByte(), 0xCE.toByte(), 0x3D.toByte(), 0x02.toByte(), 0x01.toByte(), 0x06.toByte(), 0x08.toByte(), 0x2A.toByte(), 0x86.toByte(), 0x48.toByte(), 0xCE.toByte(), 0x3D.toByte(), 0x03.toByte(), 0x01.toByte(), 0x07.toByte(), 0x03.toByte(), 0x42.toByte(), 0x00.toByte(), 0x04.toByte(), 0x8A.toByte(), 0x9B.toByte(), 0x22.toByte(), 0x46.toByte(), 0x74.toByte(), 0x65.toByte(), 0x1C.toByte(), 0x8A.toByte(), 0x99.toByte(), 0x0F.toByte(), 0x8B.toByte(), 0x42.toByte(), 0xE8.toByte(), 0x23.toByte(), 0xC1.toByte(), 0x8B.toByte(), 0x2F.toByte(), 0x3F.toByte(), 0xE6.toByte(), 0xDA.toByte(), 0xEC.toByte(), 0x59.toByte(), 0x5A.toByte(), 0x3F.toByte(), 0xFE.toByte(), 0xE7.toByte(), 0x62.toByte(), 0xF2.toByte(), 0xBE.toByte(), 0x16.toByte(), 0x18.toByte(), 0x10.toByte(), 0x24.toByte(), 0x46.toByte(), 0x2C.toByte(), 0x45.toByte(), 0x89.toByte(), 0x27.toByte(), 0x89.toByte(), 0x0A.toByte(), 0xA8.toByte(), 0x30.toByte(), 0x6C.toByte(), 0xF8.toByte(), 0xEF.toByte(), 0x12.toByte(), 0x8C.toByte(), 0xEE.toByte(), 0x40.toByte(), 0x81.toByte(), 0x20.toByte(), 0x54.toByte(), 0xE9.toByte(), 0x2C.toByte(), 0x6E.toByte(), 0xCB.toByte(), 0xD3.toByte(), 0x16.toByte(), 0x1C.toByte(), 0x95.toByte(), 0xD9.toByte(), 0x14.toByte(), 0x42.toByte(), 0x69.toByte()
    )

    @Test
    fun testSignAndVerify() {
        val signedData = signData(dataToBeSigned)
        verifyData(dataToBeSigned, signedData, publicKeyBytes)
    }

    // TODO: Vini
    // Use this function to sign the Cbor Sig Structure
    private fun signData(dataToBeSigned : ByteArray) : ByteArray {
        try {
            val ks = PKCS8EncodedKeySpec(privateKeyBytes)
            val keyFactory = KeyFactory.getInstance("ECDSA", BouncyCastleProvider())
            val privKey = keyFactory.generatePrivate(ks)
            val sig = Signature.getInstance("SHA256withECDSA", BouncyCastleProvider())
            sig.initSign(privKey)
            sig.update(dataToBeSigned)
            return sig.sign()
        } catch (ex : Exception) {
            ex.printStackTrace()
        }
        return byteArrayOf()
    }

    @Test
    fun testVerifySignature() {
        val isVerified = verifyData(dataToBeSigned, signature, publicKeyBytes)
        Assert.assertTrue(isVerified)
    }

    private fun verifyData(dataToBeSigned: ByteArray, signature : ByteArray, publicKeyBytes: ByteArray) : Boolean {
        try {
            val ks = X509EncodedKeySpec(publicKeyBytes)
            val keyFactory = KeyFactory.getInstance("ECDSA", BouncyCastleProvider())
            val pubKey = keyFactory.generatePublic(ks)
            //"1.2.840.10045.4.3.2" is equivalent to "SHA256withECDSA"
            val sig = Signature.getInstance("1.2.840.10045.4.3.2", BouncyCastleProvider())
            sig.initVerify(pubKey)
            sig.update(dataToBeSigned)
            return sig.verify(signature)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return false
    }

}