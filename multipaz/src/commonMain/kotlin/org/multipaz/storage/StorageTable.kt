package org.multipaz.storage

import org.multipaz.storage.base.BaseStorageTable
import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString

/**
 * A storage unit that holds a collection of items. An item is a [ByteString] indexed by a
 * unique key.
 *
 * [BaseStorageTable] has two optional features: partitioning and expiration.
 *
 * When the table is partitioned, each item is actually indexed by a key pair (partitionId, key).
 * Keys are unique only within a particular partition.
 *
 * When item expiration is enabled, each item can be given optional expiration time. An item is
 * (conceptually) silently and automatically deleted once clock time goes past expiration time
 * (in other words, an item still exists at exactly the expiration time).
 */
interface StorageTable {
    /**
     * The [Storage] the table belongs to.
     */
    val storage: Storage

    /**
     * Gets data.
     *
     * This gets data previously stored with [StorageTable.insert].
     *
     * - [key] is the key used to identify the data.
     * - [partitionId] secondary key. If partitioning is supported
     *   (see [StorageTableSpec.supportExpiration]), it must be non-null. If partitioning is not
     *   supported it must be null.
     *
     * Returns the stored data or `null` if there is no data for the given key (including the case
     * when the record has expired).
     */
    suspend fun get(key: String, partitionId: String? = null): ByteString?

    /**
     * Stores new data.
     *
     * The data can later be retrieved using [StorageTable.get].
     *
     * - [key] the key used to identify the data. If null, and new unique key will be generated.
     *   if not null, the key (or (partitionId,key) pair if partitioning is enabled) must be unique
     *   and [KeyExistsStorageException] is thrown if the key (or (partitionId,key) pair) already
     *   exists in the table.
     * - [partitionId] secondary key. If partitioning is supported
     *   (see [StorageTableSpec.supportExpiration]), it must be non-null. If partitioning is not
     *   supported it must be null.
     * - [expiration] if expiration is not supported (see [StorageTableSpec]), this must be
     *   [Instant.DISTANT_FUTURE] which is default value. If expiration is supported this should
     *   be last moment of time when the newly-created record still exists. Expired records are
     *   not accessible using [Storage] APIs, and the storage they occupy can be reclaimed
     *   at any moment.
     * - [data] the data to store.
     *
     * Returns the key for the newly-inserted record. Generated keys only contain letters, digits,
     * and characters '_' and '-' (base64url character set). This restriction does not apply to
     * the user-provided keys.
     */
    suspend fun insert(
        key: String?,
        data: ByteString,
        partitionId: String? = null,
        expiration: Instant = Instant.DISTANT_FUTURE
    ): String

    /**
     * Updates data that is already stored in the engine.
     *
     * The data can later be retrieved using [get]. The record with the given key (or
     * (partitionId, key) pair when partitions are enabled) must exist in the database or
     * [NoRecordStorageException] will be thrown.
     *
     * - [key] the key used to identify the data.
     * - [partitionId] secondary key. If partitioning is supported
     *   (see [StorageTableSpec.supportExpiration]), it must be non-null. If partitioning is not
     *   supported it must be null.
     * - [expiration] if expiration is not supported (see [StorageTableSpec]), this must be `null`.
     *   Otherwise, if expiration is given, it is updated, and if it is `null`, it is left as it
     *   was before.
     * - [data] the data to store.
     */
   suspend fun update(
        key: String,
        data: ByteString,
        partitionId: String? = null,
        expiration: Instant? = null
    )

    /**
     * Deletes data.
     *
     * - [key] the key used to identify the data.
     * - [partitionId] secondary key. If partitioning is supported in [StorageTableSpec] this must
     *   be non-null. If partitioning is not supported it must be null.
     *
     *  Returns `true` if the record was found and successfully deleted. Returns `false` if
     *  the record was not found (including the case when it is expired).
     */
    suspend fun delete(
        key: String,
        partitionId: String? = null,
    ): Boolean

    /**
     * Deletes all data previously stored in this table.
     */
    suspend fun deleteAll()

    /**
     * Deletes all data previously stored in this the given partition.
     *
     * Partitioning must be supported in [StorageTableSpec] for this table. All records in the
     * given partition are erased.
     */
    suspend fun deletePartition(partitionId: String)

    /**
     * Enumerate keys of the records with given table and partitionId in key lexicographic order.
     *
     * - [partitionId] secondary key. If partitioning is supported
     *   (see [StorageTableSpec.supportExpiration]), it must be non-null. If partitioning is not
     *   supported it must be null.
     * - [afterKey] if given only keys that follow the given key lexicographically are returned. If
     *   not given, enumeration starts from the lexicographically first key.
     * - [limit] if given, no more than the given number of keys are returned.
     *
     * To enumerate a large table completely in manageable chunks, specify the desired [limit]
     * to repeated [enumerate] calls and pass last key from the previously returned list as
     * [afterKey].
     */
    suspend fun enumerate(
        partitionId: String? = null,
        afterKey: String? = null,
        limit: Int = Int.MAX_VALUE
    ): List<String>

    /**
     * Enumerate the records with given table and partitionId in key lexicographic order.
     *
     * This is similar to [enumerate], but it returns key/data pairs stored in the table rather
     * than just keys.
     */
    suspend fun enumerateWithData(
        partitionId: String? = null,
        afterKey: String? = null,
        limit: Int = Int.MAX_VALUE
    ): List<Pair<String, ByteString>>
}