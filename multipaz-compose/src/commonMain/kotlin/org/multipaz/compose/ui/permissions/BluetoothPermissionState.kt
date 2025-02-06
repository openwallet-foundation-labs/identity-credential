package org.multipaz.compose.ui.permissions

import androidx.compose.runtime.Composable

interface BluetoothPermissionState {

    val isGranted: Boolean

    fun launchPermissionRequest()
}

@Composable
expect fun rememberBluetoothPermissionState(): BluetoothPermissionState
