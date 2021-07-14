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

package com.ul.ims.gmdl.cbordata.deviceEngagement

import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement.Companion.OPTIONS_COMPACT_KEY
import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement.Companion.OPTIONS_OIDC_KEY
import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement.Companion.OPTIONS_WEBAPI_KEY
import com.ul.ims.gmdl.cbordata.deviceEngagement.options.Oidc
import com.ul.ims.gmdl.cbordata.deviceEngagement.options.WebAPI
import com.ul.ims.gmdl.cbordata.deviceEngagement.security.Security
import com.ul.ims.gmdl.cbordata.deviceEngagement.transferMethods.BleDeviceRetrievalMethod
import com.ul.ims.gmdl.cbordata.security.CoseKey
import org.junit.Assert
import org.junit.Test
import java.util.*

class DeviceEngagementTest {

    val version = "1.0"

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

    val security = Security.Builder()
        .setCoseKey(coseKey)
        .setCipherSuiteIdent(CHIPER_SUITE_IDENT)
        .build()

    private val token = "abcdabcd"
    private val webAPI = WebAPI.Builder()
        .setVersion(1)
        .setToken(token)
        .setBaseUrl("trident.quarin.net/MVR-Backend.Api")
        .build()

    private val oidc = Oidc.Builder()
        .setVersion(2)
        .setUrl("oidc-url")
        .setToken("oidc-token")
        .build()

    private val options: Map<String, Any>? = mutableMapOf(
        OPTIONS_WEBAPI_KEY to webAPI,
        OPTIONS_OIDC_KEY to oidc,
        OPTIONS_COMPACT_KEY to false
    )
    private val proprietary = mapOf(
        PROP_MAP_KEY_1 to OPTIONS_MAP_VALUE_5,
        PROP_MAP_KEY_2 to OPTIONS_MAP_VALUE_3,
        PROP_MAP_KEY_3 to OPTIONS_MAP_VALUE_1
    )

    private val bleTransferMethod = BleDeviceRetrievalMethod(
        2,
        1,
        BleDeviceRetrievalMethod.BleOptions(
            peripheralServer = false,
            centralClient = true,
            peripheralServerUUID = null,
            centralClientUUID = UUID.randomUUID(),
            mac = null
        )
    )

    private val encodedExpected = byteArrayOf(
        0xa5.toByte(), 0x00.toByte(), 0x63.toByte(), 0x31.toByte(), 0x2e.toByte(),
        0x30.toByte(), 0x01.toByte(), 0x82.toByte(), 0x01.toByte(), 0xd8.toByte(),
        0x18.toByte(), 0x58.toByte(), 0x4b.toByte(), 0xa4.toByte(), 0x01.toByte(),
        0x02.toByte(), 0x20.toByte(), 0x01.toByte(), 0x21.toByte(), 0x58.toByte(),
        0x20.toByte(), 0x1a.toByte(), 0xd2.toByte(), 0x71.toByte(), 0xbe.toByte(),
        0x8d.toByte(), 0x9f.toByte(), 0x0b.toByte(), 0x1f.toByte(), 0x99.toByte(),
        0x20.toByte(), 0x37.toByte(), 0x2a.toByte(), 0x66.toByte(), 0x20.toByte(),
        0x95.toByte(), 0x89.toByte(), 0x05.toByte(), 0xd3.toByte(), 0xcd.toByte(),
        0x95.toByte(), 0x82.toByte(), 0xc2.toByte(), 0x40.toByte(), 0x94.toByte(),
        0x3b.toByte(), 0x90.toByte(), 0xa0.toByte(), 0xf7.toByte(), 0x5f.toByte(),
        0xc5.toByte(), 0xe1.toByte(), 0x07.toByte(), 0x22.toByte(), 0x58.toByte(),
        0x20.toByte(), 0xb7.toByte(), 0xe3.toByte(), 0x7b.toByte(), 0xb4.toByte(),
        0x09.toByte(), 0x82.toByte(), 0x8a.toByte(), 0x8e.toByte(), 0x3e.toByte(),
        0x8a.toByte(), 0x48.toByte(), 0x99.toByte(), 0x36.toByte(), 0x0a.toByte(),
        0x7b.toByte(), 0xfd.toByte(), 0xd3.toByte(), 0xa9.toByte(), 0xaa.toByte(),
        0x48.toByte(), 0x5b.toByte(), 0xe5.toByte(), 0x0d.toByte(), 0xf0.toByte(),
        0x39.toByte(), 0x4a.toByte(), 0x49.toByte(), 0x47.toByte(), 0x38.toByte(),
        0x3e.toByte(), 0xb0.toByte(), 0xe2.toByte(), 0x02.toByte(), 0x81.toByte(),
        0x83.toByte(), 0x02.toByte(), 0x01.toByte(), 0xa3.toByte(), 0x00.toByte(),
        0xf4.toByte(), 0x01.toByte(), 0xf5.toByte(), 0x0b.toByte(), 0x78.toByte(),
        0x24.toByte(), 0x66.toByte(), 0x34.toByte(), 0x39.toByte(), 0x64.toByte(),
        0x62.toByte(), 0x33.toByte(), 0x30.toByte(), 0x64.toByte(), 0x2d.toByte(),
        0x35.toByte(), 0x66.toByte(), 0x66.toByte(), 0x63.toByte(), 0x2d.toByte(),
        0x34.toByte(), 0x64.toByte(), 0x36.toByte(), 0x66.toByte(), 0x2d.toByte(),
        0x39.toByte(), 0x37.toByte(), 0x31.toByte(), 0x66.toByte(), 0x2d.toByte(),
        0x63.toByte(), 0x37.toByte(), 0x31.toByte(), 0x64.toByte(), 0x34.toByte(),
        0x66.toByte(), 0x34.toByte(), 0x66.toByte(), 0x66.toByte(), 0x62.toByte(),
        0x62.toByte(), 0x34.toByte(), 0x03.toByte(), 0xa3.toByte(), 0x64.toByte(),
        0x4f.toByte(), 0x49.toByte(), 0x44.toByte(), 0x43.toByte(), 0x83.toByte(),
        0x02.toByte(), 0x68.toByte(), 0x6f.toByte(), 0x69.toByte(), 0x64.toByte(),
        0x63.toByte(), 0x2d.toByte(), 0x75.toByte(), 0x72.toByte(), 0x6c.toByte(),
        0x6a.toByte(), 0x6f.toByte(), 0x69.toByte(), 0x64.toByte(), 0x63.toByte(),
        0x2d.toByte(), 0x74.toByte(), 0x6f.toByte(), 0x6b.toByte(), 0x65.toByte(),
        0x6e.toByte(), 0x66.toByte(), 0x57.toByte(), 0x65.toByte(), 0x62.toByte(),
        0x41.toByte(), 0x50.toByte(), 0x49.toByte(), 0x83.toByte(), 0x01.toByte(),
        0x78.toByte(), 0x22.toByte(), 0x74.toByte(), 0x72.toByte(), 0x69.toByte(),
        0x64.toByte(), 0x65.toByte(), 0x6e.toByte(), 0x74.toByte(), 0x2e.toByte(),
        0x71.toByte(), 0x75.toByte(), 0x61.toByte(), 0x72.toByte(), 0x69.toByte(),
        0x6e.toByte(), 0x2e.toByte(), 0x6e.toByte(), 0x65.toByte(), 0x74.toByte(),
        0x2f.toByte(), 0x4d.toByte(), 0x56.toByte(), 0x52.toByte(), 0x2d.toByte(),
        0x42.toByte(), 0x61.toByte(), 0x63.toByte(), 0x6b.toByte(), 0x65.toByte(),
        0x6e.toByte(), 0x64.toByte(), 0x2e.toByte(), 0x41.toByte(), 0x70.toByte(),
        0x69.toByte(), 0x68.toByte(), 0x61.toByte(), 0x62.toByte(), 0x63.toByte(),
        0x64.toByte(), 0x61.toByte(), 0x62.toByte(), 0x63.toByte(), 0x64.toByte(),
        0x67.toByte(), 0x63.toByte(), 0x6f.toByte(), 0x6d.toByte(), 0x70.toByte(),
        0x61.toByte(), 0x63.toByte(), 0x74.toByte(), 0xf4.toByte(), 0x05.toByte(),
        0xa3.toByte(), 0x66.toByte(), 0x70.toByte(), 0x72.toByte(), 0x6f.toByte(),
        0x70.toByte(), 0x5f.toByte(), 0x31.toByte(), 0x67.toByte(), 0x76.toByte(),
        0x61.toByte(), 0x6c.toByte(), 0x75.toByte(), 0x65.toByte(), 0x5f.toByte(),
        0x35.toByte(), 0x66.toByte(), 0x70.toByte(), 0x72.toByte(), 0x6f.toByte(),
        0x70.toByte(), 0x5f.toByte(), 0x32.toByte(), 0x67.toByte(), 0x76.toByte(),
        0x61.toByte(), 0x6c.toByte(), 0x75.toByte(), 0x65.toByte(), 0x5f.toByte(),
        0x33.toByte(), 0x66.toByte(), 0x70.toByte(), 0x72.toByte(), 0x6f.toByte(),
        0x70.toByte(), 0x5f.toByte(), 0x33.toByte(), 0x67.toByte(), 0x76.toByte(),
        0x61.toByte(), 0x6c.toByte(), 0x75.toByte(), 0x65.toByte(), 0x5f.toByte(),
        0x31.toByte()
    )

    @Test
    fun testBuilder() {
        val builder = DeviceEngagement.Builder()
        builder.version(version)
        builder.security(security)
        builder.transferMethods(bleTransferMethod)
        builder.options(options)
        builder.proprietary(proprietary)

        val deviceEngagement = builder.build()
        Assert.assertNotNull(deviceEngagement)
        Assert.assertEquals(version, deviceEngagement.version)
        Assert.assertEquals(security, deviceEngagement.security)
        Assert.assertEquals(1, deviceEngagement.deviceRetrievalMethod?.size)
        Assert.assertEquals(options?.size, deviceEngagement.options?.size)
        Assert.assertEquals(proprietary.size, deviceEngagement.proprietary?.size)
    }

    @Test
    fun testEncodeDecode() {
        val builder = DeviceEngagement.Builder()
        builder.version(version)
        builder.security(security)
        builder.transferMethods(bleTransferMethod)
        builder.options(options)
        builder.proprietary(proprietary)

        val deviceEngagement = builder.build()
        val encoded = deviceEngagement.encode()

        Assert.assertNotNull(encoded)

        val decoded = DeviceEngagement.Builder()
                .decode(encoded).build()

        Assert.assertNotNull(decoded)
        Assert.assertEquals(version, decoded.version)
        Assert.assertEquals(security.coseKey?.curve?.id, decoded.security?.coseKey?.curve?.id)
        Assert.assertEquals(
            security.coseKey?.curve?.xCoordinate?.contentToString(),
            decoded.security?.coseKey?.curve?.xCoordinate?.contentToString()
        )
        Assert.assertEquals(
            security.coseKey?.curve?.yCoordinate?.contentToString(),
            decoded.security?.coseKey?.curve?.yCoordinate?.contentToString()
        )
        Assert.assertEquals(
            security.coseKey?.curve?.privateKey,
            decoded.security?.coseKey?.curve?.privateKey
        )
        Assert.assertEquals(security.cipherIdent, decoded.security?.cipherIdent)
        Assert.assertEquals(1, decoded.deviceRetrievalMethod?.size)
        Assert.assertEquals(options?.size, decoded.options?.size)
        Assert.assertEquals(proprietary.size, decoded.proprietary?.size)
    }

    @Test
    fun helperFunctionsTest() {
        var builder = DeviceEngagement.Builder()
        builder.version(version)
        builder.security(security)
        builder.transferMethods(bleTransferMethod)
        builder.options(options)
        builder.proprietary(proprietary)

        var deviceEngagement = builder.build()

        Assert.assertNotNull(deviceEngagement)
        Assert.assertEquals(token, deviceEngagement.getToken())
        Assert.assertNotNull(deviceEngagement.getBLETransferMethod())

        builder = DeviceEngagement.Builder()
        builder.version(version)
        builder.security(security)
        builder.proprietary(proprietary)

        deviceEngagement = builder.build()
        Assert.assertNotNull(deviceEngagement)
        Assert.assertNull(deviceEngagement.getToken())
        Assert.assertNull(deviceEngagement.getBLETransferMethod())
    }

    @Test
    fun isValidTest() {
        var builder = DeviceEngagement.Builder()
        builder.version(version)
        builder.security(security)
        builder.transferMethods(bleTransferMethod)
        builder.options(options)

        var deviceEngagement = builder.build()
        Assert.assertNotNull(deviceEngagement)
        Assert.assertTrue(deviceEngagement.isValid())

        builder = DeviceEngagement.Builder()
        builder.version(version)
        builder.transferMethods(bleTransferMethod)
        builder.options(options)

        deviceEngagement = builder.build()
        Assert.assertFalse(deviceEngagement.isValid())
    }

    // TODO: Implement Unit Tests bellow
    fun getNfcTransferMethodTest() {}
    fun getWiFiAwareTransferMethod() {}
    fun equalsTest() {}
    fun hashCodeTest() {}
    fun decodeFromBase64Test() {}

    companion object {
        private const val OPTIONS_MAP_VALUE_1 = "value_1"
        private const val OPTIONS_MAP_VALUE_3 = "value_3"
        private const val OPTIONS_MAP_VALUE_5 = "value_5"
        private const val PROP_MAP_KEY_1 = "prop_1"
        private const val PROP_MAP_KEY_2 = "prop_2"
        private const val PROP_MAP_KEY_3 = "prop_3"
        private const val CHIPER_SUITE_IDENT = 1
    }
}