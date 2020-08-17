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

package com.ul.ims.gmdl.cbordata.deviceEngagement.security

import co.nstant.`in`.cbor.model.*
import co.nstant.`in`.cbor.model.Array
import com.ul.ims.gmdl.cbordata.security.CoseKey
import org.junit.Assert
import org.junit.Test

class SecurityTest {
    private val coseKey = CoseKey.Builder()
        .setKeyType(2)
        .setCurve(
            1
            ,
            byteArrayOf(
                26,
                -46,
                113,
                -66,
                -115,
                -97,
                11,
                31,
                -103,
                32,
                55,
                42,
                102,
                32,
                -107,
                -119,
                5,
                -45,
                -51,
                -107,
                -126,
                -62,
                64,
                -108,
                59,
                -112,
                -96,
                -9,
                95,
                -59,
                -31,
                7
            )
            ,
            byteArrayOf(
                -73,
                -29,
                123,
                -76,
                9,
                -126,
                -118,
                -114,
                62,
                -118,
                72,
                -103,
                54,
                10,
                123,
                -3,
                -45,
                -87,
                -86,
                72,
                91,
                -27,
                13,
                -16,
                57,
                74,
                73,
                71,
                56,
                62,
                -80,
                -30
            )
            ,
            null
        )
        .build()
    private val cipherIdent = 123

    //cose key values
    private val keyOpsValue1 = "keyops1"
    private val keyOpsValue2 = "keyops2"
    val kty = "kyy"
    val kid = byteArrayOf(0x02, 0x03)
    val alg = "alg"
    val baseIv = byteArrayOf(0x00, 0x01)

    @Test
    fun testBuilder() {
        val builder = Security.Builder()

        Assert.assertNotNull(builder)

        val security = builder.setCoseKey(coseKey)
            .setCipherSuiteIdent(cipherIdent)
            .build()

        Assert.assertNotNull(security)
        Assert.assertEquals(cipherIdent, security.cipherIdent)
        Assert.assertEquals(coseKey, security.coseKey)
    }

    @Test
    fun testIsValid() {
        var security =  Security.Builder()
            .setCoseKey(coseKey)
            .setCipherSuiteIdent(cipherIdent)
            .build()

        Assert.assertNotNull(security)
        Assert.assertTrue(security.isValid())

        security =  Security.Builder()
            .setCipherSuiteIdent(cipherIdent)
            .build()

        Assert.assertNotNull(security)
        Assert.assertFalse(security.isValid())
    }

    @Test
    fun fromCborStructureTest() {
        val keyOpsArr = Array()
        keyOpsArr.add(UnicodeString(keyOpsValue1))
        keyOpsArr.add(UnicodeString(keyOpsValue2))

        val dataItem = ByteString(coseKey.encode())
        dataItem.tag = Tag(24)

        val cborStructure = Array()
        cborStructure.add(UnsignedInteger(cipherIdent.toLong()))
        cborStructure.add(dataItem)

        val security = Security.Builder()
            .fromCborStructure(cborStructure)
            .build()

        Assert.assertNotNull(security)

        val coseKey = CoseKey.Builder()
            .decode(dataItem.bytes)
            .build()

        Assert.assertEquals(coseKey, security.coseKey)
        Assert.assertEquals(cipherIdent, security.cipherIdent)
        Assert.assertTrue(security.isValid())
    }

    @Test
    fun equalsTest() {
        val security =  Security.Builder()
            .setCoseKey(coseKey)
            .setCipherSuiteIdent(cipherIdent)
            .build()

        val security1 =  Security.Builder()
            .setCoseKey(coseKey)
            .setCipherSuiteIdent(cipherIdent)
            .build()

        Assert.assertTrue(security == security1)

        val security2 =  Security.Builder()
            .setCipherSuiteIdent(cipherIdent)
            .build()

        Assert.assertFalse(security1 == security2)
    }

    //TODO: Finish Unit Test Implementation
    fun appendToCborStructureTest(){}
}