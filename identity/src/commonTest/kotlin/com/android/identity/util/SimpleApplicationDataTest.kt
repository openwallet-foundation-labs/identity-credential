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
package com.android.identity.util

import com.android.identity.document.NameSpacedData
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SimpleApplicationDataTest {
    private fun testEncodingConsistency(
        original: SimpleApplicationData,
        other: SimpleApplicationData
    ) {
        assertContentEquals(original.encodeAsCbor(), other.encodeAsCbor())
    }

    @Test
    fun testOverrides() {
        val appData = SimpleApplicationData {}
        assertFalse(appData.keyExists("testkey"))
        assertFailsWith(IllegalArgumentException::class) { appData.getData("testkey") }
        assertFailsWith(IllegalArgumentException::class) { appData.getString("testkey") }
        assertFailsWith(IllegalArgumentException::class) { appData.getNumber("testkey") }
        assertFailsWith(IllegalArgumentException::class) { appData.getBoolean("testkey") }
        assertFailsWith(IllegalArgumentException::class) { appData.getNameSpacedData("testkey") }
        appData.setData("foo", byteArrayOf(0x50, 0x51, 0x52))
        assertContentEquals(byteArrayOf(0x50, 0x51, 0x52), appData.getData("foo"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))

        // do the same thing and assert nothing changes
        appData.setData("foo", byteArrayOf(0x50, 0x51, 0x52))
        assertContentEquals(byteArrayOf(0x50, 0x51, 0x52), appData.getData("foo"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setString("foo", "testString")
        assertEquals("testString", appData.getString("foo"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setNumber("foo", 792L)
        assertEquals(792L, appData.getNumber("foo"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setBoolean("foo", false)
        assertEquals(false, appData.getBoolean("foo"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setNameSpacedData(
            "foo", NameSpacedData.Builder()
                .putEntryString("foo", "bar", "baz")
                .build()
        )
        assertEquals(
            "baz", appData.getNameSpacedData("foo")
                .getDataElementString("foo", "bar")
        )
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))

        // remove by nulling
        appData.setData("foo", null as ByteArray?)
        assertFalse(appData.keyExists("testkey"))
    }

    @Test
    fun testListenerNotCalledDuringConstruction() {
        val appData = SimpleApplicationData({})
        appData.setString("foo", "bar")
        assertEquals("bar", appData.getString("foo"))
        val numOnDataSetCalled = intArrayOf(0)
        testEncodingConsistency(appData,
            SimpleApplicationData.decodeFromCbor(
                appData.encodeAsCbor()
            ) { key: String? -> numOnDataSetCalled[0] += 1 }
        )
        assertEquals(0, numOnDataSetCalled[0].toLong())
    }

    @Test
    fun testByteArrays() {
        val appData = SimpleApplicationData({})
        appData.setData("foo", byteArrayOf(0x50, 0x51, 0x52))
        assertContentEquals(byteArrayOf(0x50, 0x51, 0x52), appData.getData("foo"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setData("bar", byteArrayOf(0x53, 0x54, 0x55))
        assertContentEquals(byteArrayOf(0x50, 0x51, 0x52), appData.getData("foo"))
        assertContentEquals(byteArrayOf(0x53, 0x54, 0x55), appData.getData("bar"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))

        // remove by nulling
        appData.setData("bar", null as ByteArray?)
        assertContentEquals(byteArrayOf(0x50, 0x51, 0x52), appData.getData("foo"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))
        assertFalse(appData.keyExists("bar"))
        assertFailsWith(IllegalArgumentException::class) { appData.getData("bar") }
    }

    @Test
    fun testStringValues() {
        val appData = SimpleApplicationData({})
        appData.setString("foo", "abc")
        assertEquals("abc", appData.getString("foo"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setString("bar", "foo")
        assertEquals("abc", appData.getString("foo"))
        assertEquals("foo", appData.getString("bar"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))

        // remove by nulling
        appData.setData("bar", null as ByteArray?)
        assertEquals("abc", appData.getString("foo"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))
        assertFalse(appData.keyExists("bar"))
        assertFailsWith(IllegalArgumentException::class) { appData.getString("bar") }

        // empty string
        appData.setString("bar", "")
        assertEquals("abc", appData.getString("foo"))
        assertEquals("", appData.getString("bar"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))

        // non-String values being read as a String
        appData.setNumber("bar", 0L)
        assertEquals("abc", appData.getString("foo"))
        assertFailsWith(IllegalArgumentException::class) { appData.getString("bar") }
        appData.setData("bar", byteArrayOf(0x53, 0x54, 0x55))
        assertEquals("abc", appData.getString("foo"))
        assertFailsWith(IllegalArgumentException::class) { appData.getString("bar") }
        appData.setData("bar", ByteArray(0))
        assertEquals("abc", appData.getString("foo"))
        assertEquals(0, appData.getData("bar").size.toLong())
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setBoolean("bar", true)
        assertFailsWith(IllegalArgumentException::class) { appData.getString("bar") }
    }

    @Test
    fun testNumberValues() {
        val appData = SimpleApplicationData({})
        appData.setNumber("foo", 83L)
        assertEquals(83L, appData.getNumber("foo"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setNumber("bar", 0L)
        assertEquals(83L, appData.getNumber("foo"))
        assertEquals(0L, appData.getNumber("bar"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))

        // remove by nulling
        appData.setData("bar", null)
        assertEquals(83L, appData.getNumber("foo"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))
        assertFalse(appData.keyExists("bar"))
        assertFailsWith(IllegalArgumentException::class) { appData.getString("bar") }

        // non-Number values being read as a Number
        appData.setData("bar", byteArrayOf(0x53, 0x54, 0x55))
        assertEquals(83L, appData.getNumber("foo"))
        assertFailsWith(IllegalArgumentException::class) { appData.getNumber("bar") }
        appData.setData("bar", ByteArray(0))
        assertEquals(83L, appData.getNumber("foo"))
        assertFailsWith(IllegalArgumentException::class) { appData.getNumber("bar") }
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setString("bar", "abc")
        assertEquals(83L, appData.getNumber("foo"))
        assertFailsWith(IllegalArgumentException::class) { appData.getNumber("bar") }
        appData.setBoolean("bar", true)
        assertEquals(83L, appData.getNumber("foo"))
        assertFailsWith(IllegalArgumentException::class) { appData.getNumber("bar") }
    }

    @Test
    fun checkedLongValueEdgeCases() {
        val appData = SimpleApplicationData({})
        appData.setNumber("foo", Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, appData.getNumber("foo"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setNumber("bar", Long.MIN_VALUE)
        assertEquals(Long.MAX_VALUE, appData.getNumber("foo"))
        assertEquals(Long.MIN_VALUE, appData.getNumber("bar"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))

        // remove by nulling
        appData.setData("bar", null)
        assertEquals(Long.MAX_VALUE, appData.getNumber("foo"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))
        assertFalse(appData.keyExists("bar"))
        assertFailsWith(IllegalArgumentException::class) { appData.getString("bar") }
    }

    @Test
    fun testBooleanValues() {
        val appData = SimpleApplicationData({})
        appData.setBoolean("foo", true)
        assertEquals(true, appData.getBoolean("foo"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setBoolean("bar", false)
        assertEquals(true, appData.getBoolean("foo"))
        assertEquals(false, appData.getBoolean("bar"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))

        // remove by nulling
        appData.setData("bar", null)
        assertEquals(true, appData.getBoolean("foo"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))
        assertFalse(appData.keyExists("bar"))
        assertFailsWith(IllegalArgumentException::class) { appData.getString("bar") }

        // non-boolean values being read as a boolean
        appData.setData("bar", byteArrayOf(0x53, 0x54, 0x55))
        assertEquals(true, appData.getBoolean("foo"))
        assertFailsWith(IllegalArgumentException::class) { appData.getBoolean("bar") }
        appData.setData("bar", ByteArray(0))
        assertEquals(true, appData.getBoolean("foo"))
        assertFailsWith(IllegalArgumentException::class) { appData.getBoolean("bar") }
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setString("bar", "abc")
        assertEquals(true, appData.getBoolean("foo"))
        assertFailsWith(IllegalArgumentException::class) { appData.getBoolean("bar") }
        appData.setNumber("bar", 82L)
        assertEquals(true, appData.getBoolean("foo"))
        assertFailsWith(IllegalArgumentException::class) { appData.getBoolean("bar") }
    }

    @Test
    fun testMixedValues() {
        val appData = SimpleApplicationData({})
        appData.setData("foo", byteArrayOf(0x50, 0x51, 0x52))
        assertContentEquals(byteArrayOf(0x50, 0x51, 0x52), appData.getData("foo"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setString("bar", "abc")
        assertContentEquals(byteArrayOf(0x50, 0x51, 0x52), appData.getData("foo"))
        assertEquals("abc", appData.getString("bar"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setNumber("c", 601L)
        assertContentEquals(byteArrayOf(0x50, 0x51, 0x52), appData.getData("foo"))
        assertEquals("abc", appData.getString("bar"))
        assertEquals(601L, appData.getNumber("c"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setBoolean("a", false)
        assertContentEquals(byteArrayOf(0x50, 0x51, 0x52), appData.getData("foo"))
        assertEquals("abc", appData.getString("bar"))
        assertEquals(601L, appData.getNumber("c"))
        assertEquals(false, appData.getBoolean("a"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))

        // override the "foo" key with a String
        appData.setString("foo", "bar")
        assertEquals("bar", appData.getString("foo"))
        assertEquals("abc", appData.getString("bar"))
        assertEquals(601L, appData.getNumber("c"))
        assertEquals(false, appData.getBoolean("a"))
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), {}))
    }
}