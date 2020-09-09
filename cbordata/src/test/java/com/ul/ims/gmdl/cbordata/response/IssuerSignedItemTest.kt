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

package com.ul.ims.gmdl.cbordata.response

import com.ul.ims.gmdl.cbordata.utils.CborUtils
import org.junit.Assert
import org.junit.Test

class IssuerSignedItemTest {
    private val expectedEncoded = byteArrayOf(
        0xa4.toByte(), 0x68.toByte(), 0x64.toByte(), 0x69.toByte(), 0x67.toByte(),
        0x65.toByte(), 0x73.toByte(), 0x74.toByte(), 0x49.toByte(), 0x44.toByte(),
        0x09.toByte(), 0x66.toByte(), 0x72.toByte(), 0x61.toByte(), 0x6e.toByte(),
        0x64.toByte(), 0x6f.toByte(), 0x6d.toByte(), 0x50.toByte(), 0x3c.toByte(),
        0xfc.toByte(), 0xa9.toByte(), 0xc5.toByte(), 0x19.toByte(), 0x27.toByte(),
        0x5a.toByte(), 0x3c.toByte(), 0x57.toByte(), 0x2a.toByte(), 0x8b.toByte(),
        0x45.toByte(), 0xb9.toByte(), 0x11.toByte(), 0x23.toByte(), 0x52.toByte(),
        0x71.toByte(), 0x65.toByte(), 0x6c.toByte(), 0x65.toByte(), 0x6d.toByte(),
        0x65.toByte(), 0x6e.toByte(), 0x74.toByte(), 0x49.toByte(), 0x64.toByte(),
        0x65.toByte(), 0x6e.toByte(), 0x74.toByte(), 0x69.toByte(), 0x66.toByte(),
        0x69.toByte(), 0x65.toByte(), 0x72.toByte(), 0x6b.toByte(), 0x66.toByte(),
        0x61.toByte(), 0x6d.toByte(), 0x69.toByte(), 0x6c.toByte(), 0x79.toByte(),
        0x5f.toByte(), 0x6e.toByte(), 0x61.toByte(), 0x6d.toByte(), 0x65.toByte(),
        0x6c.toByte(), 0x65.toByte(), 0x6c.toByte(), 0x65.toByte(), 0x6d.toByte(),
        0x65.toByte(), 0x6e.toByte(), 0x74.toByte(), 0x56.toByte(), 0x61.toByte(),
        0x6c.toByte(), 0x75.toByte(), 0x65.toByte(), 0x63.toByte(), 0x44.toByte(),
        0x6f.toByte(), 0x65.toByte()
    )

    @Test
    fun testBuilder() {
        val issuerSignedItem = IssuerSignedItem.Builder().decode(expectedEncoded).build()

        Assert.assertTrue(issuerSignedItem.digestId == 9)
        Assert.assertTrue(CborUtils.encodeToString(issuerSignedItem.randomValue) == "3cfca9c519275a3c572a8b45b9112352")
        Assert.assertTrue(issuerSignedItem.elementIdentifier == "family_name")
        Assert.assertTrue(issuerSignedItem.elementValue == "Doe")
    }

    //LastNameIssuerSignedItem:
    private val issuerSignedItemLastNameData =  byteArrayOf(
        0xa4.toByte(), 0x68.toByte(), 0x64.toByte(), 0x69.toByte(), 0x67.toByte(),
        0x65.toByte(), 0x73.toByte(), 0x74.toByte(), 0x49.toByte(), 0x44.toByte(),
        0x00.toByte(), 0x66.toByte(), 0x72.toByte(), 0x61.toByte(), 0x6e.toByte(),
        0x64.toByte(), 0x6f.toByte(), 0x6d.toByte(), 0x50.toByte(), 0xac.toByte(),
        0x15.toByte(), 0x4d.toByte(), 0x37.toByte(), 0xa6.toByte(), 0xce.toByte(),
        0x5a.toByte(), 0x38.toByte(), 0x16.toByte(), 0xe2.toByte(), 0xd7.toByte(),
        0xc3.toByte(), 0x2f.toByte(), 0x8c.toByte(), 0x29.toByte(), 0x78.toByte(),
        0x71.toByte(), 0x65.toByte(), 0x6c.toByte(), 0x65.toByte(), 0x6d.toByte(),
        0x65.toByte(), 0x6e.toByte(), 0x74.toByte(), 0x49.toByte(), 0x64.toByte(),
        0x65.toByte(), 0x6e.toByte(), 0x74.toByte(), 0x69.toByte(), 0x66.toByte(),
        0x69.toByte(), 0x65.toByte(), 0x72.toByte(), 0x6b.toByte(), 0x66.toByte(),
        0x61.toByte(), 0x6d.toByte(), 0x69.toByte(), 0x6c.toByte(), 0x79.toByte(),
        0x5f.toByte(), 0x6e.toByte(), 0x61.toByte(), 0x6d.toByte(), 0x65.toByte(),
        0x6c.toByte(), 0x65.toByte(), 0x6c.toByte(), 0x65.toByte(), 0x6d.toByte(),
        0x65.toByte(), 0x6e.toByte(), 0x74.toByte(), 0x56.toByte(), 0x61.toByte(),
        0x6c.toByte(), 0x75.toByte(), 0x65.toByte(), 0x63.toByte(), 0x44.toByte(),
        0x6f.toByte(), 0x65.toByte()
    )
    private val expectedLastNameDigestId = 0
    private val expectedLastNameRandom = byteArrayOf(
        0xac.toByte(), 0x15.toByte(), 0x4d.toByte(), 0x37.toByte(), 0xa6.toByte(),
        0xce.toByte(), 0x5a.toByte(), 0x38.toByte(), 0x16.toByte(), 0xe2.toByte(),
        0xd7.toByte(), 0xc3.toByte(), 0x2f.toByte(), 0x8c.toByte(), 0x29.toByte(),
        0x78
    )
    private val expectedLastNameElementId = "family_name"
    private val expectedFamilyName = "Doe"

    @Test
    fun testLastNameIssuerSignedItemDecode() {

        val isi = IssuerSignedItem.Builder().decode(issuerSignedItemLastNameData).build()

        Assert.assertTrue(isi.digestId == expectedLastNameDigestId)
        Assert.assertArrayEquals(expectedLastNameRandom, isi.randomValue)
        Assert.assertTrue(isi.elementIdentifier == expectedLastNameElementId)
        Assert.assertTrue(isi.elementValue == expectedFamilyName)
    }

    @Test
    fun testLastNameISIEncode() {
        val isiBuilder = IssuerSignedItem.Builder()
        isiBuilder.setDigestId(expectedLastNameDigestId)
        isiBuilder.setRandomValue(expectedLastNameRandom)
        isiBuilder.setElementIdentifier(expectedLastNameElementId)
        isiBuilder.setElementValue(expectedFamilyName)
        val isi = isiBuilder.build()

        val isiDecode = IssuerSignedItem.Builder().decode(issuerSignedItemLastNameData).build()

        Assert.assertTrue(isiDecode.digestId == isi.digestId)
        Assert.assertArrayEquals(isi.randomValue, isiDecode.randomValue)
        Assert.assertTrue(isiDecode.elementIdentifier == isi.elementIdentifier)
        Assert.assertTrue(isiDecode.elementValue == isi.elementValue)
    }
}