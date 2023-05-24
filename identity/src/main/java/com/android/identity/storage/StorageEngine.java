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

package com.android.identity.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;

/**
 * A simple interface for storing key/value-pairs.
 */
public interface StorageEngine {

    /**
     * Gets data.
     *
     * <p>This gets data previously stored with {@link #put(String, byte[])}.
     *
     * @param key the key used to identify the data.
     * @return The stored data or {@code null} if there is no data for the given key.
     */
    @Nullable byte[] get(@NonNull String key);

    /**
     * Stores data.
     *
     * <p>The data can later be retrieved using {@link #get(String)}. If data already
     * exists for the given key it will be overwritten.
     *
     * @param key the key used to identify the data.
     * @param data the data to store.
     */
    void put(@NonNull String key, @NonNull byte[] data);

    /**
     * Deletes data.
     *
     * <p>If there is no data for the given key, this is a no-op.
     *
     * @param key the key used to identify the data.
     */
    void delete(@NonNull String key);

    /**
     * Deletes all data previously stored.
     */
    void deleteAll();

    /**
     * Enumerates the keys for which data is currently stored.
     *
     * @return A collection of keys.
     */
    @NonNull
    Collection<String> enumerate();
}
