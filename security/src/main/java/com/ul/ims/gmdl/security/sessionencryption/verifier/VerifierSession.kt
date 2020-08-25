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

package com.ul.ims.gmdl.security.sessionencryption.verifier

import android.security.keystore.KeyProperties
import android.util.Log
import androidx.security.identity.IdentityCredentialException
import com.ul.ims.gmdl.cbordata.cryptoUtils.CryptoUtils
import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement
import com.ul.ims.gmdl.cbordata.security.CoseKey
import com.ul.ims.gmdl.cbordata.security.mdlauthentication.SessionTranscript
import com.ul.ims.gmdl.cbordata.utils.CborUtils
import com.ul.ims.gmdl.security.util.Utils
import java.nio.ByteBuffer
import java.security.*
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class VerifierSession
constructor(holderEphemeralPublicKey: PublicKey,
            deviceEngagement: DeviceEngagement) {

    companion object {
        const val LOG_TAG = "VerifierSession"
        const val ERR_ENCRYPT = "Error encrypting message"
        const val ERR_DECRYPT = "Error decrypting message"
    }

    private val mHolderEphemeralPublicKey = holderEphemeralPublicKey
    private var mEphemeralKeyPair: KeyPair
    private var mSecretKey: SecretKey
    private var mReaderSecretKey: SecretKey
    private var mCounter: Int = 1
    private var mMdlExpectedCounter: Int = 1
    private var mSecureRandom: SecureRandom? = null

    init {

        try {
            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC)
            val ecSpec = ECGenParameterSpec("prime256v1")
            kpg.initialize(ecSpec)
            mEphemeralKeyPair = kpg.generateKeyPair()
        } catch (e: NoSuchAlgorithmException) {
            Log.e(LOG_TAG, e.message, e)
            throw IdentityCredentialException("Error generating ephemeral key", e)
        } catch (e: InvalidAlgorithmParameterException) {
            Log.e(LOG_TAG, e.message, e)
            throw IdentityCredentialException("Error generating ephemeral key", e)
        }

        try {
            val ka = KeyAgreement.getInstance("ECDH")
            ka.init(mEphemeralKeyPair.private)
            ka.doPhase(mHolderEphemeralPublicKey, true)
            val sharedSecret = ka.generateSecret()

            val sessionTranscriptBytes = SessionTranscript.Builder()
                .setReaderKey(getEphemeralPublicKeyAsCoseKey().encode())
                .setDeviceEngagement(deviceEngagement.encode())
                .build()
                .encodeAsTaggedByteString()

            val sharedSecretWithDeviceTranscriptBytes = sharedSecret + sessionTranscriptBytes

            val salt = ByteArray(1)
            val info = ByteArray(0)

            salt[0] = 0x01
            var derivedKey = Utils.computeHkdf("HmacSha256", sharedSecretWithDeviceTranscriptBytes, salt, info, 32)
            mSecretKey = SecretKeySpec(derivedKey, "AES")

            salt[0] = 0x00
            derivedKey = Utils.computeHkdf("HmacSha256", sharedSecretWithDeviceTranscriptBytes, salt, info, 32)
            mReaderSecretKey = SecretKeySpec(derivedKey, "AES")

            mSecureRandom = SecureRandom()

            Log.d(LOG_TAG, "mSecretKey.encoded: ${CborUtils.encodeToString(mSecretKey.encoded)}")
            Log.d(
                LOG_TAG,
                "mReaderSecretKey.encoded: ${CborUtils.encodeToString(mReaderSecretKey.encoded)}"
            )
            Log.d(
                LOG_TAG,
                "mEphemeralKeyPair.private.encoded: ${CborUtils.encodeToString(mEphemeralKeyPair.private.encoded)}"
            )
            Log.d(
                LOG_TAG,
                "mEphemeralKeyPair.public.encoded: ${CborUtils.encodeToString(mEphemeralKeyPair.public.encoded)}"
            )
            Log.d(
                LOG_TAG,
                "KeyAgreement ECDH sharedSecret: ${CborUtils.encodeToString(sharedSecret)}"
            )

        } catch (e: InvalidKeyException) {
            e.printStackTrace()
            throw IdentityCredentialException("Error performing key agreement", e)
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            throw IdentityCredentialException("Error performing key agreement", e)
        }

    }

    fun getEphemeralPublicKeyAsCoseKey() : CoseKey {
        // TODO: Add support for other curves (CoseKey.P256.value.toInt()).
        val curveId = 1
        val pk = mEphemeralKeyPair.public as ECPublicKey
        val xco = CryptoUtils.toByteArrayUnsigned(pk.w.affineX)
        val yco = CryptoUtils.toByteArrayUnsigned(pk.w.affineY)
        val builder = CoseKey.Builder()
        builder.setKeyType(2)
        builder.setCurve(curveId, xco, yco, null)
        return builder.build()
    }

    fun getReaderPublicKey() : ECPublicKey? {
        return mEphemeralKeyPair.public as? ECPublicKey
    }

    fun getPrivateKey() : PrivateKey? {
        return mEphemeralKeyPair.private
    }

    fun encryptMessageToHolder(messagePlaintext: ByteArray): ByteArray? {
        var messageCiphertext: ByteArray? = null
        try {
            val iv = ByteBuffer.allocate(12)
            iv.putInt(0, 0x00000000)
            iv.putInt(4, 0x00000000)
            iv.putInt(8, mCounter)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val encryptionParameterSpec = GCMParameterSpec(128, iv.array())
            cipher.init(Cipher.ENCRYPT_MODE, mReaderSecretKey, encryptionParameterSpec)
            messageCiphertext = cipher.doFinal(messagePlaintext) // This includes the auth tag
        } catch (e: BadPaddingException) {
            Log.e(LOG_TAG, e.message, e)
            throw IdentityCredentialException(ERR_ENCRYPT, e)
        } catch (e: IllegalBlockSizeException) {
            Log.e(LOG_TAG, e.message, e)
            throw IdentityCredentialException(ERR_ENCRYPT, e)
        } catch (e: NoSuchPaddingException) {
            Log.e(LOG_TAG, e.message, e)
            throw IdentityCredentialException(ERR_ENCRYPT, e)
        } catch (e: InvalidKeyException) {
            Log.e(LOG_TAG, e.message, e)
            throw IdentityCredentialException(ERR_ENCRYPT, e)
        } catch (e: NoSuchAlgorithmException) {
            Log.e(LOG_TAG, e.message, e)
            throw IdentityCredentialException(ERR_ENCRYPT, e)
        } catch (e: InvalidAlgorithmParameterException) {
            Log.e(LOG_TAG, e.message, e)
            throw IdentityCredentialException(ERR_ENCRYPT, e)
        }

        mCounter += 1
        return messageCiphertext
    }

    fun decryptMessageFromHolder(messageCiphertext: ByteArray): ByteArray? {
        val iv = ByteBuffer.allocate(12)
        iv.putInt(0, 0x00000000)
        iv.putInt(4, 0x00000001)
        iv.putInt(8, mMdlExpectedCounter)
        var plaintext: ByteArray? = null
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, mSecretKey, GCMParameterSpec(128, iv.array()))
            plaintext = cipher.doFinal(messageCiphertext)
        } catch (e: BadPaddingException) {
            Log.e(LOG_TAG, e.message, e)
            throw IdentityCredentialException(ERR_DECRYPT, e)
        } catch (e: IllegalBlockSizeException) {
            Log.e(LOG_TAG, e.message, e)
            throw IdentityCredentialException(ERR_DECRYPT, e)
        } catch (e: InvalidAlgorithmParameterException) {
            Log.e(LOG_TAG, e.message, e)
            throw IdentityCredentialException(ERR_DECRYPT, e)
        } catch (e: InvalidKeyException) {
            Log.e(LOG_TAG, e.message, e)
            throw IdentityCredentialException(ERR_DECRYPT, e)
        } catch (e: NoSuchAlgorithmException) {
            Log.e(LOG_TAG, e.message, e)
            throw IdentityCredentialException(ERR_DECRYPT, e)
        } catch (e: NoSuchPaddingException) {
            Log.e(LOG_TAG, e.message, e)
            throw IdentityCredentialException(ERR_DECRYPT, e)
        }

        mMdlExpectedCounter += 1
        return plaintext
    }
}