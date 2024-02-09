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

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.builder.MapBuilder
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.UnicodeString
import com.android.identity.credential.NameSpacedData
import com.android.identity.credential.NameSpacedData.Companion.fromEncodedCbor
import com.android.identity.internal.Util
import com.android.identity.internal.Util.cborDecodeBoolean
import com.android.identity.internal.Util.cborDecodeLong
import com.android.identity.internal.Util.cborDecodeString
import com.android.identity.internal.Util.cborEncode
import com.android.identity.internal.Util.cborEncodeBoolean
import com.android.identity.internal.Util.cborEncodeNumber
import com.android.identity.internal.Util.cborEncodeString
import java.io.ByteArrayInputStream

/**
 * An implementation of [ApplicationData] using a [LinkedHashMap] as in-memory backing.
 *
 * Also contains functionality to serialize to/from CBOR.
 *
 * @param onDataChanged callback invoked whenever changes are made to a key that is
 * if it's added, changed, or removed.
 */
class SimpleApplicationData(private val onDataChanged: (key: String) -> Unit) : ApplicationData {

    private val data = mutableMapOf<String, ByteArray?>()

    override fun setData(key: String, value: ByteArray?): ApplicationData {
        if (value == null) {
            data.remove(key)
        } else {
            data[key] = value
        }
        onDataChanged(key)
        return this
    }

    override fun setString(key: String, value: String): ApplicationData =
        setData(key, cborEncodeString(value))

    override fun setNumber(key: String, value: Long): ApplicationData =
        setData(key, cborEncodeNumber(value))

    override fun setBoolean(key: String, value: Boolean): ApplicationData =
        setData(key, cborEncodeBoolean(value))

    override fun setNameSpacedData(key: String, value: NameSpacedData): ApplicationData =
        setData(key, value.encodeAsCbor())

    override fun keyExists(key: String): Boolean = data[key] != null

    override fun getData(key: String): ByteArray =
        data[key] ?: throw IllegalArgumentException("Key '$key' is not present")

    override fun getString(key: String): String = cborDecodeString(getData(key))

    override fun getNumber(key: String): Long = cborDecodeLong(getData(key))

    override fun getBoolean(key: String): Boolean = cborDecodeBoolean(getData(key))

    override fun getNameSpacedData(key: String): NameSpacedData = fromEncodedCbor(getData(key))

    /**
     * Encode the [ApplicationData] as a byte[] using [CBOR](http://cbor.io/).
     *
     * @return a byte[] of the encoded app data.
     */
    fun encodeAsCbor(): ByteArray {
        val appDataBuilder = CborBuilder()
        val appDataMapBuilder: MapBuilder<CborBuilder> = appDataBuilder.addMap()
        for (key in data.keys) {
            appDataMapBuilder.put(key, data[key])
        }
        appDataMapBuilder.end()
        return cborEncode(appDataBuilder.build().get(0))
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
         * @param onDataChanged callback invoked whenever changes are made to a key that is
         * if it's added, changed, or removed.
         * @return A [SimpleApplicationData].
         */
        @JvmStatic
        fun decodeFromCbor(
            encodedApplicationData: ByteArray,
            onDataChanged: (key: String) -> Unit
        ): SimpleApplicationData {
            val bais = ByteArrayInputStream(encodedApplicationData)
            val dataItems: List<DataItem> = try {
                CborDecoder(bais).decode()
            } catch (e: CborException) {
                throw IllegalStateException("Error decoding CBOR", e)
            }
            check(dataItems.size == 1) { "Expected 1 item, found " + dataItems.size }
            check(dataItems[0] is Map) { "Item is not a map" }
            val applicationDataDataItem: DataItem = dataItems[0]
            val appData = SimpleApplicationData(onDataChanged)
            for (keyItem in (applicationDataDataItem as Map).keys) {
                val key: String = (keyItem as UnicodeString).string
                val value = Util.cborMapExtractByteString(applicationDataDataItem, key)
                appData.data[key] = value
            }
            return appData
        }
    }
}