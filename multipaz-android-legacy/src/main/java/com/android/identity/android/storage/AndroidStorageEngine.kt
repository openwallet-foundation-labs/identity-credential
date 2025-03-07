/*
 * Copyright 2023 The Android Open Source Project
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
package com.android.identity.android.storage

import android.content.Context
import android.os.storage.StorageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.storage.GenericStorageEngine
import org.multipaz.util.toHex
import kotlinx.io.bytestring.ByteStringBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.io.files.Path

/**
 * A storage engine on Android.
 *
 * This is like `GenericStorageEngine` but the backing file is optionally encrypted at rest,
 * using a hardware-backed symmetric encryption key and AES-128 GCM.
 *
 * Note that data is stored in a way so it's still available to the application even if when
 * encryption is toggled on and off.
 */
class AndroidStorageEngine internal constructor(
    private val context: Context,
    private val storageFile: Path,
    private val useEncryption: Boolean
) : GenericStorageEngine(Path(storageFile)) {

    private val secretKey by lazy { ensureSecretKey() }

    override fun transform(data: ByteArray, isLoading: Boolean): ByteArray {
        if (isLoading) {
            check(data.size >= MAGIC_SIZE) { "File too short for magic" }
            val magic = data.sliceArray(IntRange(0, MAGIC_SIZE - 1))
            val dataAfterMagic = data.sliceArray(IntRange(MAGIC_SIZE, data.size - 1))
            if (magic contentEquals MAGIC_ENCRYPTED) {
                return decrypt(secretKey, dataAfterMagic)
            } else if (magic contentEquals MAGIC_NOT_ENCRYPTED) {
                return dataAfterMagic
            } else {
                throw IllegalStateException("Unexpected magic ${magic.toHex()}")
            }
        } else {
            if (useEncryption) {
                return MAGIC_ENCRYPTED + encrypt(secretKey, data)
            } else {
                return MAGIC_NOT_ENCRYPTED + data
            }
        }
    }

    private fun ensureSecretKey(): SecretKey {
        val keyAlias = PREFIX + "_KeyFor_" + storageFile.name
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            val entry = ks.getEntry(keyAlias, null)
            if (entry != null) {
                return (entry as KeyStore.SecretKeyEntry).secretKey
            }
            val kg = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
            )
            val builder = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(128)
            kg.init(builder.build())
            kg.generateKey()
        } catch (e: Exception) {
            throw IllegalStateException("Error loading secret key", e)
        }
    }

    /**
     * A builder for [AndroidStorageEngine].
     *
     * @param context application context.
     * @param storagePath the file to store data in.
     */
    class Builder(
        private val context: Context,
        private val storageFile: Path
    ) {
        private var useEncryption =
            !((context.getSystemService(StorageManager::class.java) as StorageManager)
                .isEncrypted(File(storageFile.toString())))

        /**
         * Sets whether to encrypt the backing file on disk.
         *
         * By default this is set to `true` only if the given file to store data files in
         * isn't encrypted (determined using [StorageManager.isEncrypted]). All Android
         * devices launching with Android 10 or later has an encrypted data partition meaning
         * that the encryption of values won't be on by default for such devices.
         *
         * @param useEncryption whether to encrypt values.
         * @return the builder.
         */
        fun setUseEncryption(useEncryption: Boolean) = apply {
            this.useEncryption = useEncryption
        }

        /**
         * Builds the [AndroidStorageEngine].
         *
         * @return a [AndroidStorageEngine].
         */
        fun build(): AndroidStorageEngine {
            return AndroidStorageEngine(context, storageFile, useEncryption)
        }
    }

    companion object {
        // We prefix the data stored with a magic marker so we know whether it needs to be
        // decrypted or not.
        //
        private const val MAGIC_SIZE = 4
        private val MAGIC_ENCRYPTED = "Ienc".encodeToByteArray()
        private val MAGIC_NOT_ENCRYPTED = "Iraw".encodeToByteArray()

        private const val PREFIX = "IC_AndroidStorageEngine_"

        // Because some older Android versions have a buggy Android Keystore where encryption
        // only works with small amounts of data (b/234563696) chop the cleartext into smaller
        // chunks and encrypt them separately. We store the data using the following CDDL
        //
        //  StoredData = [+ bstr]
        //
        const val CHUNKED_ENCRYPTED_MAX_CHUNK_SIZE = 16384
        private fun decrypt(
            secretKey: SecretKey,
            encryptedData: ByteArray
        ): ByteArray {
            val array = Cbor.decode(encryptedData).asArray
            return try {
                val builder = ByteStringBuilder()
                for (item in array) {
                    val encryptedChunk = item.asBstr
                    val iv = encryptedChunk.sliceArray(IntRange(0, 11))
                    val cipherText = encryptedChunk.sliceArray(IntRange(12, encryptedChunk.size - 1))
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                    val decryptedChunk = cipher.doFinal(cipherText)
                    builder.append(decryptedChunk)
                }
                builder.toByteString().toByteArray()
            } catch (e: Exception) {
                throw IllegalStateException("Error decrypting chunk", e)
            }
        }

        private fun encrypt(
            secretKey: SecretKey,
            data: ByteArray
        ): ByteArray {
            val builder = CborArray.builder()
            return try {
                var offset = 0
                var lastChunk = false
                do {
                    var chunkSize = data.size - offset
                    if (chunkSize <= CHUNKED_ENCRYPTED_MAX_CHUNK_SIZE) {
                        lastChunk = true
                    } else {
                        chunkSize = CHUNKED_ENCRYPTED_MAX_CHUNK_SIZE
                    }
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                    // Produced cipherText includes auth tag
                    val cipherTextForChunk = cipher.doFinal(
                        data,
                        offset,
                        chunkSize
                    )
                    val baos = ByteArrayOutputStream()
                    baos.write(cipher.iv)
                    baos.write(cipherTextForChunk)
                    val cipherTextForChunkWithIV = baos.toByteArray()
                    builder.add(cipherTextForChunkWithIV)
                    offset += chunkSize
                } while (!lastChunk)
                Cbor.encode(builder.end().build())
            } catch (e: Exception) {
                throw IllegalStateException("Error encrypting data", e)
            }
        }
    }
}
