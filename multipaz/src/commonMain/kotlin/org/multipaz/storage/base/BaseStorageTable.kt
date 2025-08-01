package org.multipaz.storage.base

import org.multipaz.storage.StorageTable
import org.multipaz.storage.StorageTableSpec
import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString

abstract class BaseStorageTable(val spec: StorageTableSpec): StorageTable {
    /** Reclaim storage that is taken up by the expired entries. */
    abstract suspend fun purgeExpired()

    protected fun checkExpiration(expiration: Instant) {
        if (!this.spec.supportExpiration && expiration < Instant.DISTANT_FUTURE) {
            throw IllegalArgumentException("Expiration is not supported")
        }
    }

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

    protected fun checkKey(key: String) {
        if (key.isEmpty()) {
            throw IllegalArgumentException("Empty key is not allowed")
        }
        if (key.length > BaseStorage.MAX_KEY_SIZE) {
            throw IllegalArgumentException("Key is too long")
        }
    }

    protected fun checkLimit(limit: Int) {
        if (limit < 0) {
            throw IllegalArgumentException("Negative limit: $limit")
        }
    }

    protected fun recordDescription(key: String, partitionId: String?): String {
        return if (spec.supportPartitions) {
            "partitionId='$partitionId' key='$key' (table '${spec.name}')"
        } else {
            "key='$key' (table '${spec.name}')"
        }
    }
}