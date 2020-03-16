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

package com.ul.ims.gmdl.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.ul.ims.gmdl.offlinetransfer.transportLayer.EngagementChannels
import com.ul.ims.gmdl.offlinetransfer.transportLayer.TransferChannels
import kotlin.IllegalStateException


object SettingsUtils {

    fun getEngagementMethod(context: Context): EngagementChannels {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val qr = sharedPreferences.getBoolean("engagement_method_qr", false)
        val nfc = sharedPreferences.getBoolean("engagement_method_nfc", false)

        return when {
            qr -> EngagementChannels.QR
            nfc -> EngagementChannels.NFC
            else -> throw IllegalStateException("Unable to get Engagement Method from shared preferences")
        }
    }

    fun getTransferMethod(context: Context) : TransferChannels {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val ble = sharedPreferences.getBoolean("transfer_method_ble", false)
        val wifi = sharedPreferences.getBoolean("transfer_method_wifi", false)
        val nfc = sharedPreferences.getBoolean("transfer_method_nfc", false)

        return when {
            ble -> TransferChannels.BLE
            wifi -> TransferChannels.WiFiAware
            nfc -> TransferChannels.NFC
            else -> throw IllegalStateException("Unable to get Transfer Method from shared preferences")
        }
    }
}
