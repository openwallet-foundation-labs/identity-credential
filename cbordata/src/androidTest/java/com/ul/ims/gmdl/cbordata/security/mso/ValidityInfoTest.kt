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

package com.ul.ims.gmdl.cbordata.security.mso

import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.MajorType
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.UnicodeString
import com.ul.ims.gmdl.cbordata.utils.DateUtils
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ValidityInfoTest {

    // Datetime without milliseconds
    private val signed = DateUtils.cborDecodeCalendar(
        DateUtils.getFormattedDateTime(DateUtils.getTimeOfLastUpdate()) ?: ""
    )
    private val validFrom = DateUtils.getValidityDate()
    private val validUntil = DateUtils.getDateOfExpiry()
    // Datetime without milliseconds
    private val expectedUpdate = DateUtils.cborDecodeCalendar(
        DateUtils.getFormattedDateTime(DateUtils.getTimeOfLastUpdate()) ?: ""
    )
    private lateinit var validityInfo: ValidityInfo

    @Before
    fun setUp() {
        validityInfo = ValidityInfo.Builder()
            .setSigned(signed)
            .setValidFrom(validFrom)
            .setValidUntil(validUntil)
            .setExpectedUpdate(expectedUpdate)
            .build()
    }

    @Test
    fun toDataItem() {
        assertNotNull(validityInfo)
        val dataItem = validityInfo.toDataItem()

        assertNotNull(dataItem)
        assertEquals(MajorType.MAP, dataItem.majorType)

        val map = dataItem as Map
        assertNotNull(map)

        // signed
        val signedKey = UnicodeString(ValidityInfo.SIGNED)
        assertTrue(map.keys.contains(signedKey))
        assertTdate(map.get(signedKey))

        // validFrom
        val validFromKey = UnicodeString(ValidityInfo.VALID_FROM)
        assertTrue(map.keys.contains(validFromKey))
        assertTdate(map.get(validFromKey))

        //validUntil
        val validUntil = UnicodeString(ValidityInfo.VALID_UNTIL)
        assertTrue(map.keys.contains(validUntil))
        assertTdate(map.get(validUntil))

        // expectedUpdate
        val expectedUpdate = UnicodeString(ValidityInfo.EXPECTED_UPDATE)
        assertTrue(map.keys.contains(expectedUpdate))
        assertTdate(map.get(expectedUpdate))
    }

    @Test
    fun builderTest() {

        assertNotNull(validityInfo)
        assertEquals(signed.timeInMillis, validityInfo.signed.timeInMillis)
        assertEquals(validFrom.timeInMillis, validityInfo.validFrom.timeInMillis)
        assertEquals(validUntil.timeInMillis, validityInfo.validUntil.timeInMillis)

        assertNotNull(validityInfo.expectedUpdate)
        validityInfo.expectedUpdate?.let {eu ->
            assertEquals(expectedUpdate.timeInMillis, eu.timeInMillis)
        }
    }

    private fun assertTdate(dataItem : DataItem) {
        assertEquals(MajorType.UNICODE_STRING, dataItem.majorType)
        assertEquals(0L, dataItem.tag.value)
    }

    @Test
    fun testFromEncodedDataItem() {
        val dataItem = validityInfo.toDataItem()

        val vInfo = ValidityInfo.Builder()
            .fromDataItem(dataItem)
            .build()

        assertNotNull(vInfo)
        assertEquals(signed.timeInMillis, vInfo.signed.timeInMillis)
        assertEquals(validFrom.timeInMillis, vInfo.validFrom.timeInMillis)
        assertEquals(validUntil.timeInMillis, vInfo.validUntil.timeInMillis)

        assertNotNull(vInfo.expectedUpdate)
        vInfo.expectedUpdate?.let {eu ->
            assertEquals(expectedUpdate.timeInMillis, eu.timeInMillis)
        }
    }
}