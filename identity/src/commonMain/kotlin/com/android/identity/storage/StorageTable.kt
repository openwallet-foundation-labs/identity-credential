package com.android.identity.storage

import kotlinx.datetime.Instant
import kotlinx.io.bytestring.ByteString

/**
 * A storage unit that holds a collection of items. An item is a [ByteString] indexed by a
 * unique key.
 *
 * [StorageTable] has two optional features: partitioning and expiration.
 *
 * When the table is partitioned, each item is actually indexed by a key pair (partitionId, key).
 * Keys are unique only within a particular partition.
 *
 * When item expiration is enabled, each item can be given optional expiration time. An item is
 * (conceptually) silently and automatically deleted once clock time goes past expiration time
 * (in other words, an item still exists at the expiration time).
 */
interface StorageTable {
    /**
     * Gets data.
     *
     * This gets data previously stored with [StorageTable.insert].
     *
     * @param key the key used to identify the data.
     * @return The stored data or `null` if there is no data for the given key.
     */
    suspend fun get(key: String, partitionId: String = ""): ByteString?

    /**
     * Stores new data.
     *
     * The data can later be retrieved using [StorageTable.get].
     *
     * @param key the key used to identify the data.
     * @param data the data to store.
     */
    suspend fun insert(
        data: ByteString,
        partitionId: String = "",
        key: String = "",
        expiration: Instant = Instant.DISTANT_FUTURE
    ): String

    /**
     * Updates data that is already stored in the engine.
     *
     * The data can later be retrieved using [.get]. If data already
     * exists for the given key it will be overwritten.
     *
     * @param key the key used to identify the data.
     * @param data the data to store.
     */
    suspend fun update(
        key: String,
        data: ByteString,
        partitionId: String = "",
        expiration: Instant? = null
    )

    /**
     * Deletes data.
     *
     * If there is no data for the given key, this is a no-op.
     *
     * @param key the key used to identify the data.
     */
    suspend fun delete(
        key: String,
        partitionId: String = "",
    ): Boolean

    /**
     * Deletes all data previously stored.
     */
    suspend fun deleteAll()

    /**
     * Enumerate keys of the records with given table and partitionId in key lexicographic order.
     *
     * If [limit] is given, no more than the given number of keys are returned. If [afterKey]
     * is given only keys that follow the give key are returned. By specifying the desired [limit]
     * and passing last key from the previously returned list as [afterKey] allows enumerating
     * all of the key in manageable chunks.
     */
    suspend fun enumerate(
        partitionId: String = "",
        afterKey: String = "",
        limit: Int = Int.MAX_VALUE
    ): List<String>

}