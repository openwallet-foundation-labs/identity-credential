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

package com.ul.ims.gmdl.cbordata.security.mdlauthentication

import com.ul.ims.gmdl.cbordata.security.mdlauthentication.CoseMac0.Companion.HMAC256_ALGORITHM_NAME
import org.junit.Assert
import org.junit.Test

class CoseMac0Tests {

    private val expectedCoseMac0Data = byteArrayOf(
        0xD1.toByte(), 0x84.toByte(), 0x43.toByte(), 0xa1.toByte(), 0x01.toByte(), 0x05.toByte(), 0xA0.toByte(), 0xf6.toByte(), 0x58.toByte(), 0x20.toByte(), 0x1c.toByte(), 0x1c.toByte(), 0xdc.toByte(), 0x37.toByte(), 0xeb.toByte(), 0x1e.toByte(), 0x6c.toByte(), 0xc2.toByte(), 0xf0.toByte(), 0x00.toByte(), 0x45.toByte(), 0x20.toByte(), 0xc2.toByte(), 0x0c.toByte(), 0xff.toByte(), 0xd9.toByte(), 0x04.toByte(), 0x5f.toByte(), 0xbf.toByte(), 0x72.toByte(), 0x60.toByte(), 0xe9.toByte(), 0xc4.toByte(), 0x7c.toByte(), 0xbe.toByte(), 0x91.toByte(), 0x28.toByte(), 0xeb.toByte(), 0xa0.toByte(), 0x3d.toByte(), 0x39.toByte(), 0xf9.toByte()
    )

    @Test
    fun testCoseMac0Encode() {
        val coseMac0Builder = CoseMac0.Builder()
        coseMac0Builder.setPayload(null)
        coseMac0Builder.setMacValue(byteArrayOf(0x1c.toByte(), 0x1c.toByte(), 0xdc.toByte(), 0x37.toByte(), 0xeb.toByte(), 0x1e.toByte(), 0x6c.toByte(), 0xc2.toByte(), 0xf0.toByte(), 0x00.toByte(), 0x45.toByte(), 0x20.toByte(), 0xc2.toByte(), 0x0c.toByte(), 0xff.toByte(), 0xd9.toByte(), 0x04.toByte(), 0x5f.toByte(), 0xbf.toByte(), 0x72.toByte(), 0x60.toByte(), 0xe9.toByte(), 0xc4.toByte(), 0x7c.toByte(), 0xbe.toByte(), 0x91.toByte(), 0x28.toByte(), 0xeb.toByte(), 0xa0.toByte(), 0x3d.toByte(), 0x39.toByte(), 0xf9.toByte()))
        val cMac0Data = coseMac0Builder.build().encode()

        Assert.assertArrayEquals(expectedCoseMac0Data, cMac0Data)
    }

    @Test
    fun testCoseMac0Decode() {
        val coseMac0 = CoseMac0.Builder().decode(expectedCoseMac0Data).build()
        val expectedMacValue = byteArrayOf(
            0x1c.toByte(), 0x1c.toByte(), 0xdc.toByte(), 0x37.toByte(), 0xeb.toByte(), 0x1e.toByte(), 0x6c.toByte(), 0xc2.toByte(), 0xf0.toByte(), 0x00.toByte(), 0x45.toByte(), 0x20.toByte(), 0xc2.toByte(), 0x0c.toByte(), 0xff.toByte(), 0xd9.toByte(), 0x04.toByte(), 0x5f.toByte(), 0xbf.toByte(), 0x72.toByte(), 0x60.toByte(), 0xe9.toByte(), 0xc4.toByte(), 0x7c.toByte(), 0xbe.toByte(), 0x91.toByte(), 0x28.toByte(), 0xeb.toByte(), 0xa0.toByte(), 0x3d.toByte(), 0x39.toByte(), 0xf9.toByte()
        )

        Assert.assertArrayEquals(expectedMacValue, coseMac0.macValue)
        Assert.assertNull(coseMac0.payload)
        Assert.assertTrue(HMAC256_ALGORITHM_NAME == "HMac/SHA256")
    }

    @Test
    fun testCoseMac0Decode_ExtraExample() {
        val data = byteArrayOf(
            0xD1.toByte(), 0x84.toByte(), 0x43.toByte(), 0xA1.toByte(), 0x01.toByte(), 0x05.toByte(), 0xA0.toByte(), 0xF6.toByte(), 0x58.toByte(), 0x20.toByte(), 0xC2.toByte(), 0x44.toByte(), 0xAE.toByte(), 0x94.toByte(), 0x5C.toByte(), 0x62.toByte(), 0x60.toByte(), 0x71.toByte(), 0x2D.toByte(), 0x9E.toByte(), 0x5B.toByte(), 0xC1.toByte(), 0x53.toByte(), 0x0D.toByte(), 0x25.toByte(), 0x6A.toByte(), 0xA9.toByte(), 0x14.toByte(), 0xD0.toByte(), 0x8E.toByte(), 0x86.toByte(), 0x0C.toByte(), 0x92.toByte(), 0x4D.toByte(), 0x51.toByte(), 0x5F.toByte(), 0xBA.toByte(), 0x54.toByte(), 0x08.toByte(), 0x35.toByte(), 0xC6.toByte(), 0xCA.toByte()
        )
        val expectedMacValue = byteArrayOf(
            0xC2.toByte(), 0x44.toByte(), 0xAE.toByte(), 0x94.toByte(), 0x5C.toByte(), 0x62.toByte(), 0x60.toByte(), 0x71.toByte(), 0x2D.toByte(), 0x9E.toByte(), 0x5B.toByte(), 0xC1.toByte(), 0x53.toByte(), 0x0D.toByte(), 0x25.toByte(), 0x6A.toByte(), 0xA9.toByte(), 0x14.toByte(), 0xD0.toByte(), 0x8E.toByte(), 0x86.toByte(), 0x0C.toByte(), 0x92.toByte(), 0x4D.toByte(), 0x51.toByte(), 0x5F.toByte(), 0xBA.toByte(), 0x54.toByte(), 0x08.toByte(), 0x35.toByte(), 0xC6.toByte(), 0xCA.toByte()
        )
        val coseMac0 = CoseMac0.Builder().decode(data).build()

        Assert.assertNotNull(coseMac0)
        Assert.assertNull(coseMac0.payload)
        Assert.assertArrayEquals(expectedMacValue, coseMac0.macValue)
    }

    @Test
    fun testCoseMac0Decode_ExampleFrom180135() {
        val data = byteArrayOf(
            0x84.toByte(), 0x41.toByte(), 0x00.toByte(), 0xA0.toByte(), 0xF6.toByte(), 0x41.toByte(), 0x00.toByte()
        )
        val coseMac0 = CoseMac0.Builder().decode(data).build()

        Assert.assertNotNull(coseMac0)
        Assert.assertNull(coseMac0.payload)
        Assert.assertArrayEquals(byteArrayOf(0x00.toByte()), coseMac0.macValue)
    }
}