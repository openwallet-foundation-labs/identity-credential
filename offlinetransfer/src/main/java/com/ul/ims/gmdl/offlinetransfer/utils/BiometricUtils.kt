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

package com.ul.ims.gmdl.offlinetransfer.utils

import android.content.Context
import androidx.biometric.BiometricManager

object BiometricUtils {
    const val LOG_TAG = "BiometricUtils"

    private fun isBiometricAuthSupported(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)

        return when (biometricManager.canAuthenticate()) {
            BiometricManager.BIOMETRIC_SUCCESS,
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Log.d(LOG_TAG, "Biometric Authentication supported by the device")
                true
            }
            else -> {
                Log.d(LOG_TAG, "Biometric Authentication supported by the device")
                false
            }
        }
    }

    fun isBiometricEnrolled(context: Context) : Boolean {
        val biometricManager = BiometricManager.from(context)

        return when(biometricManager.canAuthenticate()) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                Log.d(LOG_TAG, "Biometric Authentication enrolled")
                true
            }
            else -> {
                Log.d(LOG_TAG, "Biometric Authentication not enrolled")
                false
            }
        }
    }

    fun setUserAuth(context: Context) =
        isBiometricAuthSupported(context) && isBiometricEnrolled(context)
}
