package org.multipaz.compose.permissions

import androidx.compose.runtime.Composable

expect class BluetoothEnabledState {
    val isEnabled: Boolean
    suspend fun enable()
}

@Composable
expect fun rememberBluetoothEnabledState(): BluetoothEnabledState