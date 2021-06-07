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

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelUuid
import android.util.Log
import com.ul.ims.gmdl.bleofflinetransfer.TERMINATE_TRANSMISSION
import com.ul.ims.gmdl.bleofflinetransfer.common.BleEventListener
import com.ul.ims.gmdl.bleofflinetransfer.config.ServiceCharacteristics
import com.ul.ims.gmdl.bleofflinetransfer.exceptions.BluetoothDisabledException
import com.ul.ims.gmdl.bleofflinetransfer.exceptions.BluetoothException
import com.ul.ims.gmdl.bleofflinetransfer.exceptions.TransportLayerException
import com.ul.ims.gmdl.offlinetransfer.executorLayer.IExecutorEventListener
import com.ul.ims.gmdl.offlinetransfer.transportLayer.EventType
import com.ul.ims.gmdl.offlinetransfer.transportLayer.ITransportLayer

class BlePeripheralConnection(
    private val context: Context,
    private val bleServiceCharacteristics: ServiceCharacteristics,
    private val bluetoothManager: BluetoothManager?) : ITransportLayer {

    override fun closeConnection() {
        writeToState(TERMINATE_TRANSMISSION)
        stop()
    }

    override fun inititalize(publicKeyHash: ByteArray) {
        try {
            runSetup(publicKeyHash)
            start()

            executorEventListener?.let {
                getGattServer().setDelegate(it)
            }
        }
        catch (ex: BluetoothDisabledException) {
            val error = "BLE is not enabled"
            Log.e(javaClass.simpleName, error)
            peripheralEventListener.onBLEEvent(error, EventType.BT_OFF)
        }
    }

    companion object{
        val LOG_TAG = BlePeripheralConnection::class.java.simpleName
    }

    // Listener for internal BLE events (related to the transport layer)
    private lateinit var peripheralEventListener : BleEventListener

    // Listener to notify the executor layer that we've got data
    private var executorEventListener: IExecutorEventListener? = null

    // GattServer Obj
    private var gattServer: GattServer? = null

    // Advertisement Callback
    private val advCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(javaClass.simpleName, "Advertising started successfully. " +
                    "settings= ${settingsInEffect.toString()}")
            super.onStartSuccess(settingsInEffect)
        }
        override fun onStartFailure(errorCode: Int) {
            peripheralEventListener.onBLEEvent("Advertisement failed with error code " +
                    "$errorCode", EventType.ERROR)
        }
    }

    private val advertiser = bluetoothManager?.adapter?.bluetoothLeAdvertiser

    private var gattUpdateReceiver: BroadcastReceiver? = null

    fun setEventDelegate(peripheralEventListener: BleEventListener) {
        this.peripheralEventListener = peripheralEventListener
    }

    private fun runSetup(publicKeyHash : ByteArray) {
        bluetoothManager?.let {
            gattServer = GattServer(context,bluetoothManager,
                bleServiceCharacteristics, publicKeyHash, peripheralEventListener)
        }
    }

    private fun start() {
        if (gattServer == null){
            throw BluetoothException("No gatt server")
        }

        getGattServer().startServer()

        startAdvertise()
    }

    private fun startAdvertise() {
        val advSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val advData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(bleServiceCharacteristics.serviceUuid))
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        advertiser?.startAdvertising(advSettings, advData, scanResponse, advCallback)

        gattUpdateReceiver = object : BroadcastReceiver() {

            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                Log.d("onReceive", "action = $action")

                when(action) {
                    BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
                        peripheralEventListener.onBLEEvent("BLE is off", EventType.ERROR)
                    }
                }
            }
        }

        context.registerReceiver(gattUpdateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    }

    private fun stopAdvertise() {
        advertiser?.stopAdvertising(advCallback)
        try {
            context.unregisterReceiver(gattUpdateReceiver)
        } catch (e: IllegalArgumentException) {
            // Ignore error when trying to unregister receiver
            Log.e(LOG_TAG, "Ignored error: ${e.message}")
        }
    }

    fun stop() {

        try {

            stopAdvertise()
            getGattServer().stopServer()
            getGattServer().close()

            Log.d(javaClass.simpleName, "Stopped GatServer serviceUuid")
        } catch (ex: BluetoothException) {
            Log.e(LOG_TAG, ex.localizedMessage, ex)
        } catch (ex: IllegalArgumentException) {
            Log.e(LOG_TAG, ex.localizedMessage, ex)
        }
    }

    override fun write(data: ByteArray?) {
        val chunkSize = getGattServer().maxSupportedMtu - 4
        if (data == null) {
            throw TransportLayerException("Empty data")
        }
        getGattServer().write(data, chunkSize)
    }

    override fun setEventListener(eventListener: IExecutorEventListener) {
        this.executorEventListener = eventListener
    }

    private fun getGattServer(): GattServer {
        return gattServer ?: throw BluetoothException("GattServer is null")
    }

    fun readingComplete() {
        getGattServer().writeToState(TERMINATE_TRANSMISSION)
    }

    fun writeToState(byte : Byte) {
        getGattServer().writeToState(byte)
    }

    fun setReadyForNextFile(boolean: Boolean) {
        getGattServer().setReadyForNextFile(boolean)
    }
}