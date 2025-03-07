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
package com.android.identity.android.legacy;

import org.junit.Assert
import org.junit.Test

class TimestampTest {
    @Test
    fun now() {
        val firstTime = Timestamp.now()
        Thread.sleep(5)
        val secondTime = Timestamp.now()
        Assert.assertTrue(
            firstTime.toEpochMilli().toString() + " < " + secondTime.toEpochMilli(),
            firstTime.toEpochMilli() < secondTime.toEpochMilli()
        )
    }

    @Test
    fun testEpochMilli() {
        Assert.assertEquals(42, Timestamp.ofEpochMilli(42).toEpochMilli())
        Assert.assertEquals(31415, Timestamp.ofEpochMilli(31415).toEpochMilli())
    }

    @Test
    fun testToString() {
        Assert.assertEquals(Timestamp.ofEpochMilli(0).toString(), "Timestamp{epochMillis=0}")
        Assert.assertEquals(Timestamp.ofEpochMilli(101).toString(), "Timestamp{epochMillis=101}")
    }

    @Test
    fun testEquals() {
        Assert.assertEquals(Timestamp.ofEpochMilli(1234), Timestamp.ofEpochMilli(1234))
        Assert.assertEquals(Timestamp.ofEpochMilli(8675309), Timestamp.ofEpochMilli(8675309))
        Assert.assertNotEquals(Timestamp.ofEpochMilli(1234), Timestamp.ofEpochMilli(8675309))
    }

    @Test
    fun testHashCode() {
        Assert.assertEquals(
            Timestamp.ofEpochMilli(0).hashCode().toLong(),
            Timestamp.ofEpochMilli(0).hashCode().toLong()
        )
        Assert.assertEquals(
            Timestamp.ofEpochMilli(1).hashCode().toLong(),
            Timestamp.ofEpochMilli(1).hashCode().toLong()
        )
        Assert.assertNotEquals(
            Timestamp.ofEpochMilli(0).hashCode().toLong(),
            Timestamp.ofEpochMilli(1).hashCode().toLong()
        )
    }
}