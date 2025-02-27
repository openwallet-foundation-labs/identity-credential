package com.android.identity.flow.handler

import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder
import kotlin.random.Random

/** SimpleCipher implementation for AES/GCM with 128/192/256 bit keys. */
class AesGcmCipher(val key: ByteArray) : SimpleCipher {
    private val alg = when (key.size) {
        16 -> Algorithm.A128GCM
        24 -> Algorithm.A192GCM
        32 -> Algorithm.A256GCM
        else -> throw IllegalArgumentException("key length must be 16, 24, or 32 bytes")
    }

    override fun encrypt(plaintext: ByteString): ByteString {
        val ciphertext = ByteStringBuilder()
        val iv = Random.Default.nextBytes(12)
        ciphertext.append(iv)
        ciphertext.append(Crypto.encrypt(alg, key, iv, plaintext.toByteArray()))
        return ciphertext.toByteString()
    }

    override fun decrypt(ciphertext: ByteString): ByteString {
        val iv = ByteArray(12)
        ciphertext.copyInto(iv, endIndex = iv.size)
        try {
            return ByteString(
                Crypto.decrypt(
                    alg, key, iv,
                    ciphertext.substring(iv.size, ciphertext.size).toByteArray()
                )
            )
        } catch (ex: IllegalStateException) {
            throw SimpleCipher.DataTamperedException()
        }
    }
}
