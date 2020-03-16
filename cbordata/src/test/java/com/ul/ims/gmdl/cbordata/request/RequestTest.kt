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

import com.ul.ims.gmdl.cbordata.doctype.DocType
import com.ul.ims.gmdl.cbordata.doctype.MdlDoctype
import com.ul.ims.gmdl.cbordata.namespace.CborNamespace
import com.ul.ims.gmdl.cbordata.namespace.MdlNamespace
import com.ul.ims.gmdl.cbordata.namespace.Namespace
import org.junit.Assert
import org.junit.Test

class RequestTest {
    val encodedExpected = byteArrayOf(
        0xa2.toByte(), 0x67.toByte(), 0x76.toByte(), 0x65.toByte(), 0x72.toByte(),
        0x73.toByte(), 0x69.toByte(), 0x6f.toByte(), 0x6e.toByte(), 0x63.toByte(),
        0x31.toByte(), 0x2e.toByte(), 0x30.toByte(), 0x6b.toByte(), 0x64.toByte(),
        0x6f.toByte(), 0x63.toByte(), 0x52.toByte(), 0x65.toByte(), 0x71.toByte(),
        0x75.toByte(), 0x65.toByte(), 0x73.toByte(), 0x74.toByte(), 0x73.toByte(),
        0x81.toByte(), 0xa1.toByte(), 0x6c.toByte(), 0x69.toByte(), 0x74.toByte(),
        0x65.toByte(), 0x6d.toByte(), 0x73.toByte(), 0x52.toByte(), 0x65.toByte(),
        0x71.toByte(), 0x75.toByte(), 0x65.toByte(), 0x73.toByte(), 0x74.toByte(),
        0xd8.toByte(), 0x18.toByte(), 0x58.toByte(), 0xcf.toByte(), 0xa2.toByte(),
        0x67.toByte(), 0x64.toByte(), 0x6f.toByte(), 0x63.toByte(), 0x54.toByte(),
        0x79.toByte(), 0x70.toByte(), 0x65.toByte(), 0x75.toByte(), 0x6f.toByte(),
        0x72.toByte(), 0x67.toByte(), 0x2e.toByte(), 0x69.toByte(), 0x73.toByte(),
        0x6f.toByte(), 0x2e.toByte(), 0x31.toByte(), 0x38.toByte(), 0x30.toByte(),
        0x31.toByte(), 0x33.toByte(), 0x2e.toByte(), 0x35.toByte(), 0x2e.toByte(),
        0x31.toByte(), 0x2e.toByte(), 0x6d.toByte(), 0x44.toByte(), 0x4c.toByte(),
        0x6a.toByte(), 0x6e.toByte(), 0x61.toByte(), 0x6d.toByte(), 0x65.toByte(),
        0x53.toByte(), 0x70.toByte(), 0x61.toByte(), 0x63.toByte(), 0x65.toByte(),
        0x73.toByte(), 0xa1.toByte(), 0x71.toByte(), 0x6f.toByte(), 0x72.toByte(),
        0x67.toByte(), 0x2e.toByte(), 0x69.toByte(), 0x73.toByte(), 0x6f.toByte(),
        0x2e.toByte(), 0x31.toByte(), 0x38.toByte(), 0x30.toByte(), 0x31.toByte(),
        0x33.toByte(), 0x2e.toByte(), 0x35.toByte(), 0x2e.toByte(), 0x31.toByte(),
        0xaa.toByte(), 0x68.toByte(), 0x70.toByte(), 0x6f.toByte(), 0x72.toByte(),
        0x74.toByte(), 0x72.toByte(), 0x61.toByte(), 0x69.toByte(), 0x74.toByte(),
        0xf4.toByte(), 0x6a.toByte(), 0x62.toByte(), 0x69.toByte(), 0x72.toByte(),
        0x74.toByte(), 0x68.toByte(), 0x5f.toByte(), 0x64.toByte(), 0x61.toByte(),
        0x74.toByte(), 0x65.toByte(), 0xf4.toByte(), 0x6a.toByte(), 0x67.toByte(),
        0x69.toByte(), 0x76.toByte(), 0x65.toByte(), 0x6e.toByte(), 0x5f.toByte(),
        0x6e.toByte(), 0x61.toByte(), 0x6d.toByte(), 0x65.toByte(), 0xf4.toByte(),
        0x6a.toByte(), 0x69.toByte(), 0x73.toByte(), 0x73.toByte(), 0x75.toByte(),
        0x65.toByte(), 0x5f.toByte(), 0x64.toByte(), 0x61.toByte(), 0x74.toByte(),
        0x65.toByte(), 0xf4.toByte(), 0x6b.toByte(), 0x65.toByte(), 0x78.toByte(),
        0x70.toByte(), 0x69.toByte(), 0x72.toByte(), 0x79.toByte(), 0x5f.toByte(),
        0x64.toByte(), 0x61.toByte(), 0x74.toByte(), 0x65.toByte(), 0xf4.toByte(),
        0x6b.toByte(), 0x66.toByte(), 0x61.toByte(), 0x6d.toByte(), 0x69.toByte(),
        0x6c.toByte(), 0x79.toByte(), 0x5f.toByte(), 0x6e.toByte(), 0x61.toByte(),
        0x6d.toByte(), 0x65.toByte(), 0xf4.toByte(), 0x6f.toByte(), 0x64.toByte(),
        0x6f.toByte(), 0x63.toByte(), 0x75.toByte(), 0x6d.toByte(), 0x65.toByte(),
        0x6e.toByte(), 0x74.toByte(), 0x5f.toByte(), 0x6e.toByte(), 0x75.toByte(),
        0x6d.toByte(), 0x62.toByte(), 0x65.toByte(), 0x72.toByte(), 0xf4.toByte(),
        0x6f.toByte(), 0x69.toByte(), 0x73.toByte(), 0x73.toByte(), 0x75.toByte(),
        0x69.toByte(), 0x6e.toByte(), 0x67.toByte(), 0x5f.toByte(), 0x63.toByte(),
        0x6f.toByte(), 0x75.toByte(), 0x6e.toByte(), 0x74.toByte(), 0x72.toByte(),
        0x79.toByte(), 0xf4.toByte(), 0x71.toByte(), 0x69.toByte(), 0x73.toByte(),
        0x73.toByte(), 0x75.toByte(), 0x69.toByte(), 0x6e.toByte(), 0x67.toByte(),
        0x5f.toByte(), 0x61.toByte(), 0x75.toByte(), 0x74.toByte(), 0x68.toByte(),
        0x6f.toByte(), 0x72.toByte(), 0x69.toByte(), 0x74.toByte(), 0x79.toByte(),
        0xf4.toByte(), 0x72.toByte(), 0x64.toByte(), 0x72.toByte(), 0x69.toByte(),
        0x76.toByte(), 0x69.toByte(), 0x6e.toByte(), 0x67.toByte(), 0x5f.toByte(),
        0x70.toByte(), 0x72.toByte(), 0x69.toByte(), 0x76.toByte(), 0x69.toByte(),
        0x6c.toByte(), 0x65.toByte(), 0x67.toByte(), 0x65.toByte(), 0x73.toByte(),
        0xf4.toByte()
    )

    val expectedEncodedRequest = byteArrayOf(
        0xa2.toByte(), 0x67.toByte(), 0x76.toByte(), 0x65.toByte(), 0x72.toByte(),
        0x73.toByte(), 0x69.toByte(), 0x6f.toByte(), 0x6e.toByte(), 0x63.toByte(),
        0x31.toByte(), 0x2e.toByte(), 0x30.toByte(), 0x6b.toByte(), 0x64.toByte(),
        0x6f.toByte(), 0x63.toByte(), 0x52.toByte(), 0x65.toByte(), 0x71.toByte(),
        0x75.toByte(), 0x65.toByte(), 0x73.toByte(), 0x74.toByte(), 0x73.toByte(),
        0x81.toByte(), 0xa1.toByte(), 0x6c.toByte(), 0x69.toByte(), 0x74.toByte(),
        0x65.toByte(), 0x6d.toByte(), 0x73.toByte(), 0x52.toByte(), 0x65.toByte(),
        0x71.toByte(), 0x75.toByte(), 0x65.toByte(), 0x73.toByte(), 0x74.toByte(),
        0xd8.toByte(), 0x18.toByte(), 0x58.toByte(), 0xcf.toByte(), 0xa2.toByte(),
        0x67.toByte(), 0x64.toByte(), 0x6f.toByte(), 0x63.toByte(), 0x54.toByte(),
        0x79.toByte(), 0x70.toByte(), 0x65.toByte(), 0x75.toByte(), 0x6f.toByte(),
        0x72.toByte(), 0x67.toByte(), 0x2e.toByte(), 0x69.toByte(), 0x73.toByte(),
        0x6f.toByte(), 0x2e.toByte(), 0x31.toByte(), 0x38.toByte(), 0x30.toByte(),
        0x31.toByte(), 0x33.toByte(), 0x2e.toByte(), 0x35.toByte(), 0x2e.toByte(),
        0x31.toByte(), 0x2e.toByte(), 0x6d.toByte(), 0x44.toByte(), 0x4c.toByte(),
        0x6a.toByte(), 0x6e.toByte(), 0x61.toByte(), 0x6d.toByte(), 0x65.toByte(),
        0x53.toByte(), 0x70.toByte(), 0x61.toByte(), 0x63.toByte(), 0x65.toByte(),
        0x73.toByte(), 0xa1.toByte(), 0x71.toByte(), 0x6f.toByte(), 0x72.toByte(),
        0x67.toByte(), 0x2e.toByte(), 0x69.toByte(), 0x73.toByte(), 0x6f.toByte(),
        0x2e.toByte(), 0x31.toByte(), 0x38.toByte(), 0x30.toByte(), 0x31.toByte(),
        0x33.toByte(), 0x2e.toByte(), 0x35.toByte(), 0x2e.toByte(), 0x31.toByte(),
        0xaa.toByte(), 0x68.toByte(), 0x70.toByte(), 0x6f.toByte(), 0x72.toByte(),
        0x74.toByte(), 0x72.toByte(), 0x61.toByte(), 0x69.toByte(), 0x74.toByte(),
        0xf4.toByte(), 0x6a.toByte(), 0x62.toByte(), 0x69.toByte(), 0x72.toByte(),
        0x74.toByte(), 0x68.toByte(), 0x5f.toByte(), 0x64.toByte(), 0x61.toByte(),
        0x74.toByte(), 0x65.toByte(), 0xf4.toByte(), 0x6a.toByte(), 0x67.toByte(),
        0x69.toByte(), 0x76.toByte(), 0x65.toByte(), 0x6e.toByte(), 0x5f.toByte(),
        0x6e.toByte(), 0x61.toByte(), 0x6d.toByte(), 0x65.toByte(), 0xf4.toByte(),
        0x6a.toByte(), 0x69.toByte(), 0x73.toByte(), 0x73.toByte(), 0x75.toByte(),
        0x65.toByte(), 0x5f.toByte(), 0x64.toByte(), 0x61.toByte(), 0x74.toByte(),
        0x65.toByte(), 0xf4.toByte(), 0x6b.toByte(), 0x65.toByte(), 0x78.toByte(),
        0x70.toByte(), 0x69.toByte(), 0x72.toByte(), 0x79.toByte(), 0x5f.toByte(),
        0x64.toByte(), 0x61.toByte(), 0x74.toByte(), 0x65.toByte(), 0xf4.toByte(),
        0x6b.toByte(), 0x66.toByte(), 0x61.toByte(), 0x6d.toByte(), 0x69.toByte(),
        0x6c.toByte(), 0x79.toByte(), 0x5f.toByte(), 0x6e.toByte(), 0x61.toByte(),
        0x6d.toByte(), 0x65.toByte(), 0xf4.toByte(), 0x6f.toByte(), 0x64.toByte(),
        0x6f.toByte(), 0x63.toByte(), 0x75.toByte(), 0x6d.toByte(), 0x65.toByte(),
        0x6e.toByte(), 0x74.toByte(), 0x5f.toByte(), 0x6e.toByte(), 0x75.toByte(),
        0x6d.toByte(), 0x62.toByte(), 0x65.toByte(), 0x72.toByte(), 0xf4.toByte(),
        0x6f.toByte(), 0x69.toByte(), 0x73.toByte(), 0x73.toByte(), 0x75.toByte(),
        0x69.toByte(), 0x6e.toByte(), 0x67.toByte(), 0x5f.toByte(), 0x63.toByte(),
        0x6f.toByte(), 0x75.toByte(), 0x6e.toByte(), 0x74.toByte(), 0x72.toByte(),
        0x79.toByte(), 0xf4.toByte(), 0x71.toByte(), 0x69.toByte(), 0x73.toByte(),
        0x73.toByte(), 0x75.toByte(), 0x69.toByte(), 0x6e.toByte(), 0x67.toByte(),
        0x5f.toByte(), 0x61.toByte(), 0x75.toByte(), 0x74.toByte(), 0x68.toByte(),
        0x6f.toByte(), 0x72.toByte(), 0x69.toByte(), 0x74.toByte(), 0x79.toByte(),
        0xf4.toByte(), 0x72.toByte(), 0x64.toByte(), 0x72.toByte(), 0x69.toByte(),
        0x76.toByte(), 0x69.toByte(), 0x6e.toByte(), 0x67.toByte(), 0x5f.toByte(),
        0x70.toByte(), 0x72.toByte(), 0x69.toByte(), 0x76.toByte(), 0x69.toByte(),
        0x6c.toByte(), 0x65.toByte(), 0x67.toByte(), 0x65.toByte(), 0x73.toByte(),
        0xf4.toByte()
    )

    val version = "1.0"
    val doctype = MdlDoctype
    val wrongDoctype = "org.iso.2019"
    val unsupportedDoctype = DocType(wrongDoctype)
    val unsupportedNamespace = Namespace(wrongDoctype, LinkedHashMap())

    @Test
    fun testEncode() {
        val doctype1 = DocType(MdlDoctype.docType)
        val dataElements = DataElements.Builder()
            .dataElements(MdlNamespace.items)
            .build()

        val cborNamespace1 = CborNamespace.Builder()
            .addNamespace(MdlNamespace.namespace, dataElements)
            .build()

        val itemsRequest1 = ItemsRequest.Builder()
            .docType(doctype1)
            .namespaces(cborNamespace1)
            .build()

        val docRequest = DocRequest.Builder()
            .itemsRequest(itemsRequest1)
            .build()

        var request: Request? = null

        docRequest?.let { doc ->
            request = Request.Builder()
                .setVersion(version)
                .addDocRequest(doc)
                .build()
        }

        val encoded1 = request?.encode()
        Assert.assertArrayEquals(expectedEncodedRequest, encoded1)
    }

    @Test
    fun testDecode() {
        val request = Request.Builder()
                .decode(encodedExpected)
                .build()

        Assert.assertNotNull(request)
        Assert.assertEquals("1.0", request.version)
        Assert.assertEquals(1, request.docRequests.size)
    }

//    @Test
//    fun testDefaultRequest() {
//        val req = Request.Builder()
//                .dataItemsToRequest()
//                .build()
//
//        Assert.assertArrayEquals(encodedExpected, req.encode())
//    }

    @Test
    fun isValidTest() {
        // Test Valid Cases
        var dataElements = DataElements.Builder()
            .dataElements(MdlNamespace.items)
            .build()

        var cborNamespace = CborNamespace.Builder()
            .addNamespace(MdlNamespace.namespace, dataElements)
            .build()

        var itemsRequest = ItemsRequest.Builder()
            .namespaces(cborNamespace)
            .build()

        var docRequest = DocRequest.Builder()
            .itemsRequest(itemsRequest)
            .build()

        var request: Request? = null

        docRequest?.let { doc ->
            request = Request.Builder()
                .setVersion(version)
                .addDocRequest(doc)
                .build()
        }

        Assert.assertNotNull(request)
        Assert.assertTrue(request?.isValid() == true)


        cborNamespace = CborNamespace.Builder()
            .addNamespace(MdlNamespace.namespace, dataElements)
            .build()


        itemsRequest = ItemsRequest.Builder()
            .docType(doctype)
            .namespaces(cborNamespace)
            .build()

        docRequest = DocRequest.Builder()
            .itemsRequest(itemsRequest)
            .build()

        docRequest?.let { doc ->
            request = Request.Builder()
                .setVersion(version)
                .addDocRequest(doc)
                .build()
        }

        Assert.assertNotNull(request)
        Assert.assertTrue(request?.isValid() == true)

        // Test Invalid Cases
        cborNamespace = CborNamespace.Builder()
            .addNamespace(MdlNamespace.namespace, dataElements)
            .build()

        itemsRequest = ItemsRequest.Builder()
            .docType(unsupportedDoctype)
            .namespaces(cborNamespace)
            .build()

        docRequest = DocRequest.Builder()
            .itemsRequest(itemsRequest)
            .build()

        docRequest?.let { doc ->
            request = Request.Builder()
                .setVersion(version)
                .addDocRequest(doc)
                .build()
        }

        Assert.assertNotNull(request)
        Assert.assertFalse(request?.isValid() == true)

        dataElements = DataElements.Builder()
            .dataElements(unsupportedNamespace.items)
            .build()

        cborNamespace = CborNamespace.Builder()
            .addNamespace(unsupportedNamespace.namespace, dataElements)
            .build()

        itemsRequest = ItemsRequest.Builder()
            .namespaces(cborNamespace)
            .build()

        docRequest = DocRequest.Builder()
            .itemsRequest(itemsRequest)
            .build()

        docRequest?.let { doc ->
            request = Request.Builder()
                .setVersion(version)
                .addDocRequest(doc)
                .build()
        }

        Assert.assertNotNull(request)
        Assert.assertFalse(request?.isValid() == true)
    }
}