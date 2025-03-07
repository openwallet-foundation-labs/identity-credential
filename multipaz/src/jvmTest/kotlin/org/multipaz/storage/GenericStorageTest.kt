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
package org.multipaz.storage

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GenericStorageTest {
    @Test
    fun testStorageImplementation() {
        SystemFileSystem.delete(Path("/tmp-ic-test"), false)
        val storageFile = Path("/tmp/ic-test")
        val storage: StorageEngine = GenericStorageEngine(storageFile)
        storage.deleteAll()
        assertEquals(0, storage.enumerate().size.toLong())
        assertNull(storage["foo"])
        val data = byteArrayOf(1, 2, 3)
        storage.put("foo", data)
        assertContentEquals(storage["foo"], data)
        assertEquals(1, storage.enumerate().size.toLong())
        assertEquals("foo", storage.enumerate().iterator().next())
        assertNull(storage["bar"])
        val data2 = byteArrayOf(4, 5, 6)
        storage.put("bar", data2)
        assertContentEquals(storage["bar"], data2)
        assertEquals(2, storage.enumerate().size.toLong())
        storage.delete("foo")
        assertNull(storage["foo"])
        assertNotNull(storage["bar"])
        assertEquals(1, storage.enumerate().size.toLong())
        storage.delete("bar")
        assertNull(storage["bar"])
        assertEquals(0, storage.enumerate().size.toLong())
    }

    @Test
    fun testPersistence() {
        SystemFileSystem.delete(Path("/tmp-ic-test"), false)
        val storageFile = Path("/tmp/ic-test")
        var storage: StorageEngine = GenericStorageEngine(storageFile)
        storage.deleteAll()
        assertEquals(0, storage.enumerate().size.toLong())
        assertNull(storage["foo"])
        val data = byteArrayOf(1, 2, 3)
        storage.put("foo", data)
        assertContentEquals(storage["foo"], data)

        // Create a new StorageEngine instance and check that data is still there...
        val storage2 = GenericStorageEngine(storageFile)
        assertContentEquals(data, storage2["foo"])
    }
}
