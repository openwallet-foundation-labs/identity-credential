package org.multipaz.compose.permissions

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

actual class BluetoothEnabledState internal constructor(
    private val context: Context,
    private val adapter: BluetoothAdapter?
) {
    private val _isEnabled = mutableStateOf(adapter?.isEnabled == true)
    actual val isEnabled: Boolean get() = _isEnabled.value

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                _isEnabled.value = adapter?.isEnabled == true
            }
        }
    }

    init {
        context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    }

    actual suspend fun enable() {
        try {
            context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: SecurityException) {
            throw IllegalStateException("Bluetooth permission is required to enable Bluetooth", e)
        }
    }

    fun unregister() {
        context.unregisterReceiver(receiver)
    }
}

@Composable
actual fun rememberBluetoothEnabledState(): BluetoothEnabledState {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val adapter = remember { BluetoothAdapter.getDefaultAdapter() }
    val state = remember {
        BluetoothEnabledState(context, adapter)
    }
    DisposableEffect(Unit) {
        onDispose {
            state.unregister()
        }
    }
    return state
}