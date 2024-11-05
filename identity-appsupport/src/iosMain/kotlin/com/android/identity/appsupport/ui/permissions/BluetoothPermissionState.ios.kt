package com.android.identity.appsupport.ui.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.android.identity.util.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBManager
import platform.CoreBluetooth.CBManagerAuthorization
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBManagerAuthorizationNotDetermined
import platform.darwin.NSObject

private const val TAG = "BluetoothPermissionState.ios"

@OptIn(ExperimentalForeignApi::class)
private fun calcIsGranted(): Boolean {
    val state: CBManagerAuthorization = CBManager.authorization
    Logger.i(TAG, "state (CBManagerAuthorization): $state")
    when (state) {
        CBManagerAuthorizationAllowedAlways -> {
            return true
        }
        CBManagerAuthorizationNotDetermined -> {
            return false
        }
        else -> {
            return false
        }
    }
}

private class IosBluetoothPermissionState(
    val recomposeToggleState: MutableState<Boolean>,
    val scope: CoroutineScope
) : BluetoothPermissionState {

    private var iosIsGranted: Boolean = calcIsGranted()

    override val isGranted: Boolean
        get() = iosIsGranted

    override fun launchPermissionRequest() {
        Logger.i(TAG, "launchPermissionRequest...")
        CBCentralManager(object : NSObject(), CBCentralManagerDelegateProtocol {
            override fun centralManagerDidUpdateState(central: CBCentralManager) {
                Logger.i(TAG, "Did update state ${central.authorization}")
                scope.launch(Dispatchers.Main) {
                    iosIsGranted = calcIsGranted()
                    recomposeToggleState.value = !recomposeToggleState.value
                }
            }
        }, null)
    }
}

@Composable
actual fun rememberBluetoothPermissionState(): BluetoothPermissionState {
    val scope = rememberCoroutineScope()
    val recomposeToggleState: MutableState<Boolean> = mutableStateOf(false)
    LaunchedEffect(recomposeToggleState.value) {}
    return IosBluetoothPermissionState(recomposeToggleState, scope)
}

