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

package com.android.identity.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.identity.credential.Credential;
import com.android.identity.credential.NameSpacedData;

import co.nstant.in.cbor.model.DataItem;

/**
 * An interface to an object storing application data in a key-value pair manner.
 *
 * <p>This interface exists to support applications which wish to store additional data it wants to
 * associate with any object, such as a {@link Credential} or {@link Credential.AuthenticationKey}.
 * For example, on a {@link Credential} object one could use this to store the document
 * type (e.g. DocType for 18013-5 credentials), user-visible name, logos/background, state,
 * and so on.
 */
public interface ApplicationData {

    /**
     * Sets application specific data.
     *
     * <p>Use {@link #getData(String)} to read the data back.
     *
     * @param key   the key for the data.
     * @param value the value or {@code null} to remove.
     * @return      the modified {@link ApplicationData}.
     */
    @NonNull ApplicationData setData(@NonNull String key, @Nullable byte[] value);

    /**
     * Sets application specific data as a string.
     *
     * <p>Like {@link #setData(String, byte[])} but encodes the given value
     * using <a href="http://cbor.io/">CBOR</a> before storing it.
     *
     * @param key   the key for the data.
     * @param value the value
     * @return      the modified {@link ApplicationData}.
     */
    @NonNull ApplicationData setString(@NonNull String key, @NonNull String value);

    /**
     * Sets application specific data as a {@code long}.
     *
     * <p>Like {@link #setData(String, byte[])} but encodes the given value
     * using <a href="http://cbor.io/">CBOR</a> before storing it.
     *
     * @param key   the key for the data.
     * @param value the value.
     * @return      the modified {@link ApplicationData}.
     */
    @NonNull ApplicationData setNumber(@NonNull String key, long value);

    /**
     * Sets application specific data as a boolean.
     *
     * <p>Like {@link #setData(String, byte[])} but encodes the given value
     * using <a href="http://cbor.io/">CBOR</a> before storing it.
     *
     * @param key   the key for the data.
     * @param value the value.
     * @return      the modified {@link ApplicationData}.
     */
    @NonNull ApplicationData setBoolean(@NonNull String key, boolean value);

    /**
     * Sets application specific data as a {@link NameSpacedData}.
     *
     * <p>Like {@link #setData(String, byte[])} but encodes the given value
     * as <a href="http://cbor.io/">CBOR</a> using {@link NameSpacedData#encodeAsCbor()}
     * before storing it.
     *
     * @param key   the key for the data.
     * @param value the value.
     * @return      the modified {@link ApplicationData}.
     */
    @NonNull ApplicationData setNameSpacedData(@NonNull String key, @NonNull NameSpacedData value);

    /**
     * Returns whether the {@link ApplicationData} has a value for the key provided.
     *
     * @param key the key for the data.
     * @return    true if the key exists, false else.
     */
    boolean keyExists(@NonNull String key);

    /**
     * Gets application specific data.
     *
     * <p>Gets data previously stored with {@link #setData(String, byte[])}.
     *
     * @param  key the key for the data.
     * @return the value.
     * @throws IllegalArgumentException if the data element does not exist.
     */
    @NonNull byte[] getData(@NonNull String key);

    /**
     * Gets application specific data as a string.
     *
     * <p>Takes the data returned by {@link #getData(String)} and decodes it as a CBOR string.
     *
     * @param  key the key for the data.
     * @return the value.
     * @throws IllegalArgumentException if the data element does not exist.
     * @throws IllegalArgumentException if the data isn't a CBOR encoded string.
     */
    @NonNull String getString(@NonNull String key);

    /**
     * Gets application specific data as a {@code long}.
     *
     * <p>Takes the data returned by {@link #getData(String)} and decodes it as a {@code long}.
     *
     * @param  key the key for the data.
     * @return the value.
     * @throws IllegalArgumentException if the data element does not exist.
     * @throws IllegalArgumentException if the data isn't a CBOR encoded {@code long}.
     */
    long getNumber(@NonNull String key);

    /**
     * Gets application specific data as a {@code boolean}.
     *
     * <p>Takes the data returned by {@link #getData(String)} and decodes it as a {@code boolean}.
     *
     * @param  key the key for the data.
     * @return the value.
     * @throws IllegalArgumentException if the data element does not exist.
     * @throws IllegalArgumentException if the data isn't a CBOR encoded {@code boolean}.
     */
    boolean getBoolean(@NonNull String key);

    /**
     * Gets application specific data as a {@link NameSpacedData}.
     *
     * <p>Takes the data returned by {@link #getData(String)} and decodes it as a
     * {@link NameSpacedData} using {@link NameSpacedData#fromEncodedCbor(byte[])}.
     *
     * @param  key the key for the data.
     * @return the value.
     * @throws IllegalArgumentException if the data element does not exist.
     * @throws IllegalArgumentException if the data isn't encoded as a {@link NameSpacedData}.
     */
    @NonNull NameSpacedData getNameSpacedData(@NonNull String key);
}
