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

package com.ul.ims.gmdl.cbordata.namespace

import com.ul.ims.gmdl.cbordata.request.DataElements
import org.junit.Assert
import org.junit.Test

class CborNamespaceTest {
    val encodedExpected = byteArrayOf(
        0xa1.toByte(), 0x6b.toByte(), 0x63.toByte(), 0x6f.toByte(), 0x6d.toByte(),
        0x2e.toByte(), 0x75.toByte(), 0x6c.toByte(), 0x2e.toByte(), 0x74.toByte(),
        0x65.toByte(), 0x73.toByte(), 0x74.toByte(), 0xa2.toByte(), 0x65.toByte(),
        0x69.toByte(), 0x74.toByte(), 0x65.toByte(), 0x6d.toByte(), 0x31.toByte(),
        0xf4.toByte(), 0x65.toByte(), 0x69.toByte(), 0x74.toByte(), 0x65.toByte(),
        0x6d.toByte(), 0x32.toByte(), 0xf4.toByte()
    )

    @Test
    fun testBuilder() {
        val dataElements = DataElements.Builder()
            .dataElements(MdlNamespace.items)
            .build()

        val cborNamespace = CborNamespace.Builder()
            .addNamespace(MdlNamespace.namespace, dataElements)
                .build()

        Assert.assertNotNull(cborNamespace)
        Assert.assertTrue(cborNamespace.namespaces.size == 1)
        Assert.assertTrue(cborNamespace.namespaces.containsKey(MdlNamespace.namespace))
    }

    @Test
    fun testEncode() {
        val dataElements = DataElements.Builder()
            .dataElements(TestNamespace.items)
            .build()

        val cborNamespace = CborNamespace.Builder()
            .addNamespace(TestNamespace.namespace, dataElements)
                .build()
        val encoded = cborNamespace.encode()
        Assert.assertTrue(encodedExpected.contentEquals(encoded))
    }

    object TestNamespace : INamespace {
        override val namespace = "com.ul.test"
        override val items = linkedMapOf(
            Pair("item1", false),
            Pair("item2", false)
        )
    }
}