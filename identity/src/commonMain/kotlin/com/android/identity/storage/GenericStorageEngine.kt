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

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborMap
import kotlinx.io.buffered
import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/**
 * An storage engine implemented by storing data in a file.
 *
 * @param storageFile the directory to store the data in.
 */
class GenericStorageEngine(private val storageFile: Path) : StorageEngine {

    private var data: MutableMap<String, ByteArray>? = null

    private fun ensureData() {
        if (data != null) {
            return
        }
        val path = Path(storageFile)
        try {
            val encodedData = SystemFileSystem.source(path).buffered().readByteArray()
            data = mutableMapOf()
            if (encodedData.size > 0) {
                val map = Cbor.decode(encodedData).asMap
                for ((key, value) in map) {
                    data!!.put(key.asTstr, value.asBstr)
                }
            }
        } catch (e: FileNotFoundException) {
            // No problem, we all start from zero at some point...
            data = mutableMapOf()
        } catch (e: Throwable) {
            throw IllegalStateException("Error loading data", e)
        }
    }

    private fun saveData() {
        check(data != null)
        val builder = CborMap.builder()
        for ((key, value) in data!!) {
            builder.put(key, value)
        }
        val newPath = Path(storageFile.name + ".tmp")
        val path = Path(storageFile)
        val sink = SystemFileSystem.sink(newPath).buffered()
        sink.write(Cbor.encode(builder.end().build()))
        sink.flush()
        sink.close()
        SystemFileSystem.atomicMove(newPath, path)
    }

    override fun get(key: String): ByteArray? {
        ensureData()
        return data!![key]
    }

    override fun put(key: String, data: ByteArray) {
        ensureData()
        this.data!![key] = data
        saveData()
    }

    override fun delete(key: String) {
        ensureData()
        data!!.remove(key)
        saveData()
    }

    override fun deleteAll() {
        ensureData()
        data!!.clear()
        saveData()
    }

    override fun enumerate(): Collection<String> {
        ensureData()
        return data!!.keys
    }

}