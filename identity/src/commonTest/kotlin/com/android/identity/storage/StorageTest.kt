package com.android.identity.storage

import com.android.identity.storage.ephemeral.EphemeralStorage
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.io.bytestring.decodeToString
import kotlinx.io.bytestring.encodeToByteString
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class StorageTest {
    // NB: this in-memory database is shared across tests. If a particular test requires
    // empty database, create on specifically for that test using test name as database name.
    private val storage = EphemeralStorage(TestClock)

    @Test
    fun testSimple() {
        runBlocking {
            val tableSpec = StorageTableSpec(
                name = "testSimple",
                supportPartitions = false,
                supportExpiration = false
            )
            val table = storage.getTable(tableSpec)
            assertEquals(0, table.enumerate().size.toLong())
            assertNull(table.get("foo"))

            val data = "Foobar".encodeToByteString()
            table.insert(data, key = "foo")
            assertEquals(table.get("foo"), data)
            assertEquals(1, table.enumerate().size.toLong())
            assertEquals("foo", table.enumerate().iterator().next())
            assertNull(table.get("bar"))

            val data2 = "Buzz".encodeToByteString()
            table.insert(data2, key = "bar")
            assertEquals(table.get("bar"), data2)
            assertEquals(2, table.enumerate().size.toLong())
            table.delete("foo")
            assertNull(table.get("foo"))
            assertNotNull(table.get("bar"))
            assertEquals(1, table.enumerate().size.toLong())
            table.delete("bar")
            assertNull(table.get("bar"))
            assertEquals(0, table.enumerate().size.toLong())
        }
    }

    @Test
    fun testInsertAndQuery() {
        runBlocking {
            val tableSpec = StorageTableSpec(
                name = "testInsertAndQuery",
                supportPartitions = true,
                supportExpiration = false
            )
            val table = storage.getTable(tableSpec)
            table.insert("bad1".encodeToByteString(), partitionId = "client0")
            val key = table.insert("good".encodeToByteString(), partitionId = "client1")
            table.insert("bad2".encodeToByteString(), partitionId = "client1")
            table.insert("bad3".encodeToByteString(), partitionId = "client3")
            assertNotEquals("", key)
            val data = table.get(key = key, partitionId = "client1")
            assertEquals("good", data!!.decodeToString())
        }
    }

    @Test
    fun testEnumerate() {
        runBlocking {
            val tableSpec = StorageTableSpec(
                name = "testEnumerate",
                supportPartitions = true,
                supportExpiration = false
            )
            val table = storage.getTable(tableSpec)
            (0..19).forEach { i ->
                table.insert(partitionId = "client0", data = "bad$i".encodeToByteString())
                table.insert(partitionId = "client2", data = "bad$i".encodeToByteString())
            }
            val keys = (0..19).map { i ->
                table.insert(partitionId = "client1", data = "good$i".encodeToByteString())
            }.toSet()
            assertEquals(20, keys.size)
            val chunk1 = table.enumerate(
                partitionId = "client1",
                limit = 9
            )
            val chunk2 = table.enumerate(
                partitionId = "client1",
                afterKey = chunk1.last(),
                limit = 14
            )
            assertEquals(9, chunk1.size)
            assertEquals(11, chunk2.size)
            assertEquals(keys, (chunk1 + chunk2).toSet())
            for (key in keys) {
                val data = table.get(partitionId = "client1", key = key)!!.decodeToString()
                assertTrue(data.startsWith("good"))
            }
        }
    }

    @Test
    fun testUpdate() {
        runBlocking {
            val tableSpec = StorageTableSpec(
                name = "testUpdate",
                supportPartitions = true,
                supportExpiration = false
            )
            val table = storage.getTable(tableSpec)
            val key = table.insert(partitionId = "client1", data = "bad".encodeToByteString())
            table.update(partitionId = "client1", key = key, data = "good".encodeToByteString())
            val data = table.get(partitionId = "client1", key = key)
            assertEquals("good", data!!.decodeToString())
        }
    }

    @Test
    fun testDelete() {
        runBlocking {
            val tableSpec = StorageTableSpec(
                name = "testDelete",
                supportPartitions = true,
                supportExpiration = false
            )
            val table = storage.getTable(tableSpec)
            val key = table.insert(partitionId = "client1", data = "data".encodeToByteString())
            assertFalse(table.delete(partitionId = "client0", key = key))
            assertFalse(table.delete(partitionId = "client1", key = "#fake_key"))
            assertTrue(table.delete(partitionId = "client1", key = key))
            val data = table.get(partitionId = "client1", key = key)
            assertNull(data)
        }
    }

    @Test
    fun testExpiration() {
        runBlocking {
            val tableSpec = StorageTableSpec(
                name = "testExpiration",
                supportPartitions = true,
                supportExpiration = true
            )
            val table = storage.getTable(tableSpec)
            TestClock.time = Instant.parse("2024-12-20T11:35:00Z")
            val time3 = TestClock.time + 3.minutes
            val time5 = TestClock.time + 5.minutes
            val time6 = TestClock.time + 6.minutes
            val time8 = TestClock.time + 8.minutes
            val id8 = table.insert("8.minutes".encodeToByteString(), expiration = time8)
            val id3 = table.insert("3.minutes".encodeToByteString(), expiration = time3)
            val id5 = table.insert("5.minutes".encodeToByteString(), expiration = time5)
            assertEquals(setOf(id3, id5, id8), table.enumerate().toSet())
            TestClock.time = time5
            assertEquals(setOf(id5, id8), table.enumerate().toSet())
            table.insert("reused id".encodeToByteString(), key = id3, expiration = time5)
            assertEquals(setOf(id3, id5, id8), table.enumerate().toSet())
            TestClock.time = time6
            assertEquals(setOf(id8), table.enumerate().toSet())
            assertEquals("8.minutes", table.get(id8)!!.decodeToString())
            storage.purgeExpired()
            assertEquals(setOf(id8), table.enumerate().toSet())
        }
    }

    object TestClock: Clock {
        internal var time: Instant = Instant.DISTANT_PAST
        override fun now(): Instant = time
    }
}