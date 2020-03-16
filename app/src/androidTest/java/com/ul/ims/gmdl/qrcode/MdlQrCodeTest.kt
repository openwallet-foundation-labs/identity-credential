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

package com.ul.ims.gmdl.qrcode

import android.net.Uri
import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement
import org.junit.Assert
import org.junit.Test

class MdlQrCodeTest {

    val base64DeString =
        "hWMxLjCCAdgYWEukAQIgASFYIAZiYL9qwp-GfaaPMuS-QEuEV-SejwLQhBs24yejSGPwIlgghRVrD3WJf2gkrepbrSZrwsQdqr4jFJm0LveGmNlhy0KBgwIBogD1AfWggA=="

    @Test
    fun getQrCodeTest() {
        val de = DeviceEngagement.Builder()
            .decodeFromBase64(base64DeString)
            .build()

        val mdlQrCode = MdlQrCode.Builder()
            .setDeviceEngagement(de)
            .build()

        val qrcode = mdlQrCode.getQrCode()

        Assert.assertNotNull(qrcode)
    }

    @Test
    fun fromUriTest() {
        val uri = Uri.Builder()
            .scheme(MdlQrCode.QRCODE_URI_SCHEME)
            .encodedOpaquePart(base64DeString)
            .build()

        val mdlQrCode = MdlQrCode.Builder()
            .fromUri(uri.toString())
            .build()

        Assert.assertNotNull(mdlQrCode)
    }
}