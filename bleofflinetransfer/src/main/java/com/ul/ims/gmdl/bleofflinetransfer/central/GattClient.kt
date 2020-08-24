/*
 * Copyright (C) 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package com.ul.ims.gmdl.bleofflinetransfer.central

import android.bluetooth.*
import android.content.Context
import android.util.Log
import com.ul.ims.gmdl.bleofflinetransfer.*
import com.ul.ims.gmdl.bleofflinetransfer.common.BleEventListener
import com.ul.ims.gmdl.bleofflinetransfer.common.DataWriter
import com.ul.ims.gmdl.bleofflinetransfer.common.GattCommon
import com.ul.ims.gmdl.bleofflinetransfer.config.ServiceCharacteristics
import com.ul.ims.gmdl.bleofflinetransfer.exceptions.GattException
import com.ul.ims.gmdl.bleofflinetransfer.utils.ByteUtils
import com.ul.ims.gmdl.offlinetransfer.config.BleServiceMode
import com.ul.ims.gmdl.offlinetransfer.executorLayer.IExecutorEventListener
import com.ul.ims.gmdl.offlinetransfer.transportLayer.EventType
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.concurrent.thread


class GattClient(
    private val bleServiceCharacteristics: ServiceCharacteristics,
    private val publicKeyHash: ByteArray?,
    private val bleServiceMode: BleServiceMode
) : GattCommon {

    override var stateCharacteristic: BluetoothGattCharacteristic? = null
    override var serverToClientCharacteristic: BluetoothGattCharacteristic? = null
    override var clientToServerCharacteristic: BluetoothGattCharacteristic? = null
    override var identCharacteristic: BluetoothGattCharacteristic? = null
    override var maxSupportedMtu: Int = MAX_MTU
    override var dataStream: ByteArrayOutputStream = ByteArrayOutputStream()
    override var bleEventListener: BleEventListener? = null
    override var executorEventListener: IExecutorEventListener? = null
    override var bluetoothGattService: BluetoothGattService? = null

    private var bluetoothGatt: BluetoothGatt? = null
    private var dataWriter: DataWriter? = null

    companion object {
        const val LOG_TAG = "GattClient"
    }

    /**
     * Read a BluetoothGattCharacteristic on the remote device
     * **/
    fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {
        bluetoothGatt?.readCharacteristic(characteristic)
    }

    /**
     * Connect to a remote BLE Device
     * **/
    @Synchronized
    fun connect(context: Context, device: BluetoothDevice) {
        try {
            Log.d(LOG_TAG, "Connecting to ${ByteUtils.deviceString(device)}")
            bluetoothGatt = device.connectGatt(context, false, object :
                BluetoothGattCallback() {

                override fun onDescriptorWrite(
                    gatt: BluetoothGatt?,
                    descriptor: BluetoothGattDescriptor?,
                    status: Int) {

                    Log.d(LOG_TAG, "onDescriptorWrite status = $status")
                }

                override fun onConnectionStateChange(
                    gatt: BluetoothGatt, status: Int,
                    newState: Int
                ) {
                    Log.d(LOG_TAG,"onConnectionStateChange status = $status, new stateUuid = $newState")
                    status.takeIf { it == BluetoothGatt.GATT_SUCCESS }?.apply {
                        when (newState) {
                            BluetoothGatt.STATE_CONNECTED -> {
                                setConnectionParameters()
                                bleEventListener?.onBLEEvent("Connected.", EventType.GATT_CONNECTED)
                            }
                            BluetoothGatt.STATE_DISCONNECTED -> {
                                gatt.close()
                                bleEventListener?.onBLEEvent(
                                    "Disconnected from GATT server",
                                    EventType.GATT_DISCONNECTED
                                )
                            }
                        }
                    }
                }

                override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                    super.onMtuChanged(gatt, mtu, status)
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.i(javaClass.simpleName, "Mtu changed. new value for mtu=$mtu")
                        maxSupportedMtu = mtu
                        discoverServices()
                    } else {
                        Log.i(javaClass.simpleName, "Cannot determine, or increase MTU")
                        throw GattException("Cannot determine, or set MTU")
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    Log.i(
                        javaClass.simpleName,
                        "On serviceUuid discovered status $status success ${status == BluetoothGatt.GATT_SUCCESS}"
                    )

                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        throw GattException("No services were discovered, or an error occurred while attempting to discover services.")
                    }

                    gatt.services.takeIf { it.isEmpty() }?.apply {
                        // No Services found.
                        Log.e(LOG_TAG, "no services found.")
                        bleEventListener?.onBLEEvent("No services found", EventType.ERROR)
                    }

                    gatt.services.forEach {
                        Log.i(javaClass.simpleName, "Found serviceUuid ${it.uuid}")
                    }

                    bluetoothGattService = gatt.getService(bleServiceCharacteristics.serviceUuid)
                    bluetoothGattService.takeIf { it != null }?.apply {
                        serverToClientCharacteristic =
                            bluetoothGattService?.getCharacteristic(bleServiceCharacteristics.server2ClientUuid)
                        stateCharacteristic =
                            bluetoothGattService?.getCharacteristic(bleServiceCharacteristics.stateUuid)
                        clientToServerCharacteristic =
                            bluetoothGattService?.getCharacteristic(bleServiceCharacteristics.client2ServerUuid)
                        identCharacteristic =
                            bluetoothGattService?.getCharacteristic(bleServiceCharacteristics.identUuid)

                        bleEventListener?.onBLEEvent(
                            "Connected to serviceUuid ${bleServiceCharacteristics.serviceUuid}, " +
                                    "serverToClientCharacteristic ${bleServiceCharacteristics.server2ClientUuid}, " +
                                    "stateCharacteristic ${bleServiceCharacteristics.stateUuid}, " +
                                    "identCharacteristic ${bleServiceCharacteristics.identUuid}",
                            EventType.SERVICE_CONNECTED
                        )

                        setCharacteristicNotifications()
                    }
                }

                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {

                    Log.i(
                        "GattClient",
                        "onCharacteristicRead: characteristic = ${characteristic.uuid}, " +
                                "Status: $status, Success: ${status == BluetoothGatt.GATT_SUCCESS}"
                    )

                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        when (characteristic.uuid) {
                            identCharacteristic?.uuid -> {
                                Log.d(
                                    LOG_TAG,
                                    "Verifier Ident Characteristic Value = ${ByteUtils.bytesToHex(
                                        characteristic.value
                                    )}"
                                )
                                publicKeyHash?.let {
                                    if (it.contentEquals(characteristic.value)) {
                                        Log.d(
                                            LOG_TAG,
                                            "Authenticity is confirmed using the Ident Characteristic"
                                        )
                                        bleEventListener?.onBLEEvent(
                                            "Authenticity is confirmed using the Ident Characteristic",
                                            EventType.VERIFIER_VERIFIED
                                        )
                                    } else {
                                        Log.d(
                                            LOG_TAG,
                                            "Authenticity could not be verified"
                                        )
                                        bleEventListener?.onBLEEvent(
                                            "Authenticity could not be verified",
                                            EventType.ERROR
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt?,
                    characteristic: BluetoothGattCharacteristic?
                ) {
                    if (characteristic == null) {
                        Log.e(LOG_TAG, "Characteristic is null")
                    }
                    Log.d(LOG_TAG,
                        "onCharacteristicChanged: Characteristic: ${serverToClientCharacteristic?.uuid} " +
                                "with value ${ByteUtils.bytesToHex(characteristic?.value)}")

                    characteristic?.let {
                        when (characteristic) {
                            serverToClientCharacteristic -> {
                                if (ByteUtils.isDataPending(characteristic.value)) {
                                    bleEventListener?.onBLEEvent(
                                        "Further data awaited",
                                        EventType.TRANSFER_IN_PROGRESS
                                    )
                                    // Prefix 01 must be removed from the beginning of the array
                                    dataStream.write(ByteUtils.decodeData(characteristic.value))
                                } else {
                                    // Prefix 00 must be removed from the beginning of the array
                                    dataStream.write(ByteUtils.decodeData(characteristic.value))
                                    val receivedData = dataStream.toByteArray()
                                    thread {
                                        Log.i(
                                            "GattClient",
                                            "Delegated (to ITransportEventListener) data: ${ByteUtils.bytesToHex(
                                                receivedData
                                            )}"
                                        )
                                        executorEventListener?.onReceive(receivedData)
                                    }
                                    dataStream.reset()
                                }
                            }
                            stateCharacteristic -> {
                                if (characteristic.value[0] == TERMINATE_TRANSMISSION) {
                                    bleEventListener?.onBLEEvent(
                                        "Termination requested",
                                        EventType.GATT_DISCONNECTED
                                    )
                                }
                            }
                        }
                    }
                }

                override fun onCharacteristicWrite(
                    gatt: BluetoothGatt?,
                    characteristic: BluetoothGattCharacteristic?,
                    status: Int
                ) {
                    Log.i(
                        "GattClient",
                        "onCharacteristicWrite: ${characteristic?.uuid}, with value: ${ByteUtils.bytesToHex(
                            characteristic?.value
                        )}, and Status: $status"
                    )
                    dataWriter?.characteristicWriteCalledBack()

                    if (bleServiceMode ==  BleServiceMode.PERIPHERAL_SERVER_MODE) {
                        when (characteristic?.uuid) {
                            bleServiceCharacteristics.stateUuid -> decodeState(characteristic.value)
                        }
                    }
                }
            }, BluetoothDevice.TRANSPORT_LE)


        } catch (ex: IllegalArgumentException) {
            Log.e(LOG_TAG, ex.message, ex)
        }
    }

    private fun decodeState(value: ByteArray?) {
        if (value == null) {
            throw GattException("Value of stateCharacteristic is null")
        }
        if (value.size != 1) {
            throw GattException("Invalid status")
        }
        if (value[0] == READY_FOR_TRANSMISSION) {
            executorEventListener?.onEvent(
                EventType.STATE_READY_FOR_TRANSMISSION.description,
                EventType.STATE_READY_FOR_TRANSMISSION.ordinal
            )
        }
//        if (value[0] == TERMINATE_TRANSMISSION) {
//
//        }
    }

    /**
     * Send BLE Event Notifications to BleCentralConnection class or BlePeripheralConnection
     * **/
    fun setBleEventCallback(bleEventListener: BleEventListener) {
        this.bleEventListener = bleEventListener
    }

    /**
     * Send Event Notifications to the upper level (Executor Level)
     * **/
    fun setExecutorEventCallback(executorEventListener: IExecutorEventListener) {
        this.executorEventListener = executorEventListener
    }

    /**
     * Close a connection with a remote device
     * **/
    fun close() {
        Log.d(LOG_TAG, "Gatt client disconnect")

        // disconnect from a remote device
        bluetoothGatt?.disconnect()

        // close this Bluetooth GATT client
        bluetoothGatt?.close()
        this.bluetoothGatt = null
    }

    /**
     * Update the value of clientToServerCharacteristic
     * **/
    fun write(data: ByteArray, chunkSize: Int) {
        Log.d(LOG_TAG, "Gatt client write with data: ${ByteUtils.bytesToHex(data)}")

        clientToServerCharacteristic?.let { btGattCharacteristic ->
            bluetoothGatt?.let { btGatt ->
                bleEventListener?.let { eventCallback ->
                    dataWriter = DataWriter(
                        data, chunkSize, btGatt, btGattCharacteristic,
                        eventCallback
                    )
                    dataWriter?.writeInParts()
                }
            }
        }
    }

    /**
     * Update the value of stateCharacteristic
     * **/
    fun writeToState(status: Byte) {
        Log.d(LOG_TAG, "Gatt client writeToState")

        stateCharacteristic?.let {
            it.value = byteArrayOf(status)
            val stateStatusWritten = bluetoothGatt?.writeCharacteristic(it) ?: false
            Log.d(LOG_TAG, "writeCharacteristic (${ByteUtils.bytesToHex(it.value)}): " +
                    "status = $status, Successfully written = $stateStatusWritten")

            if (!stateStatusWritten) {
                val error = "Could not write characteristic ${it.uuid} " +
                        "(value ${ByteUtils.bytesToHex(it.value)}."

                bleEventListener?.onBLEEvent(error,
                    EventType.ERROR)
            }
        }
    }

    /**
     * Discover services in the remote device
     * **/
    private fun discoverServices() {
        Log.d(LOG_TAG, "Discover services")
        bluetoothGatt?.discoverServices()
    }

    /**
     * Set Characteristic change notification for serverToClientCharacteristic and
     * serverToClientCharacteristic characteristics
     * **/
    private fun setCharacteristicNotifications() {

        val readerNotificationsEnabled =
            bluetoothGatt?.setCharacteristicNotification(serverToClientCharacteristic, true)

        val serverToClientDescriptor = serverToClientCharacteristic?.
            getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG))

        serverToClientDescriptor?.let {
            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            bluetoothGatt?.writeDescriptor(it)
        }

        val stateNotificationsEnabled =
            bluetoothGatt?.setCharacteristicNotification(stateCharacteristic, true)

        stateCharacteristic?.getDescriptor(
            UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG)
        )?.apply {
            value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            bluetoothGatt?.writeDescriptor(this)
        }

        if (readerNotificationsEnabled == true && stateNotificationsEnabled == true) {
            Log.d(
                LOG_TAG, "serverToClientCharacteristic and stateCharacteristic " +
                        "notifications enabled"
            )
        } else {
            Log.d(
                LOG_TAG, "Enabling serverToClientCharacteristic and stateCharacteristic " +
                        "notifications unsuccessful"
            )
        }
    }

    /**
     * Set Connection Parameters
     * **/
    private fun setConnectionParameters() {
        Log.d(LOG_TAG, "set connection parameters")

        bluetoothGatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
        bluetoothGatt?.requestMtu(MAX_MTU)
    }

    fun getMTU(): Int {
        if (this.maxSupportedMtu == MTU_UNKNOWN) {
            throw GattException("MTU not set")
        }
        return maxSupportedMtu
    }
}