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
package com.android.identity.storage

import java.io.File
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.stream.Collectors

/**
 * An storage engine implemented by storing data in a directory.
 *
 * Each file name in the given directory is the key name prefixed with
 * `IC_GenericStorageEngine_`.
 *
 * @param storageDirectory the directory to store data files in.
 */
class GenericStorageEngine(private val storageDirectory: File) : StorageEngine {
    companion object {
        private const val PREFIX = "IC_GenericStorageEngine_"
    }

    private fun getTargetFile(name: String): File =
        try {
            val fileName = PREFIX + URLEncoder.encode(name, "UTF-8")
            File(storageDirectory, fileName)
        } catch (e: UnsupportedEncodingException) {
            throw IllegalStateException("Unexpected UnsupportedEncodingException", e)
        }

    override fun get(key: String): ByteArray? =
        try {
            val file = getTargetFile(key)
            if (!Files.exists(file.toPath())) {
                null
            } else Files.readAllBytes(file.toPath())
        } catch (e: IOException) {
            throw IllegalStateException("Unexpected exception", e)
        }

    override fun put(key: String, data: ByteArray) {
        try {
            val file = getTargetFile(key)
            // TODO: do this atomically
            Files.deleteIfExists(file.toPath())
            Files.write(file.toPath(), data, StandardOpenOption.CREATE_NEW)
        } catch (e: IOException) {
            throw IllegalStateException("Error writing data", e)
        }
    }

    override fun delete(key: String) {
        val file = getTargetFile(key)
        try {
            Files.deleteIfExists(file.toPath())
        } catch (e: IOException) {
            throw IllegalStateException("Error deleting file", e)
        }
    }

    override fun deleteAll() {
        try {
            for (file in Files.list(storageDirectory.toPath())
                .map { obj: Path -> obj.toFile() }
                .filter { obj: File -> obj.isFile }
                .collect(Collectors.toList())) {
                val name = file.name
                if (!name.startsWith(PREFIX)) {
                    continue
                }
                Files.delete(file.toPath())
            }
        } catch (e: IOException) {
            throw IllegalStateException("Error deleting files", e)
        }
    }

    override fun enumerate(): Collection<String> {
        val ret = ArrayList<String>()
        try {
            for (file in Files.list(storageDirectory.toPath())
                .map { obj: Path -> obj.toFile() }
                .filter { obj: File -> obj.isFile }
                .collect(Collectors.toList())) {
                val name = file.name
                if (!name.startsWith(PREFIX)) {
                    continue
                }
                try {
                    val decodedName = URLDecoder.decode(name.substring(PREFIX.length), "UTF-8")
                    ret.add(decodedName)
                } catch (e: UnsupportedEncodingException) {
                    throw RuntimeException(e)
                }
            }
        } catch (e: IOException) {
            throw IllegalStateException("Error deleting files", e)
        }
        return ret
    }
}