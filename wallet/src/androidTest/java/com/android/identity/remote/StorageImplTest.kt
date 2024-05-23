package com.android.identity.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.identity.flow.environment.Storage
import com.android.identity.issuance.remote.StorageImpl
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageImplTest {
    lateinit var storage: Storage

    @Before
    fun initDb() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        storage = StorageImpl(appContext, "test")
    }

    @Test
    fun insertAndQuery() {
        runBlocking {
            storage.insert("InsertAndQuery", "client0", ByteString("bad1".toByteArray()))
            val key = storage.insert("InsertAndQuery", "client1", ByteString("good".toByteArray()))
            storage.insert("InsertAndQuery", "client1", ByteString("bad2".toByteArray()))
            storage.insert("InsertAndQuery", "client3", ByteString("bad3".toByteArray()))
            Assert.assertNotEquals("", key)
            val data = storage.get("InsertAndQuery", "client1", key)
            Assert.assertEquals("good", data!!.toByteArray().toString(Charsets.UTF_8))
        }
    }

    @Test
    fun testEnumerate() {
        runBlocking {
            (0..19).forEach { i ->
                storage.insert("Enumerate", "client0", ByteString("bad$i".toByteArray()))
                storage.insert("Enumerate", "client2", ByteString("bad$i".toByteArray()))
            }
            val keys = (0..19).map { i ->
                storage.insert("Enumerate", "client1", ByteString("good$i".toByteArray()))
            }.toSet()
            Assert.assertEquals(20, keys.size)
            val chunk1 = storage.enumerate("Enumerate", "client1", limit = 10)
            val chunk2 = storage.enumerate("Enumerate", "client1",
                notBeforeKey = chunk1.last(), limit = 12)
            Assert.assertEquals(10, chunk1.size)
            Assert.assertEquals(10, chunk2.size)
            Assert.assertEquals(keys, (chunk1 + chunk2).toSet())
            for (key in keys) {
                val data = storage.get("Enumerate", "client1", key)!!.toByteArray()
                Assert.assertTrue(data.toString(Charsets.UTF_8).startsWith("good"))
            }
        }
    }

    @Test
    fun testUpdate() {
        runBlocking {
            val key = storage.insert("Update", "client1", ByteString("bad".toByteArray()))
            storage.update("Update", "client1", key, ByteString("good".toByteArray()))
            val data = storage.get("Update", "client1", key)
            Assert.assertEquals("good", data!!.toByteArray().toString(Charsets.UTF_8))
        }
    }

    @Test
    fun testDelete() {
        runBlocking {
            val key = storage.insert("Delete", "client1", ByteString("data".toByteArray()))
            Assert.assertFalse(storage.delete("Delete", "client0", key))
            Assert.assertFalse(storage.delete("Delete", "client1", "fake_key"))
            Assert.assertTrue(storage.delete("Delete", "client1", key))
            val data = storage.get("Delete", "client1", key)
            Assert.assertNull(data)
        }
    }
}