package org.multipaz.compose.permissions

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.content.ContextCompat.getSystemService
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import org.multipaz.context.getActivity

@OptIn(ExperimentalPermissionsApi::class)
private class AccompanistBluetoothPermissionState(
    val activity: Activity,
    val multiplePermissionsState: MultiplePermissionsState
) : PermissionState {

    lateinit var bluetoothAdapter: BluetoothAdapter

    init {
        val bluetoothManager: BluetoothManager? =
            getSystemService(activity, BluetoothManager::class.java)
        bluetoothManager?.let {
            bluetoothAdapter = bluetoothManager.getAdapter()
        }
    }

    override val isGranted: Boolean
        get() = multiplePermissionsState.allPermissionsGranted && bluetoothAdapter.isEnabled

    override suspend fun launchPermissionRequest() {
        multiplePermissionsState.launchMultiplePermissionRequest()
        if (bluetoothAdapter.isEnabled == false) {
            startActivityForResult(
                activity,
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                2,
                null
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
actual fun rememberBluetoothPermissionState(): PermissionState {
    val activity = LocalContext.current.getActivity() as Activity
    return AccompanistBluetoothPermissionState(
        activity,
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
