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

package com.ul.ims.gmdl.security.util

import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class Utils {

    companion object {
        /**
         * Computes an HKDF.
         *
         * This is based on https://github.com/google/tink/blob/master/java/src/main/java/com/google
         * /crypto/tink/subtle/Hkdf.java
         * which is also Copyright (c) Google and also licensed under the Apache 2 license.
         *
         * @param macAlgorithm the MAC algorithm used for computing the Hkdf. I.e., "HMACSHA1" or
         * "HMACSHA256".
         * @param ikm          the input keying material.
         * @param salt         optional salt. A possibly non-secret random value. If no salt is
         * provided (i.e. if
         * salt has length 0) then an array of 0s of the same size as the hash
         * digest is used as salt.
         * @param info         optional context and application specific information.
         * @param size         The length of the generated pseudorandom string in bytes. The maximal
         * size is
         * 255.DigestSize, where DigestSize is the size of the underlying HMAC.
         * @return size pseudorandom bytes.
         */
        fun computeHkdf(
            macAlgorithm: String, ikm: ByteArray, salt: ByteArray?, info: ByteArray, size: Int
        ): ByteArray {
            var mac: Mac? = null
            try {
                mac = Mac.getInstance(macAlgorithm)
            } catch (e: NoSuchAlgorithmException) {
                throw RuntimeException("No such algorithm: $macAlgorithm", e)
            }

            if (size > 255 * mac!!.macLength) {
                throw RuntimeException("size too large")
            }
            try {
                if (salt == null || salt.size == 0) {
                    // According to RFC 5869, Section 2.2 the salt is optional. If no salt is provided
                    // then HKDF uses a salt that is an array of zeros of the same length as the hash
                    // digest.
                    mac.init(SecretKeySpec(ByteArray(mac.macLength), macAlgorithm))
                } else {
                    mac.init(SecretKeySpec(salt, macAlgorithm))
                }
                val prk = mac.doFinal(ikm)
                val result = ByteArray(size)
                var ctr = 1
                var pos = 0
                mac.init(SecretKeySpec(prk, macAlgorithm))
                var digest = ByteArray(0)
                while (true) {
                    mac.update(digest)
                    mac.update(info)
                    mac.update(ctr.toByte())
                    digest = mac.doFinal()
                    if (pos + digest.size < size) {
                        System.arraycopy(digest, 0, result, pos, digest.size)
                        pos += digest.size
                        ctr++
                    } else {
                        System.arraycopy(digest, 0, result, pos, size - pos)
                        break
                    }
                }
                return result
            } catch (e: InvalidKeyException) {
                throw RuntimeException("Error MACing", e)
            }
        }
    }
}