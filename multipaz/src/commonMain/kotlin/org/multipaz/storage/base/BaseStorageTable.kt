package org.multipaz.storage.base

import org.multipaz.storage.StorageTable
import org.multipaz.storage.StorageTableSpec
import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString

abstract class BaseStorageTable(val spec: StorageTableSpec): StorageTable {
    /** Reclaim storage that is taken up by the expired entries. */
    abstract suspend fun purgeExpired()

    /**
     * Verifies that [expiration] is supported by this table.
     *
     * @throws IllegalArgumentException if expiration is not supported.
     */
    protected fun checkExpiration(expiration: Instant) {
        if (!this.spec.supportExpiration && expiration < Instant.DISTANT_FUTURE) {
            throw IllegalArgumentException("Expiration is not supported")
        }
    }

    /**
     * Verifies that [partitionId] conforms to this table's partitioning configuration.
     *
     * @throws IllegalArgumentException if the partition ID is missing, unsupported, or too long.
     */
    protected fun checkPartition(partitionId: String?) {
        if (this.spec.supportPartitions) {
            if (partitionId == null) {
                throw IllegalArgumentException("partitionId is required")
            }
            if (partitionId.length > BaseStorage.MAX_KEY_SIZE) {
                throw IllegalArgumentException("partitionId is too long")
            }
        } else {
            if (partitionId != null) {
                throw IllegalArgumentException("Partitioning is not supported")
            }
        }
    }

    /**
     * Verifies that [key] is non-empty and does not exceed the maximum key length.
     *
     * @throws IllegalArgumentException if the key is empty or too long.
     */
    protected fun checkKey(key: String) {
        if (key.isEmpty()) {
            throw IllegalArgumentException("Empty key is not allowed")
        }
        if (key.length > BaseStorage.MAX_KEY_SIZE) {
            throw IllegalArgumentException("Key is too long")
        }
    }

    /**
     * Verifies that [limit] is non-negative.
     *
     * @throws IllegalArgumentException if the limit is negative.
     */
    protected fun checkLimit(limit: Int) {
        if (limit < 0) {
            throw IllegalArgumentException("Negative limit: $limit")
        }
    }

    /**
     * Returns a human-readable description of a record identified by [key] and [partitionId].
     */
    protected fun recordDescription(key: String, partitionId: String?): String {
        return if (spec.supportPartitions) {
            "partitionId='$partitionId' key='$key' (table '${spec.name}')"
        } else {
            "key='$key' (table '${spec.name}')"
        }
    }
}