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

/**
 * A simple interface for storing key/value-pairs.
 */
interface StorageEngine {
    /**
     * Gets data.
     *
     * This gets data previously stored with [.put].
     *
     * @param key the key used to identify the data.
     * @return The stored data or `null` if there is no data for the given key.
     */
    operator fun get(key: String): ByteArray?

    /**
     * Stores data.
     *
     * The data can later be retrieved using [.get]. If data already
     * exists for the given key it will be overwritten.
     *
     * @param key the key used to identify the data.
     * @param data the data to store.
     */
    fun put(key: String, data: ByteArray)

    /**
     * Deletes data.
     *
     * If there is no data for the given key, this is a no-op.
     *
     * @param key the key used to identify the data.
     */
    fun delete(key: String)

    /**
     * Deletes all data previously stored.
     */
    fun deleteAll()

    /**
     * Enumerates the keys for which data is currently stored.
     *
     * @return A collection of keys.
     */
    fun enumerate(): Collection<String>
}