package org.multipaz.compose.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBManagerStatePoweredOff
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBManagerStateResetting
import platform.CoreBluetooth.CBManagerStateUnauthorized
import platform.CoreBluetooth.CBManagerStateUnknown
import platform.CoreBluetooth.CBManagerStateUnsupported
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

actual class BluetoothEnabledState internal constructor(
    private val scope: CoroutineScope
) {
    private val _isEnabled = mutableStateOf(false)
    actual val isEnabled: Boolean get() = _isEnabled.value

    private var centralManager: CBCentralManager? = null

    private val delegate = object : NSObject(), CBCentralManagerDelegateProtocol {
        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            scope.launch(Dispatchers.Main) {
                _isEnabled.value = central.state == CBManagerStatePoweredOn
            }
        }
    }

    init {
        centralManager = CBCentralManager(delegate, null)
        _isEnabled.value = centralManager?.state == CBManagerStatePoweredOn
    }

    actual suspend fun enable() {
        when (centralManager?.state) {
            CBManagerStatePoweredOff -> {
                // On iOS, we can't programmatically enable Bluetooth
                // Direct the user to Settings
                val settingsUrl = NSURL.URLWithString(UIApplicationOpenSettingsURLString)
                if (settingsUrl != null && UIApplication.sharedApplication.canOpenURL(settingsUrl)) {
                    UIApplication.sharedApplication.openURL(settingsUrl, mapOf<Any?, Any?>(), null)
                }
            }

            CBManagerStateUnauthorized -> {
                // Bluetooth permission not granted
                throw IllegalStateException("Unauthorized: Bluetooth permission not granted")
            }

            CBManagerStateUnsupported -> {
                // Bluetooth is not supported on this device
                throw UnsupportedOperationException("Bluetooth is not supported on this device.")
            }

            CBManagerStateUnknown, CBManagerStateResetting -> {
                // Wait for state to be determined
                suspendCoroutine<Unit> { continuation ->
                    val tempDelegate = object : NSObject(), CBCentralManagerDelegateProtocol {
                        override fun centralManagerDidUpdateState(central: CBCentralManager) {
                            when (central.state) {
                                CBManagerStatePoweredOn -> {
                                    scope.launch(Dispatchers.Main) {
                                        _isEnabled.value = true
                                    }
                                    continuation.resume(Unit)
                                }

                                CBManagerStatePoweredOff -> {
                                    continuation.resume(Unit)
                                    // try to open settings
                                    scope.launch {
                                        val settingsUrl =
                                            NSURL.URLWithString(UIApplicationOpenSettingsURLString)
                                        if (settingsUrl != null && UIApplication.sharedApplication.canOpenURL(
                                                settingsUrl
                                            )
                                        ) {
                                            UIApplication.sharedApplication.openURL(
                                                settingsUrl, mapOf<Any?, Any?>(), null
                                            )
                                        }
                                    }
                                }

                                else -> {
                                    continuation.resume(Unit)
                                }
                            }
                        }
                    }
                    centralManager = CBCentralManager(tempDelegate, null)
                }
            }

            CBManagerStatePoweredOn -> {
                // Already enabled, nothing to do
            }

            else -> {
                // Only throw for truly unexpected states
                throw IllegalStateException("Unexpected Bluetooth state: ${centralManager?.state}")
            }
        }
    }

    fun cleanup() {
        centralManager = null
    }
}

@Composable
actual fun rememberBluetoothEnabledState(): BluetoothEnabledState {
    val scope = rememberCoroutineScope()
    val state = remember {
        BluetoothEnabledState(scope)
    }

    DisposableEffect(Unit) {
        onDispose {
            state.cleanup()
        }
    }

    return state
}