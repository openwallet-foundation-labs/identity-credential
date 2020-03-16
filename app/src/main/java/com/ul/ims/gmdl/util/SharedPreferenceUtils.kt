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
import com.ul.ims.gmdl.R

class SharedPreferenceUtils(val context: Context) {

    fun isDeviceProvisioned() : Boolean {
        val sharedPref = context.getSharedPreferences(
            context.getString(R.string.is_device_provisioned), Context.MODE_PRIVATE)

        return sharedPref.getBoolean(context.getString(R.string.is_device_provisioned), false)
    }
    
    fun setDeviceProvisioned(isProvisioned : Boolean) {
        val sharedPref = context.getSharedPreferences(
            context.getString(R.string.is_device_provisioned), Context.MODE_PRIVATE)

        with (sharedPref.edit()) {
            putBoolean(context.getString(R.string.is_device_provisioned), isProvisioned)
            apply()
        }
    }

    fun isBiometricAuthRequired() : Boolean {
        val sharedPref = context.getSharedPreferences(
            context.getString(R.string.biometric_auth), Context.MODE_PRIVATE)

        return sharedPref.getBoolean(context.getString(R.string.biometric_auth), false)
    }

    fun setBiometricAuthRequired(isRequired : Boolean) {
        val sharedPref = context.getSharedPreferences(
            context.getString(R.string.biometric_auth), Context.MODE_PRIVATE)

        with (sharedPref.edit()) {
            putBoolean(context.getString(R.string.biometric_auth), isRequired)
            apply()
        }
    }
}