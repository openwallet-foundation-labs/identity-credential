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

package com.android.identity.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import com.android.identity.credential.NameSpacedData;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SimpleApplicationDataTest {

    private void testEncodingConsistency(SimpleApplicationData original,
                                          SimpleApplicationData other) {
        assertArrayEquals(original.encodeAsCbor(), other.encodeAsCbor());
    }

    @Test
    public void testOverrides() {
        SimpleApplicationData appData = new SimpleApplicationData(null);
        assertFalse(appData.keyExists("testkey"));
        assertThrows(IllegalArgumentException.class, () -> appData.getData("testkey"));
        assertThrows(IllegalArgumentException.class, () -> appData.getString("testkey"));
        assertThrows(IllegalArgumentException.class, () -> appData.getNumber("testkey"));
        assertThrows(IllegalArgumentException.class, () -> appData.getBoolean("testkey"));
        assertThrows(IllegalArgumentException.class, () -> appData.getNameSpacedData("testkey"));

        appData.setData("foo", new byte[] {0x50, 0x51, 0x52});
        assertArrayEquals(new byte[] {0x50, 0x51, 0x52}, appData.getData("foo"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        // do the same thing and assert nothing changes
        appData.setData("foo", new byte[] {0x50, 0x51, 0x52});
        assertArrayEquals(new byte[] {0x50, 0x51, 0x52}, appData.getData("foo"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        appData.setString("foo", "testString");
        assertEquals("testString", appData.getString("foo"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        appData.setNumber("foo", 792L);
        assertEquals(792L, (long) appData.getNumber("foo"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        appData.setBoolean("foo", false);
        assertEquals(false, appData.getBoolean("foo"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        appData.setNameSpacedData("foo", new NameSpacedData.Builder()
                .putEntryString("foo", "bar", "baz")
                .build());
        assertEquals("baz", appData.getNameSpacedData("foo")
                .getDataElementString("foo", "bar"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        // remove by nulling
        appData.setData("foo", (byte[]) null);
        assertFalse(appData.keyExists("testkey"));
    }

    @Test
    public void testListenerNotCalledDuringConstruction() {
        SimpleApplicationData appData = new SimpleApplicationData(null);
        appData.setString("foo", "bar");
        assertEquals("bar", appData.getString("foo"));
        final int[] numOnDataSetCalled = {0};
        testEncodingConsistency(appData,
                SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(),
                        () -> numOnDataSetCalled[0] += 1));
        assertEquals(0, numOnDataSetCalled[0]);
    }

    @Test
    public void testByteArrays() {
        SimpleApplicationData appData = new SimpleApplicationData(null);

        appData.setData("foo", new byte[] {0x50, 0x51, 0x52});
        assertArrayEquals(new byte[] {0x50, 0x51, 0x52}, appData.getData("foo"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        appData.setData("bar", new byte[] {0x53, 0x54, 0x55});
        assertArrayEquals(new byte[] {0x50, 0x51, 0x52}, appData.getData("foo"));
        assertArrayEquals(new byte[] {0x53, 0x54, 0x55}, appData.getData("bar"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        // remove by nulling
        appData.setData("bar", (byte[]) null);
        assertArrayEquals(new byte[] {0x50, 0x51, 0x52}, appData.getData("foo"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));
        assertFalse(appData.keyExists("bar"));
        assertThrows(IllegalArgumentException.class, () -> appData.getData("bar"));
    }

    @Test
    public void testStringValues() {
        SimpleApplicationData appData = new SimpleApplicationData(null);

        appData.setString("foo", "abc");
        assertEquals("abc", appData.getString("foo"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        appData.setString("bar", "foo");
        assertEquals("abc", appData.getString("foo"));
        assertEquals("foo", appData.getString("bar"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        // remove by nulling
        appData.setData("bar", (byte[]) null);
        assertEquals("abc", appData.getString("foo"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));
        assertFalse(appData.keyExists("bar"));
        assertThrows(IllegalArgumentException.class, () -> appData.getString("bar"));

        // empty string
        appData.setString("bar", "");
        assertEquals("abc", appData.getString("foo"));
        assertEquals("", appData.getString("bar"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        // non-String values being read as a String
        appData.setNumber("bar", 0L);
        assertEquals("abc", appData.getString("foo"));
        assertThrows(IllegalArgumentException.class, () -> appData.getString("bar"));

        appData.setData("bar", new byte[] {0x53, 0x54, 0x55});
        assertEquals("abc", appData.getString("foo"));
        assertThrows(IllegalArgumentException.class, () -> appData.getString("bar"));

        appData.setData("bar", new byte[0]);
        assertEquals("abc", appData.getString("foo"));
        assertEquals(0, appData.getData("bar").length);
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        appData.setBoolean("bar", true);
        assertThrows(IllegalArgumentException.class, () -> appData.getString("bar"));
    }

    @Test
    public void testNumberValues() {
        SimpleApplicationData appData = new SimpleApplicationData(null);

        appData.setNumber("foo", 83L);
        assertEquals(83L, (long) appData.getNumber("foo"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        appData.setNumber("bar", 0L);
        assertEquals(83L, (long) appData.getNumber("foo"));
        assertEquals(0L, (long) appData.getNumber("bar"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        // remove by nulling
        appData.setData("bar", (byte[]) null);
        assertEquals(83L, (long) appData.getNumber("foo"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));
        assertFalse(appData.keyExists("bar"));
        assertThrows(IllegalArgumentException.class, () -> appData.getString("bar"));

        // non-Number values being read as a Number
        appData.setData("bar", new byte[] {0x53, 0x54, 0x55});
        assertEquals(83L, (long) appData.getNumber("foo"));
        assertThrows(IllegalArgumentException.class, () -> appData.getNumber("bar"));

        appData.setData("bar", new byte[0]);
        assertEquals(83L, (long) appData.getNumber("foo"));
        assertThrows(IllegalArgumentException.class, () -> appData.getNumber("bar"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        appData.setString("bar", "abc");
        assertEquals(83L, (long) appData.getNumber("foo"));
        assertThrows(IllegalArgumentException.class, () -> appData.getNumber("bar"));

        appData.setBoolean("bar", true);
        assertEquals(83L, (long) appData.getNumber("foo"));
        assertThrows(IllegalArgumentException.class, () -> appData.getNumber("bar"));
    }

    @Test
    public void checkedLongValueEdgeCases() {
        SimpleApplicationData appData = new SimpleApplicationData(null);

        appData.setNumber("foo", Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, (long) appData.getNumber("foo"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        appData.setNumber("bar", Long.MIN_VALUE);
        assertEquals(Long.MAX_VALUE, (long) appData.getNumber("foo"));
        assertEquals(Long.MIN_VALUE, (long) appData.getNumber("bar"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        // remove by nulling
        appData.setData("bar", (byte[]) null);
        assertEquals(Long.MAX_VALUE, (long) appData.getNumber("foo"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));
        assertFalse(appData.keyExists("bar"));
        assertThrows(IllegalArgumentException.class, () -> appData.getString("bar"));
    }

    @Test
    public void testBooleanValues() {
        SimpleApplicationData appData = new SimpleApplicationData(null);

        appData.setBoolean("foo", true);
        assertEquals(true, appData.getBoolean("foo"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        appData.setBoolean("bar", false);
        assertEquals(true, appData.getBoolean("foo"));
        assertEquals(false, appData.getBoolean("bar"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        // remove by nulling
        appData.setData("bar", (byte[]) null);
        assertEquals(true, appData.getBoolean("foo"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));
        assertFalse(appData.keyExists("bar"));
        assertThrows(IllegalArgumentException.class, () -> appData.getString("bar"));

        // non-boolean values being read as a boolean
        appData.setData("bar", new byte[] {0x53, 0x54, 0x55});
        assertEquals(true, appData.getBoolean("foo"));
        assertThrows(IllegalArgumentException.class, () -> appData.getBoolean("bar"));

        appData.setData("bar", new byte[0]);
        assertEquals(true, appData.getBoolean("foo"));
        assertThrows(IllegalArgumentException.class, () -> appData.getBoolean("bar"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        appData.setString("bar", "abc");
        assertEquals(true, appData.getBoolean("foo"));
        assertThrows(IllegalArgumentException.class, () -> appData.getBoolean("bar"));

        appData.setNumber("bar", 82L);
        assertEquals(true, appData.getBoolean("foo"));
        assertThrows(IllegalArgumentException.class, () -> appData.getBoolean("bar"));
    }

    @Test
    public void testMixedValues() {
        SimpleApplicationData appData = new SimpleApplicationData(null);

        appData.setData("foo", new byte[] {0x50, 0x51, 0x52});
        assertArrayEquals(new byte[] {0x50, 0x51, 0x52}, appData.getData("foo"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        appData.setString("bar", "abc");
        assertArrayEquals(new byte[] {0x50, 0x51, 0x52}, appData.getData("foo"));
        assertEquals("abc", appData.getString("bar"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        appData.setNumber("c", 601L);
        assertArrayEquals(new byte[] {0x50, 0x51, 0x52}, appData.getData("foo"));
        assertEquals("abc", appData.getString("bar"));
        assertEquals(601L, (long) appData.getNumber("c"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        appData.setBoolean("a", false);
        assertArrayEquals(new byte[] {0x50, 0x51, 0x52}, appData.getData("foo"));
        assertEquals("abc", appData.getString("bar"));
        assertEquals(601L, (long) appData.getNumber("c"));
        assertEquals(false, appData.getBoolean("a"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));

        // override the "foo" key with a String
        appData.setString("foo", "bar");
        assertEquals("bar", appData.getString("foo"));
        assertEquals("abc", appData.getString("bar"));
        assertEquals(601L, (long) appData.getNumber("c"));
        assertEquals(false, appData.getBoolean("a"));
        testEncodingConsistency(appData, SimpleApplicationData.decodeFromCbor(appData.encodeAsCbor(), null));
    }
}
