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

package com.ul.ims.gmdl.cbordata.request

import com.ul.ims.gmdl.cbordata.doctype.MdlDoctype
import com.ul.ims.gmdl.cbordata.namespace.CborNamespace
import com.ul.ims.gmdl.cbordata.namespace.MdlNamespace
import org.junit.Assert
import org.junit.Test

class ItemsRequestTest {
    private val dataElements = DataElements.Builder()
        .dataElements(MdlNamespace.items)
        .build()

    private val cborNamespace = CborNamespace.Builder()
        .addNamespace(MdlNamespace.namespace, dataElements)
        .build()

    private val requestInfo = mutableMapOf(
        Pair("Hello", "World"),
        Pair("Test", "Data"),
        Pair("Answer", 0x2A)
    )

    val encodedExpected = byteArrayOf(
        0xa2.toByte(), 0x67.toByte(), 0x44.toByte(), 0x6f.toByte(), 0x63.toByte(),
        0x54.toByte(), 0x79.toByte(), 0x70.toByte(), 0x65.toByte(), 0x74.toByte(),
        0x6f.toByte(), 0x72.toByte(), 0x67.toByte(), 0x2e.toByte(), 0x69.toByte(),
        0x73.toByte(), 0x6f.toByte(), 0x2e.toByte(), 0x31.toByte(), 0x38.toByte(),
        0x30.toByte(), 0x31.toByte(), 0x33.toByte(), 0x2d.toByte(), 0x35.toByte(),
        0x2e.toByte(), 0x32.toByte(), 0x30.toByte(), 0x31.toByte(), 0x39.toByte(),
        0x6a.toByte(), 0x4e.toByte(), 0x61.toByte(), 0x6d.toByte(), 0x65.toByte(),
        0x53.toByte(), 0x70.toByte(), 0x61.toByte(), 0x63.toByte(), 0x65.toByte(),
        0x73.toByte(), 0xa1.toByte(), 0x74.toByte(), 0x6f.toByte(), 0x72.toByte(),
        0x67.toByte(), 0x2e.toByte(), 0x69.toByte(), 0x73.toByte(), 0x6f.toByte(),
        0x2e.toByte(), 0x31.toByte(), 0x38.toByte(), 0x30.toByte(), 0x31.toByte(),
        0x33.toByte(), 0x2d.toByte(), 0x35.toByte(), 0x2e.toByte(), 0x32.toByte(),
        0x30.toByte(), 0x31.toByte(), 0x39.toByte(), 0x8c.toByte(), 0x6b.toByte(),
        0x66.toByte(), 0x61.toByte(), 0x6d.toByte(), 0x69.toByte(), 0x6c.toByte(),
        0x79.toByte(), 0x5f.toByte(), 0x6e.toByte(), 0x61.toByte(), 0x6d.toByte(),
        0x65.toByte(), 0x6a.toByte(), 0x67.toByte(), 0x69.toByte(), 0x76.toByte(),
        0x65.toByte(), 0x6e.toByte(), 0x5f.toByte(), 0x6e.toByte(), 0x61.toByte(),
        0x6d.toByte(), 0x65.toByte(), 0x69.toByte(), 0x62.toByte(), 0x69.toByte(),
        0x72.toByte(), 0x74.toByte(), 0x68.toByte(), 0x64.toByte(), 0x61.toByte(),
        0x74.toByte(), 0x65.toByte(), 0x6a.toByte(), 0x69.toByte(), 0x73.toByte(),
        0x73.toByte(), 0x75.toByte(), 0x65.toByte(), 0x5f.toByte(), 0x64.toByte(),
        0x61.toByte(), 0x74.toByte(), 0x65.toByte(), 0x6b.toByte(), 0x65.toByte(),
        0x78.toByte(), 0x70.toByte(), 0x69.toByte(), 0x72.toByte(), 0x79.toByte(),
        0x5f.toByte(), 0x64.toByte(), 0x61.toByte(), 0x74.toByte(), 0x65.toByte(),
        0x6f.toByte(), 0x69.toByte(), 0x73.toByte(), 0x73.toByte(), 0x75.toByte(),
        0x69.toByte(), 0x6e.toByte(), 0x67.toByte(), 0x5f.toByte(), 0x63.toByte(),
        0x6f.toByte(), 0x75.toByte(), 0x6e.toByte(), 0x74.toByte(), 0x72.toByte(),
        0x79.toByte(), 0x71.toByte(), 0x69.toByte(), 0x73.toByte(), 0x73.toByte(),
        0x75.toByte(), 0x69.toByte(), 0x6e.toByte(), 0x67.toByte(), 0x5f.toByte(),
        0x61.toByte(), 0x75.toByte(), 0x74.toByte(), 0x68.toByte(), 0x6f.toByte(),
        0x72.toByte(), 0x69.toByte(), 0x74.toByte(), 0x79.toByte(), 0x6f.toByte(),
        0x64.toByte(), 0x6f.toByte(), 0x63.toByte(), 0x75.toByte(), 0x6d.toByte(),
        0x65.toByte(), 0x6e.toByte(), 0x74.toByte(), 0x5f.toByte(), 0x6e.toByte(),
        0x75.toByte(), 0x6d.toByte(), 0x62.toByte(), 0x65.toByte(), 0x72.toByte(),
        0x72.toByte(), 0x64.toByte(), 0x72.toByte(), 0x69.toByte(), 0x76.toByte(),
        0x69.toByte(), 0x6e.toByte(), 0x67.toByte(), 0x5f.toByte(), 0x70.toByte(),
        0x72.toByte(), 0x69.toByte(), 0x76.toByte(), 0x69.toByte(), 0x6c.toByte(),
        0x65.toByte(), 0x67.toByte(), 0x65.toByte(), 0x73.toByte(), 0x68.toByte(),
        0x70.toByte(), 0x6f.toByte(), 0x72.toByte(), 0x74.toByte(), 0x72.toByte(),
        0x61.toByte(), 0x69.toByte(), 0x74.toByte(), 0x6f.toByte(), 0x6d.toByte(),
        0x67.toByte(), 0x6d.toByte(), 0x74.toByte(), 0x5f.toByte(), 0x6c.toByte(),
        0x61.toByte(), 0x73.toByte(), 0x74.toByte(), 0x75.toByte(), 0x70.toByte(),
        0x64.toByte(), 0x61.toByte(), 0x74.toByte(), 0x65.toByte(), 0x6d.toByte(),
        0x6d.toByte(), 0x67.toByte(), 0x6d.toByte(), 0x74.toByte(), 0x5f.toByte(),
        0x76.toByte(), 0x61.toByte(), 0x6c.toByte(), 0x69.toByte(), 0x64.toByte(),
        0x69.toByte(), 0x74.toByte(), 0x79.toByte()
    )

    val doctype = MdlDoctype

    @Test
    fun testBuilder() {
        val itemsRequest = ItemsRequest.Builder()
                .docType(doctype)
                .namespaces(cborNamespace)
                .requestInfo(requestInfo)
                .build()

        Assert.assertNotNull(itemsRequest)
        itemsRequest?.let {item ->
            item.doctype?.let {
                Assert.assertEquals(doctype.docType, it.docType)
            }
            Assert.assertEquals(cborNamespace, item.namespaces)
            Assert.assertEquals(requestInfo, item.requestInfo)
        }
    }

//    @Test
//    fun testEncode() {
//        val itemsRequest = CborItemsRequest.Builder()
//                .docType(doctype)
//                .namespaces(cborNamespace)
//                .build()
//
//        itemsRequest?.let {item ->
//            val encoded = item.encode()
//            Assert.assertArrayEquals(encodedExpected, encoded)
//        }
//    }
}