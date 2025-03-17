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
package org.multipaz.storage

import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborMap
import org.multipaz.cbor.buildCborMap

/**
 * An storage engine implementing by storing data in memory.
 *
 * Data is not persisted anywhere.
 */
class EphemeralStorageEngine : StorageEngine {
    private val data = mutableMapOf<String, ByteArray>()

    override fun get(key: String): ByteArray? = data[key]

    override fun put(key: String, data: ByteArray) {
        this.data[key] = data
    }

    override fun delete(key: String) {
        data.remove(key)
    }

    override fun deleteAll() {
        data.clear()
    }

    override fun enumerate(): Collection<String> = data.keys

    fun toCbor(): ByteArray {
        return Cbor.encode(
            buildCborMap {
                data.forEach { (key, value) -> put(key, Bstr(value)) }
            }
        )
    }

    companion object {
        fun fromCbor(encodedData: ByteArray): EphemeralStorageEngine {
            val engine = EphemeralStorageEngine()
            val map = Cbor.decode(encodedData)
            map.asMap.forEach() { (keyDataItem, valueDataItem) ->
                engine.data.put(keyDataItem.asTstr, valueDataItem.asBstr)
            }
            return engine
        }
    }
}