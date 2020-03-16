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

class WebApiTest {

    companion object {
        const val EXPECTED_VERSION = 99
        val EXPECTED_TOKEN = "my-token-abcdef"
        val EXPECTED_BASEURL = "trident.ul.com"
    }

    val expectedEncoded = byteArrayOf(
            0x83.toByte(), 0x18.toByte(), 0x63.toByte(), 0x6e.toByte(), 0x74.toByte(),
            0x72.toByte(), 0x69.toByte(), 0x64.toByte(), 0x65.toByte(), 0x6e.toByte(),
            0x74.toByte(), 0x2e.toByte(), 0x75.toByte(), 0x6c.toByte(), 0x2e.toByte(),
            0x63.toByte(), 0x6f.toByte(), 0x6d.toByte(), 0x6f.toByte(), 0x6d.toByte(),
            0x79.toByte(), 0x2d.toByte(), 0x74.toByte(), 0x6f.toByte(), 0x6b.toByte(),
            0x65.toByte(), 0x6e.toByte(), 0x2d.toByte(), 0x61.toByte(), 0x62.toByte(),
            0x63.toByte(), 0x64.toByte(), 0x65.toByte(), 0x66.toByte()
    )

    @Test
    fun testBuilder() {
        val builder = WebAPI.Builder()
        builder.setVersion(EXPECTED_VERSION)
        builder.setToken(EXPECTED_TOKEN)
        builder.setBaseUrl(EXPECTED_BASEURL)
        val webApi = builder.build()

        Assert.assertNotNull(webApi)
        Assert.assertEquals(EXPECTED_VERSION, webApi.version)
        Assert.assertEquals(EXPECTED_TOKEN, webApi.token)
        Assert.assertEquals(EXPECTED_BASEURL, webApi.baseUrl)
    }

    @Test
    fun testEncode() {
        val builder = WebAPI.Builder()
        builder.setVersion(EXPECTED_VERSION)
        builder.setToken(EXPECTED_TOKEN)
        builder.setBaseUrl(EXPECTED_BASEURL)
        val webApi = builder.build()
        val encoded = webApi.encode()

        Assert.assertNotNull(encoded)
        Assert.assertArrayEquals(expectedEncoded, encoded)
    }

    @Test
    fun testDecode() {
        val version = UnsignedInteger(1L)
        val token = UnicodeString("my-token")
        val baseUrl = UnicodeString("trident.ul.com")

        val arr = Array()
        arr.add(version)
        arr.add(baseUrl)
        arr.add(token)

        val decoded = WebAPI.Builder().fromArray(arr).build()

        Assert.assertNotNull(decoded)
        Assert.assertEquals(version.value.toInt(), decoded.version)
        Assert.assertEquals(token.string, decoded.token)
        Assert.assertEquals(baseUrl.string, decoded.baseUrl)
    }

    @Test
    fun testEquals() {
        var builder = WebAPI.Builder()
        builder.setVersion(EXPECTED_VERSION)
        builder.setToken(EXPECTED_TOKEN)
        builder.setBaseUrl(EXPECTED_BASEURL)
        val webApi = builder.build()

        builder.setVersion(EXPECTED_VERSION)
        builder.setToken(EXPECTED_TOKEN)
        builder.setBaseUrl(EXPECTED_BASEURL)
        val webApi1 = builder.build()

        builder.setVersion(EXPECTED_VERSION)
        builder.setToken(EXPECTED_BASEURL)
        builder.setBaseUrl(EXPECTED_TOKEN)
        val webApi2 = builder.build()

        Assert.assertNotNull(webApi)
        Assert.assertNotNull(webApi1)
        Assert.assertNotNull(webApi2)
        Assert.assertTrue(webApi == webApi1)
        Assert.assertFalse(webApi1 == webApi2)
    }

    @Test
    fun hashCodeTest() {
        var builder = WebAPI.Builder()
        builder.setVersion(EXPECTED_VERSION)
        builder.setToken(EXPECTED_TOKEN)
        builder.setBaseUrl(EXPECTED_BASEURL)
        val webApi = builder.build()

        Assert.assertEquals(-909705035, webApi.hashCode())
    }
}