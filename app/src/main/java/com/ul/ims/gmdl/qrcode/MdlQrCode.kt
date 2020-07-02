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

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement
import com.ul.ims.gmdl.cbordata.utils.Base64Utils
import com.ul.ims.gmdl.cbordata.utils.CborUtils
import com.ul.ims.gmdl.util.QrCode
import com.ul.ims.gmdl.viewmodel.ShareCredentialsViewModel

class MdlQrCode private constructor(
    private val deviceEngagement: DeviceEngagement?
) {
    companion object {
        const val QRCODE_URI_SCHEME ="mdl"
        const val LOG_TAG = "MdlQrCode"
    }

    fun getQrCode() : Bitmap? {
        var qrcode : Bitmap? = null

        deviceEngagement?.let {engagement ->
            val encoded = deviceEngagement.encode()
            Log.d(LOG_TAG, CborUtils.cborPrettyPrint(encoded))
            Log.d(LOG_TAG, deviceEngagement.encodeToString())

            val base64DeviceEngagement = Base64Utils
                .encodeToString(engagement.encode())

            val uri = Uri.Builder()
                .scheme(QRCODE_URI_SCHEME)
                .encodedOpaquePart(base64DeviceEngagement)
                .build()

            Log.d(LOG_TAG, "device engagement qrcode uri $uri")

            try {

                qrcode = QrCode.encodeAsBitmap(
                uri.toString(),
                BarcodeFormat.QR_CODE,
                ShareCredentialsViewModel.QRCODE_WIDTH,
                ShareCredentialsViewModel.QRCODE_HEIGHT)

            } catch (ex: WriterException) {
                Log.e(LOG_TAG, ex.message, ex)
            }
        }
        return qrcode
    }

    class Builder {
        private var deviceEngagement: DeviceEngagement? = null

        fun setDeviceEngagement(deviceEngagement: DeviceEngagement?) = apply {
            this.deviceEngagement = deviceEngagement
        }

        fun fromUri(qrcodeUri : String) = apply {
            val uri = Uri.parse(qrcodeUri)
            val deviceEngagementBase64 = uri.encodedSchemeSpecificPart

            if (!deviceEngagementBase64.isNullOrBlank()) {
                deviceEngagement = DeviceEngagement.Builder()
                    .decodeFromBase64(deviceEngagementBase64)
                    .build()
            } else {
                Log.d(LOG_TAG, "QRCode URI encodedSchemeSpecificPart is null or blank")
            }
        }

        fun build() : MdlQrCode {
            return MdlQrCode(deviceEngagement)
        }
    }
}