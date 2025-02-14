package org.multipaz.compose.permissions

import android.Manifest
import androidx.compose.runtime.Composable
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
private class AccompanistBluetoothPermissionState(
    val multiplePermissionsState: MultiplePermissionsState
) : BluetoothPermissionState {

    override val isGranted: Boolean
        get() = multiplePermissionsState.allPermissionsGranted

    override fun launchPermissionRequest() {
        multiplePermissionsState.launchMultiplePermissionRequest()
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
actual fun rememberBluetoothPermissionState(): BluetoothPermissionState {
    return AccompanistBluetoothPermissionState(
        rememberMultiplePermissionsState(
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        )
    )
}
