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

package com.ul.ims.gmdl.security.mdlauthentication

import android.util.Log
import com.ul.ims.gmdl.cbordata.security.mdlauthentication.SessionTranscript
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.*
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object MacVerificationUtils {

        const val LOG_TAG = "MacVerificationUtils"

        fun calculateDerivedKey(salt: ByteArray, ikm: ByteArray): ByteArray? {
            val okm = ByteArray(32)
            val hkdf = HKDFBytesGenerator(SHA256Digest())
            val params = HKDFParameters(ikm, salt, null)
            hkdf.init(params)
            hkdf.generateBytes(okm, 0, 32)

            return okm
        }

        fun calculateSharedKey(publicKey: PublicKey, privateKey: PrivateKey,
                               sessionTranscript: SessionTranscript?): ByteArray? {
            try {
                val ecka = KeyAgreement.getInstance("ECDH", BouncyCastleProvider())
                ecka.init(privateKey)
                ecka.doPhase(publicKey, true)

                val sharedSecret = ecka.generateSecret()
                sessionTranscript?.let {
                    return sharedSecret + it.encodeAsTaggedByteString()
                }
                Log.e(LOG_TAG, "No SessionTranscript")

            } catch (ex: NoSuchProviderException) {
                Log.e(LOG_TAG, ex.message, ex)
            } catch (ex: NoSuchAlgorithmException) {
                Log.e(LOG_TAG, ex.message, ex)
            } catch (ex: InvalidKeyException) {
                Log.e(LOG_TAG, ex.message, ex)
            } catch (ex: InvalidKeySpecException) {
                Log.e(LOG_TAG, ex.message, ex)
            }
            return null
        }

        fun calculateHMac(algorithm : String, data: ByteArray, key: ByteArray): ByteArray? {
            try {
                val mac = Mac.getInstance(algorithm, BouncyCastleProvider())
                mac.init(SecretKeySpec(key, algorithm))
                mac.reset()
                mac.update(data, 0, data.size)
                return mac.doFinal()
            } catch (ex: NoSuchProviderException) {
                Log.e(LOG_TAG, ex.message, ex)
            } catch (ex: NoSuchAlgorithmException) {
                Log.e(LOG_TAG, ex.message, ex)
            } catch (ex: InvalidKeyException) {
                Log.e(LOG_TAG, ex.message, ex)
            }
            return null
        }
}