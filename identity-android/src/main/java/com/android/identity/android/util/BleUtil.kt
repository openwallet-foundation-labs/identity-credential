package com.android.identity.android.util

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context

object BleUtil {

    /**
     * Checks if Bluetooth Low Energy (BLE) is enabled on the device and return the corresponding BluetoothAdapter.
     * No permissions check is performed.
     *
     * @param context The application context.
     * @return BluetoothAdapter if BLE hardware is available and enabled, `null` otherwise.
     */
    fun getBleAdapterOrNull(context: Context?): BluetoothAdapter? =
        getBleAdapterOrNull(context?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)

    /**
     * Checks if Bluetooth Low Energy (BLE) is enabled on the device and return the corresponding BluetoothAdapter.
     * No permissions check is performed.
     *
     * @return BluetoothAdapter if BLE hardware is available and enabled, `null` otherwise.
     */
    fun getBleAdapterOrNull(bluetoothManager: BluetoothManager?): BluetoothAdapter? {
        val bluetoothAdapter: BluetoothAdapter = bluetoothManager?.adapter
            ?: // Device doesn't support Bluetooth
            return null
        return if (bluetoothAdapter.isEnabled) bluetoothAdapter else null
    }
}