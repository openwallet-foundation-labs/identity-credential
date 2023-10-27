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
package com.android.identity.util

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.toDataItem
import com.android.identity.document.NameSpacedData
import com.android.identity.document.NameSpacedData.Companion.fromEncodedCbor

/**
 * An implementation of [ApplicationData] using a [LinkedHashMap] as in-memory backing.
 *
 * Also contains functionality to serialize to/from CBOR.
 *
 * @param onDataChanged optional callback invoked whenever changes are made to a key that is
 * if it's added, changed, or removed.
 */
class SimpleApplicationData(private val onDataChanged: ((key: String) -> Unit)?) : ApplicationData {

    private val data = mutableMapOf<String, ByteArray>()

    override fun setData(key: String, value: ByteArray?): ApplicationData = apply {
        if (value == null) {
            data.remove(key)
        } else {
            data[key] = value
        }
        onDataChanged?.let { it(key) }
    }

    override fun setString(key: String, value: String): ApplicationData =
        setData(key, Cbor.encode(value.toDataItem()))

    override fun setNumber(key: String, value: Long): ApplicationData =
        setData(key, Cbor.encode(value.toDataItem()))

    override fun setBoolean(key: String, value: Boolean): ApplicationData =
        setData(key, Cbor.encode(value.toDataItem()))

    override fun setNameSpacedData(key: String, value: NameSpacedData): ApplicationData =
        setData(key, value.encodeAsCbor())

    override fun keyExists(key: String): Boolean = data[key] != null

    override fun getData(key: String): ByteArray =
        data[key] ?: throw IllegalArgumentException("Key '$key' is not present")

    override fun getString(key: String): String = Cbor.decode(getData(key)).asTstr

    override fun getNumber(key: String): Long = Cbor.decode(getData(key)).asNumber

    override fun getBoolean(key: String): Boolean = Cbor.decode(getData(key)).asBoolean

    override fun getNameSpacedData(key: String): NameSpacedData = fromEncodedCbor(getData(key))

    /**
     * Encode the [ApplicationData] as a byte[] using [CBOR](http://cbor.io/).
     *
     * @return a byte[] of the encoded app data.
     */
    fun encodeAsCbor(): ByteArray =
        CborMap.builder().run {
            data.forEach { (key, value) -> put(key, value) }
            Cbor.encode(end().build())
        }


    companion object {
        /**
         * Returns a fully populated SimpleApplicationData from a CBOR-encoded
         * [ByteArray] and sets the listener to be used for notification when
         * changes are made to the [SimpleApplicationData].
         *
         * To encode a SimpleApplicationData, use [.encodeAsCbor].
         *
         * @param encodedApplicationData The byte array resulting from [.encodeAsCbor].
         * @param onDataChanged optoinal callback invoked whenever changes are made to a key that is
         * if it's added, changed, or removed.
         * @return A [SimpleApplicationData].
         */
        fun decodeFromCbor(
            encodedApplicationData: ByteArray,
            onDataChanged: ((key: String) -> Unit)?
        ): SimpleApplicationData {
            val appData = SimpleApplicationData(onDataChanged)
            val map = Cbor.decode(encodedApplicationData) as CborMap
            map.items.forEach { (key, value) -> appData.data.put(key.asTstr, value.asBstr) }
            return appData
        }
    }
}