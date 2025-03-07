package org.multipaz.storage

/**
 * Storage (in most cases persistent) that holds data items. Collection of items are organized
 * in named [StorageTable]s.
 */
interface Storage {
    /**
     * Get the table with specific name and features.
     *
     * In order to avoid situation where several parts of the app define a table with the
     * same name, this method throws [IllegalArgumentException] when there are multiple
     * [StorageTableSpec] objects that define a table with the same name.
     */
    suspend fun getTable(spec: StorageTableSpec): StorageTable

    /**
     * Reclaim the storage occupied by expired entries across all tables in this [Storage]
     * object (even if these tables were never accessed using [getTable] in this
     * session).
     */
    suspend fun purgeExpired()
}