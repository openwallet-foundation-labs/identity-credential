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

package com.ul.ims.gmdl.reader.qrcode

import android.net.Uri
import android.util.Log
import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement

class MdlQrCode private constructor(
    val deviceEngagement: DeviceEngagement?
) {
    companion object {
        const val LOG_TAG = "MdlQrCode"
    }

    class Builder {
        var deviceEngagement: DeviceEngagement? = null

        fun fromUri(qrcodeUri: String) = apply {
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

        fun build(): MdlQrCode {
            return MdlQrCode(deviceEngagement)
        }
    }
}