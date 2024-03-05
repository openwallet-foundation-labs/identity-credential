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

import android.util.AtomicFile
import androidx.test.InstrumentationRegistry
import com.android.identity.storage.StorageEngine
import com.android.identity.util.toHex
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Arrays

class AndroidStorageTest {
    @Test
    fun testStorageImplementation() {
        val context = InstrumentationRegistry.getTargetContext()
        val storageDir = File(context.dataDir, "ic-testing")
        val storage: StorageEngine = AndroidStorageEngine.Builder(context, storageDir).build()
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
        val context = InstrumentationRegistry.getTargetContext()
        val storageDir = File(context.dataDir, "ic-testing")
        var storage: StorageEngine = AndroidStorageEngine.Builder(context, storageDir).build()
        storage.deleteAll()
        Assert.assertEquals(0, storage.enumerate().size.toLong())
        Assert.assertNull(storage["foo"])
        val data = byteArrayOf(1, 2, 3)
        storage.put("foo", data)
        Assert.assertArrayEquals(storage["foo"], data)

        // Create a new StorageEngine instance and check that data is still there...
        storage = AndroidStorageEngine.Builder(context, storageDir).build()
        Assert.assertArrayEquals(storage["foo"], data)
    }

    @Test
    fun testEncryption() {
        val context = InstrumentationRegistry.getTargetContext()
        val storageDir = File(context.dataDir, "ic-testing")
        val data = "xyz123".toByteArray(StandardCharsets.UTF_8)

        // First try with a storage engine using encryption... we should not be able
        // to find the data in what is saved to disk.
        var storage: StorageEngine = AndroidStorageEngine.Builder(context, storageDir)
            .setUseEncryption(true)
            .build()
        storage.deleteAll()
        storage.put("foo", data)
        var targetFile = File(storageDir, PREFIX + URLEncoder.encode("foo", "UTF-8"))
        var fileContents = AtomicFile(targetFile).readFully()
        Assert.assertEquals(-1, (fileContents.toHex).indexOf(data.toHex).toLong())

        // Try again without encryption. The data should start at offset 4.
        storage = AndroidStorageEngine.Builder(context, storageDir)
            .setUseEncryption(false)
            .build()
        storage.deleteAll()
        storage.put("foo", data)
        targetFile = File(storageDir, PREFIX + URLEncoder.encode("foo", "UTF-8"))
        fileContents = AtomicFile(targetFile).readFully()
        Assert.assertArrayEquals(data, Arrays.copyOfRange(fileContents, 4, fileContents.size))
    }

    @Test
    fun testEncryptionLargeValue() {
        val context = InstrumentationRegistry.getTargetContext()
        val storageDir = File(context.dataDir, "ic-testing")

        // Store 2 MiB of data...
        val data = ByteArray(2 * 1024 * 1024)
        for (n in data.indices) {
            val value = n * 3 + n + n * n
            data[n] = (value and 0xff).toByte()
        }
        val storage: StorageEngine = AndroidStorageEngine.Builder(context, storageDir)
            .setUseEncryption(true)
            .build()
        storage.deleteAll()
        storage.put("foo", data)
        val retrievedData = storage["foo"]
        Assert.assertArrayEquals(retrievedData, data)
    }

    companion object {
        private const val PREFIX = "IC_AndroidStorageEngine_"
    }
}
