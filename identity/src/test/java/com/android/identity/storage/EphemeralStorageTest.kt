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
package com.android.identity.storage

import org.junit.Assert
import org.junit.Test

class EphemeralStorageTest {
    @Test
    fun testStorageImplementation() {
        val storage = EphemeralStorageEngine()
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
        var storage: StorageEngine = EphemeralStorageEngine()
        Assert.assertEquals(0, storage.enumerate().size.toLong())
        Assert.assertNull(storage["foo"])
        val data = byteArrayOf(1, 2, 3)
        storage.put("foo", data)
        Assert.assertArrayEquals(storage["foo"], data)

        // Create a new StorageEngine instance and check that data is no longer there...
        storage = EphemeralStorageEngine()
        Assert.assertEquals(0, storage.enumerate().size.toLong())
        Assert.assertNull(storage["foo"])
    }
}
