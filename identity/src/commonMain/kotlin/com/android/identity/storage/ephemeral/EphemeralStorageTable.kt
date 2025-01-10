package com.android.identity.storage.ephemeral

import com.android.identity.storage.KeyExistsStorageException
import com.android.identity.storage.NoRecordStorageException
import com.android.identity.storage.base.BaseStorageTable
import com.android.identity.storage.StorageTableSpec
import com.android.identity.util.toBase64Url
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.io.bytestring.ByteString
import kotlin.math.abs
import kotlin.random.Random

internal class EphemeralStorageTable(
    spec: StorageTableSpec,
    private val clock: Clock
): BaseStorageTable(spec) {
    private val lock = Mutex()
    private var storedData = mutableListOf<Item>()
    private var earliestExpiration: Instant = Instant.DISTANT_FUTURE

    override suspend fun get(key: String, partitionId: String?): ByteString? {
        checkPartition(partitionId)
        return lock.withLock {
            val index = storedData.binarySearch(Item(partitionId, key))
            if (index < 0) {
                null
            } else {
                val data = storedData[index]
                if (data.expired(clock.now())) null else data.value
            }
        }
    }

    override suspend fun insert(
        key: String?,
        data: ByteString,
        partitionId: String?,
        expiration: Instant
    ): String {
        checkPartition(partitionId)
        checkExpiration(expiration)
        if (key != null) {
            checkKey(key)
        }
        return lock.withLock {
            var index: Int
            var keyToUse = key
            if (keyToUse == null) {
                do {
                    keyToUse = Random.Default.nextBytes(9).toBase64Url()
                    index = storedData.binarySearch(Item(partitionId, keyToUse))
                } while (index >= 0)
            } else {
                index = storedData.binarySearch(Item(partitionId, keyToUse))
                if (index >= 0) {
                    val item = storedData[index]
                    if (item.expired(clock.now())) {
                        // Stale entry, can be reused
                        updateEarliestExpiration(expiration)
                        item.expiration = expiration
                        item.value = data
                        return@withLock keyToUse
                    }
                    throw KeyExistsStorageException(
                        "Record with ${recordDescription(key!!, partitionId)} already exists"
                    )
                }
            }
            check(index < 0)
            updateEarliestExpiration(expiration)
            storedData.add(-index - 1, Item(partitionId, keyToUse!!, data, expiration))
            keyToUse
        }
    }

    override suspend fun update(
        key: String,
        data: ByteString,
        partitionId: String?,
        expiration: Instant?
    ) {
        checkPartition(partitionId)
        if (expiration != null) {
            checkExpiration(expiration)
        }
        lock.withLock {
            val index = storedData.binarySearch(Item(partitionId, key))
            if (index < 0) {
                throw NoRecordStorageException(
                    "No record with ${recordDescription(key, partitionId)}")
            }
            val item = storedData[index]
            if (item.expired(clock.now())) {
                throw NoRecordStorageException(
                    "No record with ${recordDescription(key, partitionId)} (expired)")
            }
            item.value = data
            if (expiration != null) {
                updateEarliestExpiration(expiration)
                item.expiration = expiration
            }
        }
    }

    override suspend fun delete(key: String, partitionId: String?): Boolean {
        checkPartition(partitionId)
        return lock.withLock {
            val index = storedData.binarySearch(Item(partitionId, key))
            if (index < 0 || storedData[index].expired(clock.now())) {
                false
            } else {
                storedData.removeAt(index)
                true
            }
        }
    }

    override suspend fun deleteAll() {
        lock.withLock {
            storedData.clear()
        }
    }

    override suspend fun enumerate(
        partitionId: String?,
        afterKey: String?,
        limit: Int
    ): List<String> {
        checkPartition(partitionId)
        checkLimit(limit)
        if (limit == 0) {
            return listOf()
        }
        return lock.withLock {
            var index = if (afterKey == null) {
                val spot = storedData.binarySearch(Item(partitionId, ""))
                if (spot > 0) spot else -(spot + 1)
            } else {
                abs(storedData.binarySearch(Item(partitionId, afterKey)) + 1)
            }
            val now = clock.now()
            val keyList = mutableListOf<String>()
            while (keyList.size < limit && index < storedData.size) {
                val data = storedData[index]
                if (data.partitionId != partitionId) {
                    break
                }
                if (!data.expired(now)) {
                    keyList.add(data.key)
                }
                index++
            }
            keyList.toList()
        }
    }

    private fun updateEarliestExpiration(expiration: Instant) {
        if (earliestExpiration > expiration) {
            earliestExpiration = expiration
        }
    }

    override suspend fun purgeExpired() {
        if (!spec.supportExpiration) {
            throw IllegalStateException("This table does not support expiration")
        }
        lock.withLock {
            val now = clock.now()
            if (earliestExpiration < now) {
                earliestExpiration = Instant.DISTANT_FUTURE
                val unexpired = mutableListOf<Item>()
                for (item in storedData) {
                    if (!item.expired(now)) {
                        updateEarliestExpiration(item.expiration)
                        unexpired.add(item)
                    }
                }
                storedData = unexpired
            }
        }
    }

    private class Item(
        val partitionId: String?,
        val key: String,
        var value: ByteString = EMPTY,
        var expiration: Instant = Instant.DISTANT_FUTURE
    ): Comparable<Item> {
        override fun compareTo(other: Item): Int {
            val c = if (partitionId == null) {
                if (other.partitionId == null) 0 else -1
            } else if (other.partitionId == null) {
                1
            } else {
                partitionId.compareTo(other.partitionId)
            }
            return if (c != 0) c else key.compareTo(other.key)
        }

        fun expired(now: Instant): Boolean {
            return expiration < now
        }
    }

    companion object {
        val EMPTY = ByteString()
    }
}