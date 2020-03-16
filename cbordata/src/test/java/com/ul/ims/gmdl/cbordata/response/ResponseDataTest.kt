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
import com.ul.ims.gmdl.cbordata.security.mdlauthentication.CoseMac0
import com.ul.ims.gmdl.cbordata.security.mdlauthentication.DeviceNameSpaces
import org.junit.Assert
import org.junit.Test

class ResponseDataTest {

    private val namespace = "com.rdw.nl"
    private val issuerSignedItem1 = IssuerSignedItem.Builder()
            .setDigestId(1)
            .setRandomValue(byteArrayOf(0x01))
            .setElementIdentifier("NameAuth")
            .setElementValue("value1")
            .build()

    private val issuerSignedItem2 = IssuerSignedItem.Builder()
            .setDigestId(2)
            .setRandomValue(byteArrayOf(0x02))
            .setElementIdentifier("DateReg")
            .setElementValue("value2")
            .build()
    private val coseSign1 = CoseSign1.Builder().decode(CoseSign1Tests().coseSign1Data).build()
    private val issuerSigned = IssuerSigned.Builder()
        .setNameSpaces(namespace, arrayOf(issuerSignedItem1, issuerSignedItem2))
        .setIssuerAuth(coseSign1)
        .build()

    private val coseMac0Data = byteArrayOf(
        0x83.toByte(), 0x43.toByte(), 0xa1.toByte(), 0x01.toByte(), 0x05.toByte(), 0xf6.toByte(), 0x42.toByte(), 0x01.toByte(), 0x02.toByte()
    )
    private val coseMac0 = CoseMac0.Builder().decode(coseMac0Data).build()

    private val deviceAuth = DeviceAuth.Builder()
        .setCoseMac0(coseMac0)
        .setCoseSign1(coseSign1)
        .build()
    private val deviceSigned = DeviceSigned.Builder()
            .setDeviceAuth(deviceAuth)
        .setDeviceNameSpaces(DeviceNameSpaces.Builder().build())
            .build()

    private val errorItem1 = ErrorItem("NameAuth", 1)
    private val errorItem2 = ErrorItem("DateReg", 2)

    private val errors = Errors.Builder()
            .setError(namespace, arrayOf(errorItem1, errorItem2))
            .build()

    @Test
    fun builderTest() {
        var responseData = ResponseData.Builder()
                .setIssuerSigned(issuerSigned)
                .setDeviceSigned(deviceSigned)
                .build()

        Assert.assertNotNull(responseData)
        Assert.assertEquals(issuerSigned, responseData?.issuerSigned)
        Assert.assertEquals(deviceSigned, responseData?.deviceSigned)

        responseData = ResponseData.Builder()
                .setIssuerSigned(issuerSigned)
                .setDeviceSigned(deviceSigned)
                .setErrors(errors)
                .build()

        Assert.assertNotNull(responseData)
        Assert.assertEquals(errors, responseData?.erros)

        responseData = ResponseData.Builder()
                .build()

        Assert.assertNull(responseData)
    }
}