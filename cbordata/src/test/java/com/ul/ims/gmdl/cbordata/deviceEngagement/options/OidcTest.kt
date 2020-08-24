/*
 * Copyright (C) 2019 Google LLC
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

package com.ul.ims.gmdl.cbordata.deviceEngagement.options

import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.UnicodeString
import co.nstant.`in`.cbor.model.UnsignedInteger
import org.junit.Assert
import org.junit.Test

class OidcTest {

    companion object {
        const val EXPECTED_VALUE_1 = 1
        const val EXPECTED_VALUE_2 = "my-value-2"
        const val EXPECTED_VALUE_3 = "my-value"
    }

    private val expectedEncoded = byteArrayOf(
        0x83.toByte(), 0x01.toByte(), 0x6a.toByte(), 0x6d.toByte(), 0x79.toByte(),
        0x2d.toByte(), 0x76.toByte(), 0x61.toByte(), 0x6c.toByte(), 0x75.toByte(),
        0x65.toByte(), 0x2d.toByte(), 0x32.toByte(), 0x68.toByte(), 0x6d.toByte(),
        0x79.toByte(), 0x2d.toByte(), 0x76.toByte(), 0x61.toByte(), 0x6c.toByte(),
        0x75.toByte(), 0x65.toByte()
    )

    @Test
    fun testBuilder() {
        val builder = Oidc.Builder()
        val oidc = builder
            .setVersion(EXPECTED_VALUE_1)
            .setUrl(EXPECTED_VALUE_2)
            .setToken(EXPECTED_VALUE_3)
            .build()

        Assert.assertNotNull(oidc)
        Assert.assertEquals(EXPECTED_VALUE_1, oidc.version)
        Assert.assertEquals(EXPECTED_VALUE_2, oidc.url)
        Assert.assertEquals(EXPECTED_VALUE_3, oidc.token)
    }

    @Test
    fun testEncode() {
        val builder = Oidc.Builder()
        val oidc = builder
            .setVersion(EXPECTED_VALUE_1)
            .setUrl(EXPECTED_VALUE_2)
            .setToken(EXPECTED_VALUE_3)
            .build()
        val encoded = oidc.encode()

        Assert.assertNotNull(encoded)
        Assert.assertArrayEquals(expectedEncoded, encoded)
    }

    @Test
    fun fromArrayTest() {
        val value1 = UnsignedInteger(1L)
        val value2 = UnicodeString("value2")
        val value3 = UnicodeString("value3")

        val arr = Array()
        arr.add(value1)
        arr.add(value2)
        arr.add(value3)

        val decoded = Oidc.Builder()
                .fromArray(arr)
                .build()

        Assert.assertNotNull(decoded)
        Assert.assertEquals(value1.value.toInt(), decoded.version)
        Assert.assertEquals(value2.string, decoded.url)
        Assert.assertEquals(value3.string, decoded.token)
    }

    @Test
    fun testEquals() {
        var builder = Oidc.Builder()
        val oidc = builder
            .setVersion(EXPECTED_VALUE_1)
            .setUrl(EXPECTED_VALUE_2)
            .setToken(EXPECTED_VALUE_3)
            .build()

        builder = Oidc.Builder()
        val oidc1 = builder
            .setVersion(EXPECTED_VALUE_1)
            .setUrl(EXPECTED_VALUE_2)
            .setToken(EXPECTED_VALUE_3)
            .build()

        Assert.assertNotNull(oidc)
        Assert.assertNotNull(oidc1)
        Assert.assertTrue(oidc == oidc1)
    }

    @Test
    fun hashCodeTest() {
        val oidc = Oidc.Builder()
            .setVersion(EXPECTED_VALUE_1)
            .setUrl(EXPECTED_VALUE_2)
            .setToken(EXPECTED_VALUE_3)
            .build()

        Assert.assertEquals(658028732, oidc.hashCode())
    }
}