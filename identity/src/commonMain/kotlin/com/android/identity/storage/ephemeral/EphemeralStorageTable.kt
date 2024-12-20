package com.android.identity.storage.ephemeral

import com.android.identity.storage.StorageTable
import com.android.identity.storage.StorageTableSpec
import com.android.identity.util.toBase64Url
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.io.bytestring.ByteString
import kotlin.random.Random

internal class EphemeralStorageTable(
    internal val spec: StorageTableSpec,
    private val clock: Clock
): StorageTable {
    private val lock = Mutex()
    private var storedData = mutableListOf<Item>()
    private var earliestExpiration: Instant = Instant.DISTANT_FUTURE

    override suspend fun get(key: String, partitionId: String): ByteString? {
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
        data: ByteString,
        partitionId: String,
        key: String,
        expiration: Instant
    ): String {
        return lock.withLock {
            var index: Int
            var keyToUse = key
            if (keyToUse.isEmpty()) {
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
                    throw IllegalArgumentException(
                        "Element with partitionId = '$partitionId' key = '$key' already exists"
                    )
                }
            }
            check(index < 0)
            updateEarliestExpiration(expiration)
            storedData.add(-index - 1, Item(partitionId, keyToUse, data, expiration))
            keyToUse
        }
    }

    override suspend fun update(
        key: String,
        data: ByteString,
        partitionId: String,
        expiration: Instant?
    ) {
        lock.withLock {
            val index = storedData.binarySearch(Item(partitionId, key))
            if (index < 0) {
                throw IllegalArgumentException("No element with partitionId='$partitionId' key='$key'")
            }
            val item = storedData[index]
            if (item.expired(clock.now())) {
                throw IllegalArgumentException("No element with partitionId='$partitionId' key='$key' (expired)")
            }
            item.value = data
            if (expiration != null) {
                updateEarliestExpiration(expiration)
                item.expiration = expiration
            }
        }
    }

    override suspend fun delete(key: String, partitionId: String): Boolean {
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

    override suspend fun enumerate(partitionId: String, afterKey: String, limit: Int): List<String> {
        return lock.withLock {
            var index = storedData.binarySearch(Item(partitionId, afterKey))
            if (index < 0) {
                index = -index - 1
            } else {
                index++
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

    internal suspend fun purgeExpired() {
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
        val partitionId: String,
        val key: String,
        var value: ByteString = EMPTY,
        var expiration: Instant = Instant.DISTANT_FUTURE
    ): Comparable<Item> {
        override fun compareTo(other: Item): Int {
            val c = partitionId.compareTo(other.partitionId)
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