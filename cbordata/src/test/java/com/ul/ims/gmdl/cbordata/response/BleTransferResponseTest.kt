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

import org.junit.Assert
import org.junit.Test

class BleTransferResponseTest {
    private val errorResponse = 19

    private val dataItem = "NameAuth"
    private val errorCode = 1
    private val errorItem = ErrorItem(dataItem, errorCode)

    private val issuerSignedItem = IssuerSignedItem.Builder()
        .setDigestId(1)
        .setRandomValue(byteArrayOf(0x01))
        .setElementIdentifier("element1")
        .setElementValue("value1")
        .build()

    @Test
    fun builderTest() {
        val bleTransferResponse = BleTransferResponse.Builder()
                .setErrorItems(arrayOf(errorItem))
            .setResponseStatus(errorResponse)
                .setIssuerSignedItems(arrayOf(issuerSignedItem))
                .build()

        Assert.assertNotNull(bleTransferResponse)
        Assert.assertEquals(errorResponse, bleTransferResponse.responseStatus)
        Assert.assertEquals(issuerSignedItem, bleTransferResponse.arrIssuerSignedItem?.get(0))
        Assert.assertEquals(errorItem, bleTransferResponse.arrErrorItem?.get(0))
    }
}