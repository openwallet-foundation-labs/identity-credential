package org.multipaz.compose.permissions

import android.Manifest
import android.os.Build
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
            if (Build.VERSION.SDK_INT >= 31) {
                listOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                listOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        )
    )
}
