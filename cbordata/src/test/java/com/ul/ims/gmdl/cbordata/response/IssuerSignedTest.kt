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

import com.ul.ims.gmdl.cbordata.security.CoseSign1
import com.ul.ims.gmdl.cbordata.security.CoseSign1Tests
import org.junit.Assert
import org.junit.Test

class IssuerSignedTest {
    private val namespace = "com.rdw.nl"
    private val issuerSignedItem1 = IssuerSignedItem.Builder()
            .setDigestId(1)
            .setRandomValue(byteArrayOf(0x01))
            .setElementIdentifier("element1")
            .setElementValue("value1")
            .build()

    private val coseSign1 = CoseSign1.Builder().decode(CoseSign1Tests().coseSign1Data).build()
    private val issuerSignedItem2 = IssuerSignedItem.Builder()
            .setDigestId(2)
            .setRandomValue(byteArrayOf(0x02))
            .setElementIdentifier("element2")
            .setElementValue("value2")
            .build()

    @Test
    fun builderTest() {
        val issuerSigned = IssuerSigned.Builder()
            .setNameSpaces(namespace, arrayOf(issuerSignedItem1, issuerSignedItem2))
            .setIssuerAuth(coseSign1)
            .build()

        Assert.assertNotNull(issuerSigned)
        Assert.assertNotNull(issuerSigned.issuerAuth)
        Assert.assertTrue(1 == issuerSigned.nameSpaces?.keys?.size)
        Assert.assertTrue(issuerSigned.nameSpaces?.containsKey(namespace) == true)
        Assert.assertEquals(issuerSignedItem1, issuerSigned.nameSpaces?.get(namespace)?.get(0))
        Assert.assertEquals(issuerSignedItem2, issuerSigned.nameSpaces?.get(namespace)?.get(1))
    }
}