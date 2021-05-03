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
import android.util.Log
import androidx.preference.PreferenceManager
import com.ul.ims.gmdl.offlinetransfer.transportLayer.TransferChannels


object SettingsUtils {

    private val LOG_TAG = SettingsUtils::class.java.simpleName

    fun getTransferMethod(context: Context): TransferChannels {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val ble = sharedPreferences.getBoolean("transfer_method_ble", false)
        val wifi = sharedPreferences.getBoolean("transfer_method_wifi", false)
        val nfc = sharedPreferences.getBoolean("transfer_method_nfc", false)

        return when {
            ble -> TransferChannels.BLE
            wifi -> TransferChannels.WiFiAware
            nfc -> TransferChannels.NFC
            else -> {
                Log.d(
                    LOG_TAG,
                    "Unable to get Transfer Method from shared preferences, return BLE as default"
                )
                TransferChannels.BLE
            }
        }
    }

    fun setTransferMethod(context: Context, tranferMethod: TransferChannels) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        with(sharedPreferences.edit()) {
            putBoolean("transfer_method_ble", TransferChannels.BLE == tranferMethod)
            putBoolean("transfer_method_wifi", TransferChannels.WiFiAware == tranferMethod)
            putBoolean("transfer_method_nfc", TransferChannels.NFC == tranferMethod)
            commit()
        }
    }

    fun setAgeAttestationPreApproval(context: Context, checked: Boolean) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        with(sharedPreferences.edit()) {
            putBoolean("age_attestation_pre_approval", checked)
            commit()
        }
    }

    fun getAgeAttestationPreApproval(context: Context): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getBoolean("age_attestation_pre_approval", false)
    }
}
