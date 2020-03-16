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

package com.ul.ims.gmdl.bleofflinetransfer.peripheral

import android.bluetooth.*
import android.bluetooth.BluetoothGattDescriptor.PERMISSION_WRITE
import android.content.Context
import android.util.Log
import com.ul.ims.gmdl.bleofflinetransfer.*
import com.ul.ims.gmdl.bleofflinetransfer.common.BleEventListener
import com.ul.ims.gmdl.bleofflinetransfer.common.DataNotifier
import com.ul.ims.gmdl.bleofflinetransfer.common.GattCommon
import com.ul.ims.gmdl.bleofflinetransfer.config.ServiceCharacteristics
import com.ul.ims.gmdl.bleofflinetransfer.exceptions.BluetoothException
import com.ul.ims.gmdl.bleofflinetransfer.exceptions.GattException
import com.ul.ims.gmdl.bleofflinetransfer.exceptions.TransportLayerException
import com.ul.ims.gmdl.bleofflinetransfer.utils.ByteUtils
import com.ul.ims.gmdl.bleofflinetransfer.utils.ByteUtils.decodeData
import com.ul.ims.gmdl.bleofflinetransfer.utils.ByteUtils.deviceString
import com.ul.ims.gmdl.offlinetransfer.executorLayer.IExecutorEventListener
import com.ul.ims.gmdl.offlinetransfer.transportLayer.EventType
import java.io.ByteArrayOutputStream
import java.util.*

class GattServer (
    private val context: Context,
    private val bluetoothManager: BluetoothManager,
    private val bleServiceCharacteristics: ServiceCharacteristics,
    private val publicKeyHash: ByteArray,
    override var bleEventListener: BleEventListener?
) : GattCommon, BluetoothGattServerCallback() {

    override var stateCharacteristic: BluetoothGattCharacteristic? = null
    override var serverToClientCharacteristic: BluetoothGattCharacteristic? = null
    override var clientToServerCharacteristic: BluetoothGattCharacteristic? = null
    override var identCharacteristic: BluetoothGattCharacteristic? = null
    override var maxSupportedMtu: Int = MAX_MTU
    override var dataStream: ByteArrayOutputStream = ByteArrayOutputStream()
    override var executorEventListener: IExecutorEventListener? = null
    override var bluetoothGattService: BluetoothGattService? = null

    private var readyForNextFile: Boolean = true
    private var bluetoothGattServer: BluetoothGattServer? = null
    private var currentDevice: BluetoothDevice? = null
    private var requestId: Int = 0
    private var offset: Int = 0
    private var dataNotifier: DataNotifier? = null

    init {
        setup()
    }

    companion object {
        val LOG_TAG = "GattServer"
    }

    /**
     * Setup the Characteristics to be advertised
     * **/
    private fun setup() {
        try {

            bluetoothGattServer = bluetoothManager.openGattServer(context, this)
            bluetoothGattServer?.let {

                bluetoothGattService = BluetoothGattService(bleServiceCharacteristics.serviceUuid,
                    BluetoothGattService.SERVICE_TYPE_PRIMARY)

                // Server2Client
                serverToClientCharacteristic = bleServiceCharacteristics.server2ClientCharacteristic
                val descriptorServerToClient = BluetoothGattDescriptor(
                    UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG), PERMISSION_WRITE)
                serverToClientCharacteristic?.addDescriptor(descriptorServerToClient)
                bluetoothGattService?.addCharacteristic(serverToClientCharacteristic)

                // Client2Server
                clientToServerCharacteristic = bleServiceCharacteristics.client2ServerCharacteristic
                bluetoothGattService?.addCharacteristic(clientToServerCharacteristic)

                // State
                stateCharacteristic = bleServiceCharacteristics.stateCharacteristic
                val descriptorState = BluetoothGattDescriptor(
                    UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG), PERMISSION_WRITE)
                stateCharacteristic?.addDescriptor(descriptorState)
                bluetoothGattService?.addCharacteristic(stateCharacteristic)

                // Ident
                identCharacteristic = bleServiceCharacteristics.identCharacteristic
                bluetoothGattService?.addCharacteristic(identCharacteristic)
                identCharacteristic?.value = publicKeyHash

                Log.d(LOG_TAG, "Setup with Service: ${bluetoothGattService?.uuid}, " +
                        "serverToClientCharacteristic: ${serverToClientCharacteristic?.uuid} " +
                        "with Value: ${serverToClientCharacteristic?.value}, " +
                        "clientToServerCharacteristic: ${clientToServerCharacteristic?.uuid} " +
                        "with Value: ${clientToServerCharacteristic?.value}, " +
                        "stateCharacteristic: ${stateCharacteristic?.uuid} with Value: " +
                        "${stateCharacteristic?.value}, " +
                        "identCharacteristic: ${identCharacteristic?.uuid} with Value: " +
                        ByteUtils.bytesToHex(identCharacteristic?.value)
                )
            }
        } catch (ex: IllegalArgumentException) {
            Log.e(LOG_TAG, ex.message, ex)
        }
    }

    //region GattServiceInterface

    fun startServer() {
        getBluetoothGattServer().addService(bluetoothGattService)
        getPeripheralEventListener().onBLEEvent("Service UUID = ${bluetoothGattService?.uuid}",
            EventType.GATT_SERVICE_STARTED)
    }

    override fun onServiceAdded(status: Int, service: BluetoothGattService?) {

        if (bluetoothGattService == service) {
            if (BluetoothGatt.GATT_SUCCESS == status) {
                Log.d(javaClass.simpleName, "Service started.")
            } else {
                Log.d(javaClass.simpleName, "Service failed to start?: $status")
                getPeripheralEventListener().onBLEEvent("Service not successfully added", EventType.ERROR)
            }
        }
    }

    fun stopServer() {
        if (getBluetoothGattServer().getService(bluetoothGattService?.uuid) != null) {
            getBluetoothGattServer().removeService(bluetoothGattService)
        }
    }

    //region ITransportLayer
    fun setDelegate(executorEventListener: IExecutorEventListener) {
        this.executorEventListener = executorEventListener
    }

    fun close() {
        try {
            currentDevice.takeIf { it != null }.apply {
                getBluetoothGattServer().cancelConnection(currentDevice)
            }
            getBluetoothGattServer().clearServices()
            getBluetoothGattServer().close()
        } catch (ex: BluetoothException) {
            Log.e(LOG_TAG, ex.localizedMessage, ex)
        } catch (ex: NullPointerException) {
            Log.e(LOG_TAG, ex.localizedMessage, ex)
        }
        currentDevice = null
    }

    fun write(data: ByteArray, chunkSize: Int) {
        serverToClientCharacteristic?.let {
            dataNotifier = DataNotifier(data, chunkSize, getBluetoothGattServer(), it, getPeripheralEventListener(), currentDevice)
            val notifier = dataNotifier
            if (notifier != null)
                notifier.notifyInParts()
            else
                getPeripheralEventListener().onBLEEvent("dataNotifier is null", EventType.ERROR)
        }
    }
    //endregion

    fun writeToState(status: Byte) {
        Log.i(javaClass.simpleName, "Gatt server writeToState")
        stateCharacteristic?.value = byteArrayOf(status)
        val stateStatusWritten = getBluetoothGattServer().notifyCharacteristicChanged(currentDevice,stateCharacteristic,false)
        Log.i(javaClass.simpleName, "writeCharacteristic(${ByteUtils.bytesToHex(stateCharacteristic?.value)}): status = $status, Successfully written = $stateStatusWritten")
        if (!stateStatusWritten) {
            throw GattException("Could not write characteristic ${stateCharacteristic?.uuid} (value ${ByteUtils.bytesToHex(stateCharacteristic?.value)}.")
        }
    }

    //region BluetoothGattServerCallback


    @Synchronized
    override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
        Log.d(javaClass.simpleName, "onConnectionStateChange: ${deviceString(device)}, " +
                status + ", " + newState)
        //todo: check
        status.takeIf { it == BluetoothGatt.GATT_SUCCESS }?.apply {
            when(newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    if (currentDevice == null) {
                        currentDevice = device
                        getPeripheralEventListener().onBLEEvent("To device ${deviceString(device)}", EventType.GATT_CONNECTED)
                    }
                    else {
                        Log.i(javaClass.simpleName, "Already connected ${deviceString(device)}")
                    }
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    getPeripheralEventListener().onBLEEvent("Disconnected from remote device",
                            EventType.GATT_DISCONNECTED)
                }
            }
        }
    }

    @Synchronized
    override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
        this.maxSupportedMtu = mtu
    }

    @Synchronized
    override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int,
                                             characteristic: BluetoothGattCharacteristic
    ) {
        Log.i(javaClass.simpleName, "onCharacteristicReadRequest: ${characteristic.uuid}, " +
                "from ${deviceString(device)}, RequestId: $requestId, offset: $offset")

        when(characteristic.uuid) {
            identCharacteristic?.uuid -> {
                getBluetoothGattServer().sendResponse(device, requestId,
                    BluetoothGatt.GATT_SUCCESS, 0, publicKeyHash)
            }
        }
    }

    override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int,
                                              characteristic: BluetoothGattCharacteristic,
                                              preparedWrite: Boolean, responseNeeded: Boolean,
                                              offset: Int, value: ByteArray?) {
        if (device.address != currentDevice?.address) {
            Log.i(javaClass.simpleName, "Unexpected device; returning error")
            getPeripheralEventListener().onBLEEvent("Unexpected device; returning error", EventType.ERROR)
            getBluetoothGattServer().sendResponse(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, null)
            return
        }
        this.requestId = requestId
        Log.i(javaClass.simpleName, "onCharacteristicWriteRequest: ${characteristic.uuid}, " +
                "from ${deviceString(device)}, RequestId: $requestId, Offset: $offset, " +
                "PreparedWrite: $preparedWrite and value: ${ByteUtils.bytesToHex(value)}")
        if (characteristic.uuid == bleServiceCharacteristics.stateUuid) {
            decodeState(value, requestId)
            return
        }
        if (characteristic.uuid != bleServiceCharacteristics.client2ServerUuid) {
            Log.i(javaClass.simpleName, "Unexpected characteristic.")
            return
        }
        Log.i(javaClass.simpleName, "using offset ${this.offset}")
        this.offset = offset
        if (value == null) {
            throw TransportLayerException("Received clientToServerCharacteristic value is null")
        }
        Log.i(javaClass.simpleName, "Current value of clientToServerCharacteristic: ${ByteUtils.bytesToHex(value)}")
        if (isDataPending(value)) {
            getPeripheralEventListener().onBLEEvent("Further data awaited", EventType.TRANSFER_IN_PROGRESS)
            dataStream.write(decodeData(value))
        } else {
            dataStream.write(decodeData(value))
            val receivedData = dataStream.toByteArray()
            Log.i("GattServer", "Reached last chunk of data after splitting into mtu-sized chunks: ${ByteUtils.bytesToHex(receivedData)}")

            Log.i("GattServer", "Delegated (to ITransportEventListener) data: ${ByteUtils.bytesToHex(receivedData)}")
            getDelegate().onReceive(receivedData)
            dataStream.reset()
        }
    }

    @Synchronized
    override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
        Log.i(javaClass.simpleName, "onNotificationSent: device = $device, send status = $status")
        val notifier = dataNotifier
        if (notifier != null)
            notifier.notificationSentCalledBack()
        else
            getPeripheralEventListener().onBLEEvent("dataNotifier is null", EventType.ERROR)

    }

    private fun isDataPending(value: ByteArray): Boolean {
        return value[0] == DATA_PENDING
    }

    private fun decodeState(value: ByteArray?, requestId: Int) {
        if (value == null){
            throw GattException("Value of stateCharacteristic is null")
        }
        if (value.size != 1) {
            throw GattException("Invalid status")
        }
        if (value[0] == READY_FOR_TRANSMISSION) {
            getDelegate().onEvent(EventType.STATE_READY_FOR_TRANSMISSION.description, requestId)
            getPeripheralEventListener().onBLEEvent("Status updated", EventType.STATE_READY_FOR_TRANSMISSION)
        }
        if (value[0] == TERMINATE_TRANSMISSION) {
            getPeripheralEventListener().onBLEEvent("Status updated", EventType.GATT_DISCONNECTED)
        }
    }

    private fun getBluetoothGattServer(): BluetoothGattServer {
        return bluetoothGattServer ?: throw BluetoothException("bluetoothGattServer is null")
    }

    private fun getDelegate(): IExecutorEventListener {
        return executorEventListener ?: throw TransportLayerException("Delegate not set")
    }

    private fun getPeripheralEventListener() : BleEventListener {
        return bleEventListener ?: throw BluetoothException("Ble Event listener is null")
    }

    fun setReadyForNextFile(boolean: Boolean) {
        this.readyForNextFile = boolean
        
    }

    //endregion
}