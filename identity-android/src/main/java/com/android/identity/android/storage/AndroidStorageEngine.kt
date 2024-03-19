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
import android.util.AtomicFile
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.storage.StorageEngine
import com.android.identity.util.toHex
import kotlinx.io.bytestring.ByteStringBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.KeyStore
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * A storage engine on Android.
 *
 * Values in the key/value store is optionally stored encrypted on disk, using a hardware-backed
 * symmetric encryption key and AES-128 GCM.
 *
 * Each file name in the given directory will be prefixed with `IC_AndroidStorageEngine_`.
 */
class AndroidStorageEngine internal constructor(
    private val context: Context,
    private val storageDirectory: File,
    private val useEncryption: Boolean
) : StorageEngine {
    private fun getTargetFile(key: String): File =
        try {
            val fileName = PREFIX + URLEncoder.encode(key, "UTF-8")
            File(storageDirectory, fileName)
        } catch (e: UnsupportedEncodingException) {
            throw IllegalStateException(e)
        }

    private fun ensureSecretKey(): SecretKey =
        try {
            val keyAlias = PREFIX + "_KeyFor_" + storageDirectory
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            val entry = ks.getEntry(keyAlias, null)
            if (entry != null) {
                (entry as KeyStore.SecretKeyEntry).secretKey
            } else {
                val builder =
                    KeyGenParameterSpec.Builder(
                        keyAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(128)

                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                    .run {
                        init(builder.build())
                        generateKey()
                    }
            }
        } catch (e: Exception) {
            throw IllegalStateException("Error loading secret key", e)
        }

    override fun get(key: String): ByteArray? {
        val file = AtomicFile(getTargetFile(key))
        return try {
            val data = file.readFully()
            check(data.size >= MAGIC_SIZE) { "File too short for magic" }
            val magic = Arrays.copyOfRange(data, 0, MAGIC_SIZE)
            if (Arrays.equals(magic, MAGIC_ENCRYPTED)) {
                decrypt(ensureSecretKey(), Arrays.copyOfRange(data, MAGIC_SIZE, data.size))
            } else if (Arrays.equals(magic, MAGIC_NOT_ENCRYPTED)) {
                Arrays.copyOfRange(data, MAGIC_SIZE, data.size)
            } else {
                throw IllegalStateException("Unexpected magic ${magic.toHex}")
            }
        } catch (e: FileNotFoundException) {
            null
        } catch (e: IOException) {
            throw IllegalStateException("Unexpected exception", e)
        }
    }

    override fun put(key: String, data: ByteArray) {
        // AtomicFile isn't thread-safe (!) so need to serialize access when writing data.
        synchronized(this) {
            val file = AtomicFile(getTargetFile(key))
            var outputStream: FileOutputStream? = null
            try {
                outputStream = file.startWrite()
                if (useEncryption) {
                    outputStream.write(MAGIC_ENCRYPTED)
                    outputStream.write(encrypt(ensureSecretKey(), data))
                } else {
                    outputStream.write(MAGIC_NOT_ENCRYPTED)
                    outputStream.write(data)
                }
                file.finishWrite(outputStream)
            } catch (e: IOException) {
                if (outputStream != null) {
                    file.failWrite(outputStream)
                }
                throw IllegalStateException("Error writing data", e)
            }
        }
    }

    override fun delete(key: String) = AtomicFile(getTargetFile(key)).run { delete() }

    override fun deleteAll() {
        storageDirectory.listFiles()?.let { fileList ->
            fileList.filter { it.name.startsWith(PREFIX) }.forEach { it.delete() }
        }
    }

    override fun enumerate(): Collection<String> {
        val ret = mutableListOf<String>()
        storageDirectory.listFiles()?.let { fileList ->
            fileList
                .filter { it.name.startsWith(PREFIX) }
                .forEach { file ->
                    try {
                        URLDecoder.decode(file.name.substring(PREFIX.length), "UTF-8")
                            .let { name ->
                                name?.let { decodedName ->
                                    ret.add(decodedName)
                                }
                            }
                    } catch (e: UnsupportedEncodingException) {
                        throw IllegalStateException(e)
                    }
                }
        }
        return ret
    }

    /**
     * A builder for [AndroidStorageEngine].
     *
     * @param context application context.
     * @param storageDirectory the directory to store data files in.
     */
    class Builder(
        private val context: Context,
        private val storageDirectory: File
    ) {
        private var useEncryption =
            !((context.getSystemService(StorageManager::class.java) as StorageManager)
                .isEncrypted(storageDirectory))

        /**
         * Sets whether to encrypt the values stored on disk.
         *
         * Note that keys are not encrypted, only values.
         *
         * By default this is set to `true` only if the given directory to store data files in
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
        fun build(): AndroidStorageEngine = AndroidStorageEngine(context, storageDirectory, useEncryption)
    }

    companion object {
        private const val PREFIX = "IC_AndroidStorageEngine_"

        // We prefix the data stored with a magic marker so we know whether it needs to be
        // decrypted or not.
        //
        private const val MAGIC_SIZE = 4
        private val MAGIC_ENCRYPTED = "Ienc".toByteArray()
        private val MAGIC_NOT_ENCRYPTED = "Iraw".toByteArray()

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
                    val cipherText =
                        encryptedChunk.sliceArray(IntRange(12, encryptedChunk.size - 1))
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