/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.identity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TimestampTest {

    @Test
    public void now() throws InterruptedException {
        final Timestamp firstTime = Timestamp.now();
        Thread.sleep(5);
        final Timestamp secondTime = Timestamp.now();
        assertTrue(firstTime.toEpochMilli() + " < " + secondTime.toEpochMilli(),
            firstTime.toEpochMilli() < secondTime.toEpochMilli());
    }

    @Test
    public void testEpochMilli() {
        assertEquals(42, Timestamp.ofEpochMilli(42).toEpochMilli());
        assertEquals(31415, Timestamp.ofEpochMilli(31415).toEpochMilli());
    }

    @Test
    public void testToString() {
      assertEquals(Timestamp.ofEpochMilli(0).toString(), "Timestamp{epochMillis=0}");
      assertEquals(Timestamp.ofEpochMilli(101).toString(), "Timestamp{epochMillis=101}");
    }

    @Test
    public void testEquals() {
      assertEquals(Timestamp.ofEpochMilli(1234), Timestamp.ofEpochMilli(1234));
      assertEquals(Timestamp.ofEpochMilli(8675309), Timestamp.ofEpochMilli(8675309));
      assertNotEquals(Timestamp.ofEpochMilli(1234), Timestamp.ofEpochMilli(8675309));
    }

    @Test
    public void testHashCode() {
      assertEquals(Timestamp.ofEpochMilli(0).hashCode(), Timestamp.ofEpochMilli(0).hashCode());
      assertEquals(Timestamp.ofEpochMilli(1).hashCode(), Timestamp.ofEpochMilli(1).hashCode());
      assertNotEquals(Timestamp.ofEpochMilli(0).hashCode(), Timestamp.ofEpochMilli(1).hashCode());
    }
}