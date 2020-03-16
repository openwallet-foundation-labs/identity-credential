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

package com.ul.ims.gmdl.security.security.mdlAuthentication

import com.ul.ims.gmdl.cbordata.cryptoUtils.CryptoUtils
import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement
import com.ul.ims.gmdl.cbordata.doctype.MdlDoctype
import com.ul.ims.gmdl.cbordata.response.DeviceAuth
import com.ul.ims.gmdl.cbordata.response.DeviceSigned
import com.ul.ims.gmdl.cbordata.security.CoseKey
import com.ul.ims.gmdl.cbordata.security.mdlauthentication.DeviceNameSpaces
import com.ul.ims.gmdl.security.mdlauthentication.MdlAuthenticationException
import com.ul.ims.gmdl.security.mdlauthentication.MdlAuthenticator
import org.junit.Assert
import org.junit.Test
import java.security.PrivateKey
import kotlin.test.assertFailsWith

class MdlAuthenticatorTests {

    private val deviceEngagementData = byteArrayOf(
        0x85.toByte(), 0x63.toByte(), 0x31.toByte(), 0x2e.toByte(), 0x30.toByte(),
        0x82.toByte(), 0x01.toByte(), 0xd8.toByte(), 0x18.toByte(), 0x58.toByte(),
        0x4b.toByte(), 0xa4.toByte(), 0x01.toByte(), 0x02.toByte(), 0x20.toByte(),
        0x01.toByte(), 0x21.toByte(), 0x58.toByte(), 0x20.toByte(), 0x9c.toByte(),
        0xf4.toByte(), 0x84.toByte(), 0x5a.toByte(), 0x71.toByte(), 0x6f.toByte(),
        0xed.toByte(), 0xce.toByte(), 0x91.toByte(), 0x8d.toByte(), 0xe2.toByte(),
        0x44.toByte(), 0x70.toByte(), 0xa2.toByte(), 0xc8.toByte(), 0x6c.toByte(),
        0xd5.toByte(), 0x4d.toByte(), 0x83.toByte(), 0x11.toByte(), 0x75.toByte(),
        0x48.toByte(), 0xb5.toByte(), 0xe3.toByte(), 0x7a.toByte(), 0x35.toByte(),
        0x97.toByte(), 0xa7.toByte(), 0x91.toByte(), 0xde.toByte(), 0x01.toByte(),
        0x45.toByte(), 0x22.toByte(), 0x58.toByte(), 0x20.toByte(), 0xa1.toByte(),
        0x62.toByte(), 0xcc.toByte(), 0x0f.toByte(), 0xe7.toByte(), 0xdd.toByte(),
        0xa8.toByte(), 0xb4.toByte(), 0x6c.toByte(), 0xf1.toByte(), 0xab.toByte(),
        0x3a.toByte(), 0x56.toByte(), 0xa2.toByte(), 0x51.toByte(), 0xc8.toByte(),
        0xaf.toByte(), 0x23.toByte(), 0x0b.toByte(), 0xcd.toByte(), 0x33.toByte(),
        0x30.toByte(), 0x92.toByte(), 0x06.toByte(), 0xe4.toByte(), 0xa3.toByte(),
        0x79.toByte(), 0x53.toByte(), 0x07.toByte(), 0xcb.toByte(), 0xee.toByte(),
        0x51.toByte(), 0x81.toByte(), 0x83.toByte(), 0x03.toByte(), 0x01.toByte(),
        0xa0.toByte(), 0xa0.toByte(), 0x80.toByte()
    )

    private val deviceEngagementDataWrong = byteArrayOf(
        0x85.toByte(), 0x63.toByte(), 0x31.toByte(), 0x2e.toByte(), 0x31.toByte(),
        0x82.toByte(), 0x01.toByte(), 0xd8.toByte(), 0x18.toByte(), 0x58.toByte(),
        0x4b.toByte(), 0xa4.toByte(), 0x01.toByte(), 0x02.toByte(), 0x20.toByte(),
        0x01.toByte(), 0x21.toByte(), 0x58.toByte(), 0x20.toByte(), 0x9c.toByte(),
        0xf4.toByte(), 0x84.toByte(), 0x5a.toByte(), 0x71.toByte(), 0x6f.toByte(),
        0xed.toByte(), 0xce.toByte(), 0x91.toByte(), 0x8d.toByte(), 0xe2.toByte(),
        0x44.toByte(), 0x70.toByte(), 0xa2.toByte(), 0xc8.toByte(), 0x6c.toByte(),
        0xd5.toByte(), 0x4d.toByte(), 0x83.toByte(), 0x11.toByte(), 0x75.toByte(),
        0x48.toByte(), 0xb5.toByte(), 0xe3.toByte(), 0x7a.toByte(), 0x35.toByte(),
        0x97.toByte(), 0xa7.toByte(), 0x91.toByte(), 0xde.toByte(), 0x01.toByte(),
        0x45.toByte(), 0x22.toByte(), 0x58.toByte(), 0x20.toByte(), 0xa1.toByte(),
        0x62.toByte(), 0xcc.toByte(), 0x0f.toByte(), 0xe7.toByte(), 0xdd.toByte(),
        0xa8.toByte(), 0xb4.toByte(), 0x6c.toByte(), 0xf1.toByte(), 0xab.toByte(),
        0x3a.toByte(), 0x56.toByte(), 0xa2.toByte(), 0x51.toByte(), 0xc8.toByte(),
        0xaf.toByte(), 0x23.toByte(), 0x0b.toByte(), 0xcd.toByte(), 0x33.toByte(),
        0x30.toByte(), 0x92.toByte(), 0x06.toByte(), 0xe4.toByte(), 0xa3.toByte(),
        0x79.toByte(), 0x53.toByte(), 0x07.toByte(), 0xcb.toByte(), 0xee.toByte(),
        0x51.toByte(), 0x81.toByte(), 0x83.toByte(), 0x03.toByte(), 0x01.toByte(),
        0xa0.toByte(), 0xa0.toByte(), 0x80.toByte()
    )

    private val readerKeyData = byteArrayOf(
        0xa4.toByte(), 0x01.toByte(), 0x02.toByte(), 0x20.toByte(), 0x01.toByte(),
        0x21.toByte(), 0x58.toByte(), 0x20.toByte(), 0x5d.toByte(), 0xe2.toByte(),
        0x95.toByte(), 0x54.toByte(), 0xf7.toByte(), 0xe1.toByte(), 0x70.toByte(),
        0xcd.toByte(), 0x67.toByte(), 0xe3.toByte(), 0xa9.toByte(), 0x52.toByte(),
        0x50.toByte(), 0xa3.toByte(), 0x71.toByte(), 0x9f.toByte(), 0x5e.toByte(),
        0xd1.toByte(), 0xb9.toByte(), 0xd5.toByte(), 0x77.toByte(), 0x04.toByte(),
        0x29.toByte(), 0x79.toByte(), 0x0c.toByte(), 0x00.toByte(), 0xa7.toByte(),
        0x09.toByte(), 0x8a.toByte(), 0x1f.toByte(), 0x2b.toByte(), 0xee.toByte(),
        0x22.toByte(), 0x58.toByte(), 0x20.toByte(), 0x09.toByte(), 0xeb.toByte(),
        0x22.toByte(), 0xe6.toByte(), 0x1e.toByte(), 0xfc.toByte(), 0x58.toByte(),
        0x12.toByte(), 0xd0.toByte(), 0xeb.toByte(), 0x6c.toByte(), 0x0e.toByte(),
        0x86.toByte(), 0x05.toByte(), 0xec.toByte(), 0x3d.toByte(), 0x5e.toByte(),
        0x68.toByte(), 0x2e.toByte(), 0x1f.toByte(), 0xcd.toByte(), 0x3b.toByte(),
        0x0a.toByte(), 0xb4.toByte(), 0x92.toByte(), 0x6d.toByte(), 0x8a.toByte(),
        0xc2.toByte(), 0x36.toByte(), 0xff.toByte(), 0x29.toByte(), 0xb8.toByte()
    )

    private val deviceSignedData = byteArrayOf(
        0xa2.toByte(), 0x6a.toByte(), 0x6e.toByte(), 0x61.toByte(), 0x6d.toByte(),
        0x65.toByte(), 0x53.toByte(), 0x70.toByte(), 0x61.toByte(), 0x63.toByte(),
        0x65.toByte(), 0x73.toByte(), 0xd8.toByte(), 0x18.toByte(), 0x41.toByte(),
        0xa0.toByte(), 0x6a.toByte(), 0x64.toByte(), 0x65.toByte(), 0x76.toByte(),
        0x69.toByte(), 0x63.toByte(), 0x65.toByte(), 0x41.toByte(), 0x75.toByte(),
        0x74.toByte(), 0x68.toByte(), 0xa1.toByte(), 0x6f.toByte(), 0x64.toByte(),
        0x65.toByte(), 0x76.toByte(), 0x69.toByte(), 0x63.toByte(), 0x65.toByte(),
        0x53.toByte(), 0x69.toByte(), 0x67.toByte(), 0x6e.toByte(), 0x61.toByte(),
        0x74.toByte(), 0x75.toByte(), 0x72.toByte(), 0x65.toByte(), 0x84.toByte(),
        0x43.toByte(), 0xa1.toByte(), 0x01.toByte(), 0x26.toByte(), 0xa0.toByte(),
        0xf6.toByte(), 0x58.toByte(), 0x40.toByte(), 0xd1.toByte(), 0x7a.toByte(),
        0xfc.toByte(), 0x5e.toByte(), 0xd5.toByte(), 0xf8.toByte(), 0x92.toByte(),
        0x68.toByte(), 0x3b.toByte(), 0x5c.toByte(), 0xbe.toByte(), 0x95.toByte(),
        0x8d.toByte(), 0xbf.toByte(), 0xd7.toByte(), 0x23.toByte(), 0x3e.toByte(),
        0x0d.toByte(), 0xb9.toByte(), 0x24.toByte(), 0x14.toByte(), 0xad.toByte(),
        0x8f.toByte(), 0xf2.toByte(), 0xd9.toByte(), 0xa8.toByte(), 0x09.toByte(),
        0x41.toByte(), 0x03.toByte(), 0x7b.toByte(), 0xda.toByte(), 0xba.toByte(),
        0xda.toByte(), 0xb6.toByte(), 0xc6.toByte(), 0x7b.toByte(), 0x95.toByte(),
        0x6d.toByte(), 0xa4.toByte(), 0x33.toByte(), 0x69.toByte(), 0x28.toByte(),
        0x6f.toByte(), 0xbf.toByte(), 0x9c.toByte(), 0x66.toByte(), 0x01.toByte(),
        0xac.toByte(), 0x21.toByte(), 0xa0.toByte(), 0x10.toByte(), 0x5b.toByte(),
        0xce.toByte(), 0x16.toByte(), 0x7f.toByte(), 0xe6.toByte(), 0xa2.toByte(),
        0x07.toByte(), 0x69.toByte(), 0x27.toByte(), 0x01.toByte(), 0x4c.toByte(),
        0x00.toByte(), 0x60.toByte()
    )

    private val verifierPrivateKeyData = byteArrayOf(
        0x30.toByte(), 0x81.toByte(), 0x87.toByte(), 0x02.toByte(), 0x01.toByte(),
        0x00.toByte(), 0x30.toByte(), 0x13.toByte(), 0x06.toByte(), 0x07.toByte(),
        0x2a.toByte(), 0x86.toByte(), 0x48.toByte(), 0xce.toByte(), 0x3d.toByte(),
        0x02.toByte(), 0x01.toByte(), 0x06.toByte(), 0x08.toByte(), 0x2a.toByte(),
        0x86.toByte(), 0x48.toByte(), 0xce.toByte(), 0x3d.toByte(), 0x03.toByte(),
        0x01.toByte(), 0x07.toByte(), 0x04.toByte(), 0x6d.toByte(), 0x30.toByte(),
        0x6b.toByte(), 0x02.toByte(), 0x01.toByte(), 0x01.toByte(), 0x04.toByte(),
        0x20.toByte(), 0x23.toByte(), 0xbd.toByte(), 0xfe.toByte(), 0xb5.toByte(),
        0x14.toByte(), 0x32.toByte(), 0x13.toByte(), 0xc4.toByte(), 0x64.toByte(),
        0x24.toByte(), 0x2d.toByte(), 0x70.toByte(), 0xd7.toByte(), 0x1d.toByte(),
        0x60.toByte(), 0x60.toByte(), 0xab.toByte(), 0x67.toByte(), 0x9d.toByte(),
        0xff.toByte(), 0x18.toByte(), 0xfe.toByte(), 0x2c.toByte(), 0x13.toByte(),
        0x16.toByte(), 0x74.toByte(), 0x2d.toByte(), 0x26.toByte(), 0x0b.toByte(),
        0x79.toByte(), 0xfb.toByte(), 0x56.toByte(), 0xa1.toByte(), 0x44.toByte(),
        0x03.toByte(), 0x42.toByte(), 0x00.toByte(), 0x04.toByte(), 0x5d.toByte(),
        0xe2.toByte(), 0x95.toByte(), 0x54.toByte(), 0xf7.toByte(), 0xe1.toByte(),
        0x70.toByte(), 0xcd.toByte(), 0x67.toByte(), 0xe3.toByte(), 0xa9.toByte(),
        0x52.toByte(), 0x50.toByte(), 0xa3.toByte(), 0x71.toByte(), 0x9f.toByte(),
        0x5e.toByte(), 0xd1.toByte(), 0xb9.toByte(), 0xd5.toByte(), 0x77.toByte(),
        0x04.toByte(), 0x29.toByte(), 0x79.toByte(), 0x0c.toByte(), 0x00.toByte(),
        0xa7.toByte(), 0x09.toByte(), 0x8a.toByte(), 0x1f.toByte(), 0x2b.toByte(),
        0xee.toByte(), 0x09.toByte(), 0xeb.toByte(), 0x22.toByte(), 0xe6.toByte(),
        0x1e.toByte(), 0xfc.toByte(), 0x58.toByte(), 0x12.toByte(), 0xd0.toByte(),
        0xeb.toByte(), 0x6c.toByte(), 0x0e.toByte(), 0x86.toByte(), 0x05.toByte(),
        0xec.toByte(), 0x3d.toByte(), 0x5e.toByte(), 0x68.toByte(), 0x2e.toByte(),
        0x1f.toByte(), 0xcd.toByte(), 0x3b.toByte(), 0x0a.toByte(), 0xb4.toByte(),
        0x92.toByte(), 0x6d.toByte(), 0x8a.toByte(), 0xc2.toByte(), 0x36.toByte(),
        0xff.toByte(), 0x29.toByte(), 0xb8.toByte()
    )

    private val deviceKeyData = byteArrayOf(
        0xa4.toByte(), 0x01.toByte(), 0x02.toByte(), 0x20.toByte(), 0x01.toByte(),
        0x21.toByte(), 0x58.toByte(), 0x20.toByte(), 0x23.toByte(), 0xf4.toByte(),
        0x61.toByte(), 0x81.toByte(), 0x37.toByte(), 0x54.toByte(), 0xd5.toByte(),
        0xdc.toByte(), 0x75.toByte(), 0x69.toByte(), 0xd9.toByte(), 0x52.toByte(),
        0x59.toByte(), 0x17.toByte(), 0xaa.toByte(), 0x7a.toByte(), 0xc4.toByte(),
        0x45.toByte(), 0xd0.toByte(), 0x7f.toByte(), 0xed.toByte(), 0x39.toByte(),
        0xc9.toByte(), 0x49.toByte(), 0x43.toByte(), 0x9b.toByte(), 0x8d.toByte(),
        0x3c.toByte(), 0xc1.toByte(), 0x77.toByte(), 0xd8.toByte(), 0x2d.toByte(),
        0x22.toByte(), 0x58.toByte(), 0x20.toByte(), 0x79.toByte(), 0xe4.toByte(),
        0x6b.toByte(), 0x66.toByte(), 0x9b.toByte(), 0x43.toByte(), 0x4f.toByte(),
        0x19.toByte(), 0x01.toByte(), 0x0c.toByte(), 0x98.toByte(), 0x20.toByte(),
        0x55.toByte(), 0x3e.toByte(), 0xa0.toByte(), 0x82.toByte(), 0x94.toByte(),
        0x90.toByte(), 0x16.toByte(), 0x4d.toByte(), 0x1c.toByte(), 0x2d.toByte(),
        0x46.toByte(), 0xb4.toByte(), 0xa6.toByte(), 0xa8.toByte(), 0x57.toByte(),
        0xd0.toByte(), 0x28.toByte(), 0xcf.toByte(), 0x1e.toByte(), 0x1e.toByte()
    )

    private val deviceSigned = DeviceSigned.Builder().decode(deviceSignedData).build()

    private var deviceEngagement: DeviceEngagement =
        DeviceEngagement.Builder().decode(deviceEngagementData).build()
    private var deviceEngagementWrong: DeviceEngagement =
        DeviceEngagement.Builder().decode(deviceEngagementDataWrong).build()
    private var deviceNameSpaces: DeviceNameSpaces? = deviceSigned.deviceNameSpaces
    private val docType = MdlDoctype
    private var readerKey: CoseKey = CoseKey.Builder().decode(readerKeyData).build()
    private var deviceAuth: DeviceAuth? = deviceSigned.deviceAuth
    private var verifierPrivateKey: PrivateKey? =
        CryptoUtils.decodePrivateKey(verifierPrivateKeyData)
    private val deviceKey: CoseKey = CoseKey.Builder().decode(deviceKeyData).build()

    @Test
    fun testDeclaredObjects() {
        Assert.assertNotNull(deviceSigned)
        Assert.assertNotNull(deviceEngagement)
        Assert.assertNotNull(deviceNameSpaces)
        Assert.assertNotNull(deviceAuth)
        Assert.assertNotNull(verifierPrivateKey)
    }

    @Test
    fun testMdlAuthentic() {
        val mdlAuthenticator = MdlAuthenticator(
            deviceEngagement,
            deviceNameSpaces,
            docType,
            readerKey,
            deviceAuth,
            verifierPrivateKey,
            deviceKey
        )
        Assert.assertTrue(mdlAuthenticator.isMdlAuthentic())
    }

    @Test
    fun testMdlAuthenticFalse() {
        val mdlAuthenticator = MdlAuthenticator(
            deviceEngagementWrong,
            deviceNameSpaces,
            docType,
            readerKey,
            deviceAuth,
            verifierPrivateKey,
            deviceKey
        )
        val exception = assertFailsWith<MdlAuthenticationException> {
            mdlAuthenticator.isMdlAuthentic()
        }

        Assert.assertTrue(exception.message?.contains("Signature does not verify") ?: false)
    }
}
