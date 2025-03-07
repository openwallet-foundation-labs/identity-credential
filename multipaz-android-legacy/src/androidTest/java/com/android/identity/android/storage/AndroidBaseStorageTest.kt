/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.identity.android.storage

import androidx.test.platform.app.InstrumentationRegistry
import org.multipaz.storage.StorageEngine
import org.multipaz.util.toHex
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.nio.charset.StandardCharsets

class AndroidStorageTest {

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SystemFileSystem.delete(Path(context.dataDir.path, "testdata.bin"), false)
    }

    @Test
    fun testStorageImplementation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storageFile = Path(context.dataDir.path, "testdata.bin")
        val storage = AndroidStorageEngine.Builder(context, storageFile).build()
        storage.deleteAll()
        Assert.assertEquals(0, storage.enumerate().size.toLong())
        Assert.assertNull(storage["foo"])
        val data = byteArrayOf(1, 2, 3)
        storage.put("foo", data)
        Assert.assertArrayEquals(storage["foo"], data)
        Assert.assertEquals(1, storage.enumerate().size.toLong())
        Assert.assertEquals("foo", storage.enumerate().iterator().next())
        Assert.assertNull(storage["bar"])
        val data2 = byteArrayOf(4, 5, 6)
        storage.put("bar", data2)
        Assert.assertArrayEquals(storage["bar"], data2)
        Assert.assertEquals(2, storage.enumerate().size.toLong())
        storage.delete("foo")
        Assert.assertNull(storage["foo"])
        Assert.assertNotNull(storage["bar"])
        Assert.assertEquals(1, storage.enumerate().size.toLong())
        storage.delete("bar")
        Assert.assertNull(storage["bar"])
        Assert.assertEquals(0, storage.enumerate().size.toLong())
    }

    @Test
    fun testPersistence() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storageFile = Path(context.dataDir.path, "testdata.bin")
        val storage = AndroidStorageEngine.Builder(context, storageFile).build()
        storage.deleteAll()
        Assert.assertEquals(0, storage.enumerate().size.toLong())
        Assert.assertNull(storage["foo"])
        val data = byteArrayOf(1, 2, 3)
        storage.put("foo", data)
        Assert.assertArrayEquals(storage["foo"], data)

        // Create a new StorageEngine instance and check that data is still there...
        val storage2 = AndroidStorageEngine.Builder(context, storageFile).build()
        Assert.assertArrayEquals(storage2["foo"], data)
    }

    @Test
    fun testEncryption() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storageFile = Path(context.dataDir.path, "testdata.bin")
        val data = "xyz123".toByteArray(StandardCharsets.UTF_8)

        // First try with a storage engine using encryption... we should not be able
        // to find the data in what is saved to disk.
        val storage = AndroidStorageEngine.Builder(context, storageFile)
            .setUseEncryption(true)
            .build()
        storage.deleteAll()
        storage.put("foo", data)
        val fileContents = SystemFileSystem.source(storageFile).buffered().readByteArray()
        Assert.assertEquals(-1, (fileContents.toHex()).indexOf(data.toHex()).toLong())

        // Try again without encryption. The data should start at offset 20 which is
        // an implementation detail of how [GenericStorageEngine] stores the data.
        val storageWithoutEncryption = AndroidStorageEngine.Builder(context, storageFile)
            .setUseEncryption(false)
            .build()
        storageWithoutEncryption.deleteAll()
        storageWithoutEncryption.put("foo", data)
        val fileContentsWithoutEncryption = SystemFileSystem.source(storageFile).buffered().readByteArray()
        val idx = (fileContentsWithoutEncryption.toHex()).indexOf(data.toHex())
        Assert.assertEquals(20, idx)
    }

    @Test
    fun testEncryptionLargeValue() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storageFile = Path(context.dataDir.path, "testdata.bin")

        // Store 2 MiB of data...
        val data = ByteArray(2*1024*1024)
        for (n in data.indices) {
            val value = n * 3 + n + n * n
            data[n] = (value and 0xff).toByte()
        }
        val storage: StorageEngine = AndroidStorageEngine.Builder(context, storageFile)
            .setUseEncryption(true)
            .build()
        storage.deleteAll()
        storage.put("foo", data)
        val retrievedData = storage["foo"]
        Assert.assertArrayEquals(retrievedData, data)
    }
}
