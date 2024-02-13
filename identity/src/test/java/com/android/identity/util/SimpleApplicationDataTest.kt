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

import com.android.identity.credential.NameSpacedData
import com.android.identity.util.SimpleApplicationData.Companion.decodeFromCbor
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SimpleApplicationDataTest {
    private fun testEncodingConsistency(
        original: SimpleApplicationData,
        other: SimpleApplicationData
    ) {
        Assert.assertArrayEquals(original.encodeAsCbor(), other.encodeAsCbor())
    }

    @Test
    fun testOverrides() {
        val appData = SimpleApplicationData {}
        Assert.assertFalse(appData.keyExists("testkey"))
        Assert.assertThrows(IllegalArgumentException::class.java) { appData.getData("testkey") }
        Assert.assertThrows(IllegalArgumentException::class.java) { appData.getString("testkey") }
        Assert.assertThrows(IllegalArgumentException::class.java) { appData.getNumber("testkey") }
        Assert.assertThrows(IllegalArgumentException::class.java) { appData.getBoolean("testkey") }
        Assert.assertThrows(IllegalArgumentException::class.java) { appData.getNameSpacedData("testkey") }
        appData.setData("foo", byteArrayOf(0x50, 0x51, 0x52))
        Assert.assertArrayEquals(byteArrayOf(0x50, 0x51, 0x52), appData.getData("foo"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))

        // do the same thing and assert nothing changes
        appData.setData("foo", byteArrayOf(0x50, 0x51, 0x52))
        Assert.assertArrayEquals(byteArrayOf(0x50, 0x51, 0x52), appData.getData("foo"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setString("foo", "testString")
        Assert.assertEquals("testString", appData.getString("foo"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setNumber("foo", 792L)
        Assert.assertEquals(792L, appData.getNumber("foo"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setBoolean("foo", false)
        Assert.assertEquals(false, appData.getBoolean("foo"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setNameSpacedData(
            "foo", NameSpacedData.Builder()
                .putEntryString("foo", "bar", "baz")
                .build()
        )
        Assert.assertEquals(
            "baz", appData.getNameSpacedData("foo")
                .getDataElementString("foo", "bar")
        )
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))

        // remove by nulling
        appData.setData("foo", null as ByteArray?)
        Assert.assertFalse(appData.keyExists("testkey"))
    }

    @Test
    fun testListenerNotCalledDuringConstruction() {
        val appData = SimpleApplicationData({})
        appData.setString("foo", "bar")
        Assert.assertEquals("bar", appData.getString("foo"))
        val numOnDataSetCalled = intArrayOf(0)
        testEncodingConsistency(appData,
            decodeFromCbor(
                appData.encodeAsCbor()
            ) { key: String? -> numOnDataSetCalled[0] += 1 }
        )
        Assert.assertEquals(0, numOnDataSetCalled[0].toLong())
    }

    @Test
    fun testByteArrays() {
        val appData = SimpleApplicationData({})
        appData.setData("foo", byteArrayOf(0x50, 0x51, 0x52))
        Assert.assertArrayEquals(byteArrayOf(0x50, 0x51, 0x52), appData.getData("foo"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setData("bar", byteArrayOf(0x53, 0x54, 0x55))
        Assert.assertArrayEquals(byteArrayOf(0x50, 0x51, 0x52), appData.getData("foo"))
        Assert.assertArrayEquals(byteArrayOf(0x53, 0x54, 0x55), appData.getData("bar"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))

        // remove by nulling
        appData.setData("bar", null as ByteArray?)
        Assert.assertArrayEquals(byteArrayOf(0x50, 0x51, 0x52), appData.getData("foo"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))
        Assert.assertFalse(appData.keyExists("bar"))
        Assert.assertThrows(IllegalArgumentException::class.java) { appData.getData("bar") }
    }

    @Test
    fun testStringValues() {
        val appData = SimpleApplicationData({})
        appData.setString("foo", "abc")
        Assert.assertEquals("abc", appData.getString("foo"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setString("bar", "foo")
        Assert.assertEquals("abc", appData.getString("foo"))
        Assert.assertEquals("foo", appData.getString("bar"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))

        // remove by nulling
        appData.setData("bar", null as ByteArray?)
        Assert.assertEquals("abc", appData.getString("foo"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))
        Assert.assertFalse(appData.keyExists("bar"))
        Assert.assertThrows(IllegalArgumentException::class.java) { appData.getString("bar") }

        // empty string
        appData.setString("bar", "")
        Assert.assertEquals("abc", appData.getString("foo"))
        Assert.assertEquals("", appData.getString("bar"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))

        // non-String values being read as a String
        appData.setNumber("bar", 0L)
        Assert.assertEquals("abc", appData.getString("foo"))
        Assert.assertThrows(IllegalArgumentException::class.java) { appData.getString("bar") }
        appData.setData("bar", byteArrayOf(0x53, 0x54, 0x55))
        Assert.assertEquals("abc", appData.getString("foo"))
        Assert.assertThrows(IllegalArgumentException::class.java) { appData.getString("bar") }
        appData.setData("bar", ByteArray(0))
        Assert.assertEquals("abc", appData.getString("foo"))
        Assert.assertEquals(0, appData.getData("bar").size.toLong())
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setBoolean("bar", true)
        Assert.assertThrows(IllegalArgumentException::class.java) { appData.getString("bar") }
    }

    @Test
    fun testNumberValues() {
        val appData = SimpleApplicationData({})
        appData.setNumber("foo", 83L)
        Assert.assertEquals(83L, appData.getNumber("foo"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setNumber("bar", 0L)
        Assert.assertEquals(83L, appData.getNumber("foo"))
        Assert.assertEquals(0L, appData.getNumber("bar"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))

        // remove by nulling
        appData.setData("bar", null)
        Assert.assertEquals(83L, appData.getNumber("foo"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))
        Assert.assertFalse(appData.keyExists("bar"))
        Assert.assertThrows(IllegalArgumentException::class.java) { appData.getString("bar") }

        // non-Number values being read as a Number
        appData.setData("bar", byteArrayOf(0x53, 0x54, 0x55))
        Assert.assertEquals(83L, appData.getNumber("foo"))
        Assert.assertThrows(IllegalArgumentException::class.java) { appData.getNumber("bar") }
        appData.setData("bar", ByteArray(0))
        Assert.assertEquals(83L, appData.getNumber("foo"))
        Assert.assertThrows(IllegalArgumentException::class.java) { appData.getNumber("bar") }
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setString("bar", "abc")
        Assert.assertEquals(83L, appData.getNumber("foo"))
        Assert.assertThrows(IllegalArgumentException::class.java) { appData.getNumber("bar") }
        appData.setBoolean("bar", true)
        Assert.assertEquals(83L, appData.getNumber("foo"))
        Assert.assertThrows(IllegalArgumentException::class.java) { appData.getNumber("bar") }
    }

    @Test
    fun checkedLongValueEdgeCases() {
        val appData = SimpleApplicationData({})
        appData.setNumber("foo", Long.MAX_VALUE)
        Assert.assertEquals(Long.MAX_VALUE, appData.getNumber("foo"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setNumber("bar", Long.MIN_VALUE)
        Assert.assertEquals(Long.MAX_VALUE, appData.getNumber("foo"))
        Assert.assertEquals(Long.MIN_VALUE, appData.getNumber("bar"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))

        // remove by nulling
        appData.setData("bar", null)
        Assert.assertEquals(Long.MAX_VALUE, appData.getNumber("foo"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))
        Assert.assertFalse(appData.keyExists("bar"))
        Assert.assertThrows(IllegalArgumentException::class.java) { appData.getString("bar") }
    }

    @Test
    fun testBooleanValues() {
        val appData = SimpleApplicationData({})
        appData.setBoolean("foo", true)
        Assert.assertEquals(true, appData.getBoolean("foo"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setBoolean("bar", false)
        Assert.assertEquals(true, appData.getBoolean("foo"))
        Assert.assertEquals(false, appData.getBoolean("bar"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))

        // remove by nulling
        appData.setData("bar", null)
        Assert.assertEquals(true, appData.getBoolean("foo"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))
        Assert.assertFalse(appData.keyExists("bar"))
        Assert.assertThrows(IllegalArgumentException::class.java) { appData.getString("bar") }

        // non-boolean values being read as a boolean
        appData.setData("bar", byteArrayOf(0x53, 0x54, 0x55))
        Assert.assertEquals(true, appData.getBoolean("foo"))
        Assert.assertThrows(IllegalArgumentException::class.java) { appData.getBoolean("bar") }
        appData.setData("bar", ByteArray(0))
        Assert.assertEquals(true, appData.getBoolean("foo"))
        Assert.assertThrows(IllegalArgumentException::class.java) { appData.getBoolean("bar") }
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setString("bar", "abc")
        Assert.assertEquals(true, appData.getBoolean("foo"))
        Assert.assertThrows(IllegalArgumentException::class.java) { appData.getBoolean("bar") }
        appData.setNumber("bar", 82L)
        Assert.assertEquals(true, appData.getBoolean("foo"))
        Assert.assertThrows(IllegalArgumentException::class.java) { appData.getBoolean("bar") }
    }

    @Test
    fun testMixedValues() {
        val appData = SimpleApplicationData({})
        appData.setData("foo", byteArrayOf(0x50, 0x51, 0x52))
        Assert.assertArrayEquals(byteArrayOf(0x50, 0x51, 0x52), appData.getData("foo"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setString("bar", "abc")
        Assert.assertArrayEquals(byteArrayOf(0x50, 0x51, 0x52), appData.getData("foo"))
        Assert.assertEquals("abc", appData.getString("bar"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setNumber("c", 601L)
        Assert.assertArrayEquals(byteArrayOf(0x50, 0x51, 0x52), appData.getData("foo"))
        Assert.assertEquals("abc", appData.getString("bar"))
        Assert.assertEquals(601L, appData.getNumber("c"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))
        appData.setBoolean("a", false)
        Assert.assertArrayEquals(byteArrayOf(0x50, 0x51, 0x52), appData.getData("foo"))
        Assert.assertEquals("abc", appData.getString("bar"))
        Assert.assertEquals(601L, appData.getNumber("c"))
        Assert.assertEquals(false, appData.getBoolean("a"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))

        // override the "foo" key with a String
        appData.setString("foo", "bar")
        Assert.assertEquals("bar", appData.getString("foo"))
        Assert.assertEquals("abc", appData.getString("bar"))
        Assert.assertEquals(601L, appData.getNumber("c"))
        Assert.assertEquals(false, appData.getBoolean("a"))
        testEncodingConsistency(appData, decodeFromCbor(appData.encodeAsCbor(), {}))
    }
}