package com.android.identity.storage

/**
 * [StorageTable] name and features.
 *
 * NB: Once the table is created for the first time, its features must stay the same.
 */
data class StorageTableSpec(
    val name: String, /** name of the table */
    val supportPartitions: Boolean,  /** true if partitions are supported (see [StorageTable]) */
    val supportExpiration: Boolean,  /** true if expiration is supported (see [StorageTable]) */
)