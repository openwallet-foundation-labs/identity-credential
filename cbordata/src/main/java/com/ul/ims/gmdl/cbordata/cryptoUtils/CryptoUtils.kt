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

package com.ul.ims.gmdl.cbordata.cryptoUtils

import android.security.keystore.KeyProperties
import android.util.Log
import org.bouncycastle.jcajce.provider.asymmetric.util.EC5Util
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.ECPointUtil
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.security.*
import java.security.interfaces.ECPublicKey
import java.security.spec.ECPublicKeySpec
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import kotlin.experimental.and

object CryptoUtils {

    // TAG for Log
    const val TAG = "CryptoUtils"

    // Google Identity Credential API uses only curve prime256v1
    const val CURVE_PRIME256V1 = "prime256v1"

    // https://www.bouncycastle.org/wiki/pages/viewpage.action?pageId=362269
    const val PK_PRIME256V1_SIZE_BITS = 256

    fun decodeEncodedPubKey(publicKeyBytes: ByteArray?): ECPublicKey? {
        val publicKeySpec = X509EncodedKeySpec(publicKeyBytes)

        val keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC)
        return keyFactory.generatePublic(publicKeySpec) as? ECPublicKey
    }

    fun decodeUncompressedPoint(bytes : ByteArray?) : ECPublicKey {
        val params = ECNamedCurveTable.getParameterSpec(CURVE_PRIME256V1)
        val fact = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC)

        val curve = params.curve
        val ellipticCurve = EC5Util.convertCurve(curve, params.seed)
        val ecPoint = ECPointUtil.decodePoint(ellipticCurve, bytes)
        val params2 = EC5Util.convertSpec(ellipticCurve, params)
        val keySpec = ECPublicKeySpec(ecPoint, params2)

        return fact.generatePublic(keySpec) as ECPublicKey
    }

    fun toUncompressedPoint(x : ByteArray, y : ByteArray): ByteArray {
        // key size should be 32 bytes or 256 bits
        val keySizeBytes = (PK_PRIME256V1_SIZE_BITS + Byte.SIZE_BITS - 1) / Byte.SIZE_BITS

        // Uncompressed public key ByteArray
        val uncompressedPk = ByteArray(1 + 2 * keySizeBytes)
        var offset = 0

        // First item is 0x04
        uncompressedPk[offset++] = 0x04

        // Normalize x coordinate size
        if (x.size <= keySizeBytes) {
            System.arraycopy(x, 0, uncompressedPk, offset + keySizeBytes - x.size, x.size)
        } else if (x.size == keySizeBytes + 1 && x[0].toInt() == 0) {
            // if first byte is 0, drop it
            System.arraycopy(x, 1, uncompressedPk, offset, keySizeBytes)
        } else {
            throw IllegalStateException("x value is too large")
        }
        offset += keySizeBytes

        // Normalize y coordinate size
        if (y.size <= keySizeBytes) {
            System.arraycopy(y, 0, uncompressedPk, offset + keySizeBytes - y.size, y.size)
        } else if (y.size == keySizeBytes + 1 && y[0].toInt() == 0) {
            // if first byte is 0, drop it
            System.arraycopy(y, 1, uncompressedPk, offset, keySizeBytes)
        } else {
            throw IllegalStateException("y value is too large")
        }

        return uncompressedPk
    }

    /**
     * Returns a hash for a given ByteArray
     *
     * @param algorithm Hash algorithm to be used
     * @param bytes ByteArray to generate a hash of
     * @return bytes of the calculated hash
     *
     * **/
    fun generateHash(algorithm: HashAlgorithms, bytes: ByteArray) : ByteArray? {
        return try {

            val md = MessageDigest.getInstance(algorithm.algorithm)
            md.update(bytes)

            val pkHash = md.clone() as? MessageDigest
            pkHash?.digest()

        } catch (ex: NoSuchAlgorithmException) {
            Log.e(TAG, ex.message, ex)
            null
        } catch (ex : CloneNotSupportedException) {
            Log.e(TAG, ex.message, ex)
            null
        }
    }

    /**
     * Strip out the Sign Byte 0x00
     * **/
    fun toByteArrayUnsigned(bi: BigInteger): ByteArray {
        var extractedBytes = bi.toByteArray()
        var skipped = 0
        var skip = true
        for (b in extractedBytes) {
            val signByte = b == 0x00.toByte()
            if (skip && signByte) {
                skipped++
                continue
            } else if (skip) {
                skip = false
            }
        }
        extractedBytes = Arrays.copyOfRange(
            extractedBytes, skipped,
            extractedBytes.size
        )
        return extractedBytes
    }

    /**
     * Convert a Private Key pem into a PrivateKey Obj.
     * **/
    fun decodePrivateKey(privateKeyBytes: ByteArray?): PrivateKey? {
        return try {
            val privateKeySpec = PKCS8EncodedKeySpec(privateKeyBytes)

            val keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC)
            keyFactory.generatePrivate(privateKeySpec)
        } catch (ex: NoSuchAlgorithmException) {
            Log.e(TAG, ex.message, ex)
            null
        } catch (ex: InvalidKeySpecException) {
            Log.e(TAG, ex.message, ex)
            null
        }
    }


    /**
     * Sign data using the provided Private Key
     * **/
    fun signData(privateKey : PrivateKey, data : ByteArray) : ByteArray? {
        return try {

            val sign = Signature.getInstance("SHA256withECDSA")
            sign.initSign(privateKey)
            sign.update(data)
            sign.sign()

        } catch(ex : SignatureException) {
            Log.e(TAG, ex.message, ex)
            null
        }
    }

    fun extractRandSFromSignature(signature : ByteArray) : ByteArray? {
        if (signature[0] == 0x30.toByte()) {
            // parse R
            val rIdentifier = signature[2]
            val rSize = signature[3].toInt()
            val rBigInt = BigInteger(signature.copyOfRange(4, rSize + 4))
            val r = toByteArrayUnsigned(rBigInt)

            // parse S
            val sIdentifier = signature[4 + rSize]
            val sSize = signature[5 + rSize].toInt()
            var index = rSize + 6
            val sBigInt = BigInteger(signature.copyOfRange(index, index + sSize))
            val s = toByteArrayUnsigned(sBigInt)

            return r + s
        }

        return null
    }

    fun signatureCoseToDer(signature: ByteArray): ByteArray? {
        if (signature.size != 64) {
            throw RuntimeException("signature.length is " + signature.size + ", expected 64")
        }
        val r =
            BigInteger(Arrays.copyOfRange(signature, 0, 32))
        val s =
            BigInteger(Arrays.copyOfRange(signature, 32, 64))
        val rBytes = encodePositiveBigInteger(r)
        val sBytes = encodePositiveBigInteger(s)
        val baos = ByteArrayOutputStream()
        try {
            baos.write(0x30)
            baos.write(2 + rBytes.size + 2 + sBytes.size)
            baos.write(0x02)
            baos.write(rBytes.size)
            baos.write(rBytes)
            baos.write(0x02)
            baos.write(sBytes.size)
            baos.write(sBytes)
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
        return baos.toByteArray()
    }

    // Adds leading 0x00 if the first encoded byte MSB is set.
    private fun encodePositiveBigInteger(i: BigInteger): ByteArray {
        var bytes = i.toByteArray()
        if (bytes[0].and(0x80.toByte()).toInt() != 0) {
            val baos = ByteArrayOutputStream()
            try {
                baos.write(0x00)
                baos.write(bytes)
            } catch (e: IOException) {
                Log.e(TAG, e.message, e)
                throw java.lang.RuntimeException("Failed writing data", e)
            }
            bytes = baos.toByteArray()
        }
        return bytes
    }
}