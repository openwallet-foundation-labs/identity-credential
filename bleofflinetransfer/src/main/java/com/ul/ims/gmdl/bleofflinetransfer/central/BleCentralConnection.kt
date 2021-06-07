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

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.*
import android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ul.ims.gmdl.bleofflinetransfer.DEFAULT_SCAN_PERIOD
import com.ul.ims.gmdl.bleofflinetransfer.TERMINATE_TRANSMISSION
import com.ul.ims.gmdl.bleofflinetransfer.common.BleEventListener
import com.ul.ims.gmdl.bleofflinetransfer.config.ServiceCharacteristics
import com.ul.ims.gmdl.bleofflinetransfer.exceptions.BluetoothException
import com.ul.ims.gmdl.bleofflinetransfer.exceptions.GattException
import com.ul.ims.gmdl.bleofflinetransfer.exceptions.TransportLayerException
import com.ul.ims.gmdl.bleofflinetransfer.utils.ByteUtils
import com.ul.ims.gmdl.offlinetransfer.config.BleServiceMode
import com.ul.ims.gmdl.offlinetransfer.executorLayer.IExecutorEventListener
import com.ul.ims.gmdl.offlinetransfer.transportLayer.EventType
import com.ul.ims.gmdl.offlinetransfer.transportLayer.ITransportLayer

class BleCentralConnection(private val context: Context,
                           private val bleServiceCharacteristics: ServiceCharacteristics,
                           private val bluetoothAdapter : BluetoothAdapter?,
                           private val bleServiceMode: BleServiceMode
): ITransportLayer {

    override fun closeConnection() {
        Log.i(javaClass.simpleName, "closeConnection")
        writeToState(TERMINATE_TRANSMISSION)
        stopScan()
        stopTransfer()
    }

    companion object {
        val LOG_TAG = BleCentralConnection::class.java.simpleName
    }

    private val scanPeriod: Long = DEFAULT_SCAN_PERIOD
    private var executorEventListener: IExecutorEventListener? = null
    private var isScanning = false
    private var filters: List<ScanFilter>? = getScanFilters()
    private var settings: ScanSettings? = getScanSettings()
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private val callback: ScanCallback = getScanCallback()
    private var gattClient : GattClient? = null
    private var isTransferring = false
    private var selectedDevice : BluetoothDevice? = null
    private lateinit var centralEventListener : BleEventListener
    private var deviceList = mutableListOf<ScanResult>()
    private var scanHandler = Handler(Looper.getMainLooper())
    private var publicKeyHash : ByteArray? = null

    override fun inititalize(publicKeyHash: ByteArray) {
        this.publicKeyHash = publicKeyHash
        setup()
        startScan()
    }

    private fun setup() {
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    }

    fun verifyReaderIdent() {
        publicKeyHash?.let {pkHash ->
            Log.d(LOG_TAG, "mDL Public Key Hash = ${ByteUtils.bytesToHex(pkHash)}")

            getGattClient().identCharacteristic?.let {ident ->
                getGattClient().readCharacteristic(ident)
            }
        }
    }

    fun setEventDelegate(centralEventListener: BleEventListener) {
        this.centralEventListener = centralEventListener
    }

    private fun startScan() {
        deviceList.clear()

        scanHandler.postDelayed({ stopScan() }, scanPeriod)
        isScanning = true

        centralEventListener.onBLEEvent("Scanning ...", EventType.SCAN_STARTED)
        bluetoothLeScanner?.startScan(filters, settings , callback)
    }

    private fun stopScan() {
        try {
            if (!isScanning) {
                return
            }
            centralEventListener.onBLEEvent("Stopped scanning", EventType.SCAN_STOPPED)
            scanHandler.removeCallbacksAndMessages(null)
            bluetoothLeScanner?.flushPendingScanResults(callback)
            bluetoothLeScanner?.stopScan(callback)
            isScanning = false

            if (deviceList.count()>0) {
                deviceList.sortBy { it.rssi }
                selectedDevice = deviceList.last().device

                if (selectedDevice != null) {
                    centralEventListener.onBLEEvent("Device selected $selectedDevice", EventType.CAN_CONNECT)
                }
            }
            else {
                centralEventListener.onBLEEvent("No devices found that are suitable for this operation", EventType.NO_DEVICE_FOUND)
            }

        } catch (ex: IllegalStateException) {
            Log.e(LOG_TAG, ex.message, ex)
            centralEventListener.onBLEEvent("IllegalStateException", EventType.ERROR)
        }
    }

    private fun getScanSettings(): ScanSettings {
        return ScanSettings.Builder()
            .setReportDelay(0)
            .setCallbackType(CALLBACK_TYPE_ALL_MATCHES)
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
    }

    private fun getScanFilters(): List<ScanFilter> {
        Log.i(javaClass.simpleName, "Filtering on ${bleServiceCharacteristics.serviceUuid}" )
        return listOf(ScanFilter.Builder()
            .setServiceUuid(bleServiceCharacteristics.serviceParcelUuid)
            .build()
        )
    }

    fun startConnecting() {
        if (isTransferring) {
            return
        }
        isTransferring = true

        val device = selectedDevice ?: throw GattException("No device selected")

        Log.i(javaClass.simpleName,"Start connecting")

        gattClient = GattClient(bleServiceCharacteristics, publicKeyHash, bleServiceMode)
        getGattClient().setExecutorEventCallback(getTransportLayerDelegate())
        getGattClient().setBleEventCallback(centralEventListener)
        getGattClient().connect(context, device)
    }

    private fun stopTransfer() {
        Log.i(javaClass.simpleName, "Stop transfer")
        try {
            getGattClient().close()
        } catch (ex: BluetoothException) {
            Log.e(LOG_TAG, ex.localizedMessage, ex)
        }

        this.gattClient = null
        isTransferring = false
    }

    private fun getScanCallback(): ScanCallback {
        Log.i(javaClass.simpleName, "called")
        return object : ScanCallback() {

            override fun onScanResult(callbackType: Int, result: ScanResult) {
                Log.i(javaClass.simpleName,"Verifying device ${result.device.name} at ${result.device.address}")

                val scanResult = verifyResult(result)
                if (scanResult!=null) {
                    val existingDevice = deviceList.find { it.device.address == scanResult.device.address }
                    if (existingDevice == null) {
                        Log.i(javaClass.simpleName,"Candidate device found ${scanResult.device.name} at ${scanResult.device.address}")
                        deviceList.add(scanResult)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.d(javaClass.simpleName, "Scan failed: $errorCode")
            }
        }
    }

    private fun verifyResult(sr: ScanResult): ScanResult? {
        Log.d(javaClass.simpleName, "Found device ${sr.device.name} ( ${sr.device.address})")
        val foundServiceUUIDs = sr.scanRecord?.serviceUuids
        if (foundServiceUUIDs != null && foundServiceUUIDs.contains(bleServiceCharacteristics.serviceParcelUuid)) {
            return sr
        }
        else {
            Log.d(javaClass.simpleName, "Expected serviceUUID not found")
        }
        return null
    }

    //region Transport layer

    override fun write(data: ByteArray?) {
        val chunkSize = getGattClient().getMTU() - 4
        if (data == null) {
            throw TransportLayerException("Empty data")
        }
        getGattClient().write(data, chunkSize)
    }

    override fun setEventListener(eventListener: IExecutorEventListener) {
        this.executorEventListener = eventListener
    }

    private fun getTransportLayerDelegate() : IExecutorEventListener {
        return executorEventListener ?: throw BluetoothException("ITransportEventListener not set")
    }

    private fun getGattClient(): GattClient {
        return gattClient ?: throw BluetoothException("GattClient is null")
    }

    fun writeToState(status: Byte) {
        getGattClient().writeToState(status)
    }
}