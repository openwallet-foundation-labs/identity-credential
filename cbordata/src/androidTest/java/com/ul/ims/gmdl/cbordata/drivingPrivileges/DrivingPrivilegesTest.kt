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

package com.ul.ims.gmdl.cbordata.drivingPrivileges

import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.UnicodeString
import co.nstant.`in`.cbor.model.UnsignedInteger
import com.ul.ims.gmdl.cbordata.utils.CborUtils.encodeToString
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream


class DrivingPrivilegesTest {

    private val expectedEncoded = byteArrayOf(
        0x84.toByte(), 0xa3.toByte(), 0x75.toByte(), 0x76.toByte(), 0x65.toByte(),
        0x68.toByte(), 0x69.toByte(), 0x63.toByte(), 0x6c.toByte(), 0x65.toByte(),
        0x5f.toByte(), 0x63.toByte(), 0x61.toByte(), 0x74.toByte(), 0x65.toByte(),
        0x67.toByte(), 0x6f.toByte(), 0x72.toByte(), 0x79.toByte(), 0x5f.toByte(),
        0x63.toByte(), 0x6f.toByte(), 0x64.toByte(), 0x65.toByte(), 0x61.toByte(),
        0x41.toByte(), 0x6a.toByte(), 0x69.toByte(), 0x73.toByte(), 0x73.toByte(),
        0x75.toByte(), 0x65.toByte(), 0x5f.toByte(), 0x64.toByte(), 0x61.toByte(),
        0x74.toByte(), 0x65.toByte(), 0xd9.toByte(), 0x46.toByte(), 0x5d.toByte(),
        0x6a.toByte(), 0x32.toByte(), 0x30.toByte(), 0x31.toByte(), 0x39.toByte(),
        0x2d.toByte(), 0x31.toByte(), 0x32.toByte(), 0x2d.toByte(), 0x30.toByte(),
        0x34.toByte(), 0x6b.toByte(), 0x65.toByte(), 0x78.toByte(), 0x70.toByte(),
        0x69.toByte(), 0x72.toByte(), 0x79.toByte(), 0x5f.toByte(), 0x64.toByte(),
        0x61.toByte(), 0x74.toByte(), 0x65.toByte(), 0xd9.toByte(), 0x46.toByte(),
        0x5d.toByte(), 0x6a.toByte(), 0x32.toByte(), 0x30.toByte(), 0x32.toByte(),
        0x39.toByte(), 0x2d.toByte(), 0x31.toByte(), 0x32.toByte(), 0x2d.toByte(),
        0x30.toByte(), 0x31.toByte(), 0xa2.toByte(), 0x75.toByte(), 0x76.toByte(),
        0x65.toByte(), 0x68.toByte(), 0x69.toByte(), 0x63.toByte(), 0x6c.toByte(),
        0x65.toByte(), 0x5f.toByte(), 0x63.toByte(), 0x61.toByte(), 0x74.toByte(),
        0x65.toByte(), 0x67.toByte(), 0x6f.toByte(), 0x72.toByte(), 0x79.toByte(),
        0x5f.toByte(), 0x63.toByte(), 0x6f.toByte(), 0x64.toByte(), 0x65.toByte(),
        0x61.toByte(), 0x42.toByte(), 0x6b.toByte(), 0x65.toByte(), 0x78.toByte(),
        0x70.toByte(), 0x69.toByte(), 0x72.toByte(), 0x79.toByte(), 0x5f.toByte(),
        0x64.toByte(), 0x61.toByte(), 0x74.toByte(), 0x65.toByte(), 0xd9.toByte(),
        0x46.toByte(), 0x5d.toByte(), 0x6a.toByte(), 0x32.toByte(), 0x30.toByte(),
        0x32.toByte(), 0x39.toByte(), 0x2d.toByte(), 0x31.toByte(), 0x32.toByte(),
        0x2d.toByte(), 0x30.toByte(), 0x31.toByte(), 0xa2.toByte(), 0x75.toByte(),
        0x76.toByte(), 0x65.toByte(), 0x68.toByte(), 0x69.toByte(), 0x63.toByte(),
        0x6c.toByte(), 0x65.toByte(), 0x5f.toByte(), 0x63.toByte(), 0x61.toByte(),
        0x74.toByte(), 0x65.toByte(), 0x67.toByte(), 0x6f.toByte(), 0x72.toByte(),
        0x79.toByte(), 0x5f.toByte(), 0x63.toByte(), 0x6f.toByte(), 0x64.toByte(),
        0x65.toByte(), 0x61.toByte(), 0x43.toByte(), 0x6a.toByte(), 0x69.toByte(),
        0x73.toByte(), 0x73.toByte(), 0x75.toByte(), 0x65.toByte(), 0x5f.toByte(),
        0x64.toByte(), 0x61.toByte(), 0x74.toByte(), 0x65.toByte(), 0xd9.toByte(),
        0x46.toByte(), 0x5d.toByte(), 0x6a.toByte(), 0x32.toByte(), 0x30.toByte(),
        0x31.toByte(), 0x39.toByte(), 0x2d.toByte(), 0x31.toByte(), 0x32.toByte(),
        0x2d.toByte(), 0x30.toByte(), 0x34.toByte(), 0xa1.toByte(), 0x75.toByte(),
        0x76.toByte(), 0x65.toByte(), 0x68.toByte(), 0x69.toByte(), 0x63.toByte(),
        0x6c.toByte(), 0x65.toByte(), 0x5f.toByte(), 0x63.toByte(), 0x61.toByte(),
        0x74.toByte(), 0x65.toByte(), 0x67.toByte(), 0x6f.toByte(), 0x72.toByte(),
        0x79.toByte(), 0x5f.toByte(), 0x63.toByte(), 0x6f.toByte(), 0x64.toByte(),
        0x65.toByte(), 0x61.toByte(), 0x44.toByte()
    )

    private lateinit var drivingPrivileges: DrivingPrivileges

    @Before
    fun setUp() {
        drivingPrivileges = DrivingPrivileges.Builder()
            .useHardcodedData().build()
    }

    @Test
    fun encodeTest() {
        val encoded = drivingPrivileges.encode()

        Assert.assertNotNull(encoded)
        Assert.assertArrayEquals(expectedEncoded, encoded)
    }

    @Test
    fun toCborDataItemTest() {
        val dataItem = drivingPrivileges.toCborDataItem()

        Assert.assertNotNull(dataItem)

        val privileges = DrivingPrivileges.Builder()
            .fromCborDataItem(dataItem)
            .build()

        Assert.assertNotNull(privileges)
        Assert.assertEquals(4, privileges.drivingPrivileges.size)
    }

    @Test
    fun fromCborBytesTest() {
        val privileges = DrivingPrivileges.Builder()
            .fromCborBytes(expectedEncoded)
            .build()

        Assert.assertNotNull(privileges)
        Assert.assertEquals(4, privileges.drivingPrivileges.size)
    }

    @Test
    fun cborCanonicalTest() {
        val map = Map()
        map.put(UnicodeString("c"), UnsignedInteger(3))
        map.put(UnicodeString("b"), UnsignedInteger(2))
        map.put(UnicodeString("a"), UnsignedInteger(1))

        var baos = ByteArrayOutputStream()
        CborEncoder(baos).encode(map)
        Assert.assertEquals("a3616101616202616303", encodeToString(baos.toByteArray()))

        baos = ByteArrayOutputStream()
        CborEncoder(baos.encode(map)
        Assert.assertEquals("a3616303616202616101", encodeToString(baos.toByteArray()))
    }
}