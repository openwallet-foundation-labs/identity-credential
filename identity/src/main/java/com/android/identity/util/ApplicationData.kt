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

import com.android.identity.document.NameSpacedData

/**
 * An interface to an object storing application data in a key-value pair manner.
 *
 * This interface exists to support applications which wish to store additional data it wants to
 * associate with any object, such as a [com.android.identity.document.Document] or
 * [com.android.identity.document.Credential].
 *
 * For example, on a [com.android.identity.document.Document] object one could use this to store the
 * document type (e.g. DocType for 18013-5 documents), user-visible name, logos/background, state,
 * and so on.
 */
interface ApplicationData {
    /**
     * Sets application specific data.
     *
     * Use [.getData] to read the data back.
     *
     * @param key   the key for the data.
     * @param value the value or `null` to remove.
     * @return      the modified [ApplicationData].
     */
    fun setData(key: String, value: ByteArray?): ApplicationData

    /**
     * Sets application specific data as a string.
     *
     * Like [.setData] but encodes the given value using [CBOR](http://cbor.io/) before storing it.
     *
     * @param key   the key for the data.
     * @param value the value
     * @return      the modified [ApplicationData].
     */
    fun setString(key: String, value: String): ApplicationData

    /**
     * Sets application specific data as a `long`.
     *
     * Like [.setData] but encodes the given value using [CBOR](http://cbor.io/) before storing it.
     *
     * @param key   the key for the data.
     * @param value the value.
     * @return      the modified [ApplicationData].
     */
    fun setNumber(key: String, value: Long): ApplicationData

    /**
     * Sets application specific data as a boolean.
     *
     * Like [.setData] but encodes the given value using [CBOR](http://cbor.io/) before storing it.
     *
     * @param key   the key for the data.
     * @param value the value.
     * @return      the modified [ApplicationData].
     */
    fun setBoolean(key: String, value: Boolean): ApplicationData

    /**
     * Sets application specific data as a [NameSpacedData].
     *
     * Like [.setData] but encodes the given value as [CBOR](http://cbor.io/) using
     * [NameSpacedData.encodeAsCbor] before storing it.
     *
     * @param key   the key for the data.
     * @param value the value.
     * @return      the modified [ApplicationData].
     */
    fun setNameSpacedData(key: String, value: NameSpacedData): ApplicationData

    /**
     * Returns whether the [ApplicationData] has a value for the key provided.
     *
     * @param key the key for the data.
     * @return    true if the key exists, false else.
     */
    fun keyExists(key: String): Boolean

    /**
     * Gets application specific data.
     *
     * Gets data previously stored with [.setData].
     *
     * @param  key the key for the data.
     * @return the value.
     * @throws IllegalArgumentException if the data element does not exist.
     */
    fun getData(key: String): ByteArray

    /**
     * Gets application specific data as a string.
     *
     * Takes the data returned by [.getData] and decodes it as a CBOR string.
     *
     * @param  key the key for the data.
     * @return the value.
     * @throws IllegalArgumentException if the data element does not exist.
     * @throws IllegalArgumentException if the data isn't a CBOR encoded string.
     */
    fun getString(key: String): String

    /**
     * Gets application specific data as a `long`.
     *
     * Takes the data returned by [.getData] and decodes it as a `long`.
     *
     * @param  key the key for the data.
     * @return the value.
     * @throws IllegalArgumentException if the data element does not exist.
     * @throws IllegalArgumentException if the data isn't a CBOR encoded `long`.
     */
    fun getNumber(key: String): Long

    /**
     * Gets application specific data as a `boolean`.
     *
     * Takes the data returned by [.getData] and decodes it as a `boolean`.
     *
     * @param  key the key for the data.
     * @return the value.
     * @throws IllegalArgumentException if the data element does not exist.
     * @throws IllegalArgumentException if the data isn't a CBOR encoded `boolean`.
     */
    fun getBoolean(key: String): Boolean

    /**
     * Gets application specific data as a [NameSpacedData].
     *
     * Takes the data returned by [.getData] and decodes it as a
     * [NameSpacedData] using [NameSpacedData.fromEncodedCbor].
     *
     * @param  key the key for the data.
     * @return the value.
     * @throws IllegalArgumentException if the data element does not exist.
     * @throws IllegalArgumentException if the data isn't encoded as a [NameSpacedData].
     */
    fun getNameSpacedData(key: String): NameSpacedData
}