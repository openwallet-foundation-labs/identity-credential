package org.multipaz.compose.permissions

import androidx.compose.runtime.Composable

actual class BluetoothEnabledState {
    actual val isEnabled: Boolean
        get() = TODO("Not yet implemented")

    actual suspend fun enable() {
        TODO("Not yet implemented")
    }
}

@Composable
actual fun rememberBluetoothEnabledState(): BluetoothEnabledState {
    return BluetoothEnabledState()
}