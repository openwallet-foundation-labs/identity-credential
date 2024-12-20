package com.android.identity.storage

/**
 * Storage (in most cases persistent) that holds data items. Collection of items are organized
 * in named [StorageTable]s.
 */
interface Storage {
    suspend fun getTable(tableSpec: StorageTableSpec): StorageTable
    suspend fun purgeExpired()
}