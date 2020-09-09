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

import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.UnicodeString
import com.ul.ims.gmdl.cbordata.security.CoseSign1
import com.ul.ims.gmdl.cbordata.security.CoseSign1Tests
import com.ul.ims.gmdl.cbordata.security.mdlauthentication.CoseMac0
import org.junit.Assert
import org.junit.Test

class DeviceAuthTest {

    private val coseMac0Data = byteArrayOf(
        0x83.toByte(),
        0x43.toByte(),
        0xa1.toByte(),
        0x01.toByte(),
        0x05.toByte(),
        0xf6.toByte(),
        0x42.toByte(),
        0x01.toByte(),
        0x02.toByte()
    )
    private val coseMac0 = CoseMac0.Builder().decodeEncoded(coseMac0Data).build()
    private val coseSign1 = CoseSign1.Builder().decode(CoseSign1Tests().coseSign1Data).build()

    @Test
    fun builderTest() {
        val builder = DeviceAuth.Builder()
        builder.setCoseMac0(coseMac0)
        builder.setCoseSign1(coseSign1)

        val deviceAuth = builder.build()

        Assert.assertNotNull(deviceAuth)
        Assert.assertEquals(coseMac0, deviceAuth.deviceMac)
    }

    @Test
    fun decodeTest() {
        var map = Map()
        map = map.put(UnicodeString(KEY_DEVICE_SIGNATURE), coseSign1.addToNestedStructure())

        val builder = DeviceAuth.Builder()
        builder.decode(map)

        val deviceAuth = builder.build()

        Assert.assertNotNull(deviceAuth)
        Assert.assertNotNull(deviceAuth.deviceSignature)
        Assert.assertNull(deviceAuth.deviceMac)
    }

    companion object {
        private const val KEY_DEVICE_SIGNATURE = "deviceSignature"
    }
}