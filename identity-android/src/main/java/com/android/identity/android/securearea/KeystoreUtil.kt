package com.android.identity.android.securearea

import android.content.Context
import android.os.Build

class KeystoreUtil(
    private val context: Context
) {

    fun getDeviceCapabilities(): DeviceCapabilities {
        val systemAvailableFeatures = context.packageManager.systemAvailableFeatures
        //TODO use the system available features to find out device capabilities
        val isApiLevelOver30 = isApiLevelOver30()
        return DeviceCapabilities(configureUserAuthenticationType = isApiLevelOver30)
    }

    private fun isApiLevelOver30(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    data class DeviceCapabilities(
        val attestKey: Boolean = true,
        val secureLockScreen: Boolean = true,
        val ecdh: Boolean = true,
        val curve25519: Boolean = true,
        val strongBox: Boolean = true,
        val strongBoxEcdh: Boolean = true,
        val strongBox25519: Boolean = true,
        val strongBoxAttestKey: Boolean = true,
        val configureUserAuthenticationType: Boolean = true
    )
}
