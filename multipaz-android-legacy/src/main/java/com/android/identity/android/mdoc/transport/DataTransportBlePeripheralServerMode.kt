/*
 * Copyright 2022 The Android Open Source Project
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
package com.android.identity.android.mdoc.transport

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import androidx.annotation.RequiresApi
import org.multipaz.mdoc.connectionmethod.ConnectionMethodBle
import org.multipaz.util.Logger
import org.multipaz.util.toJavaUuid
import java.util.UUID

/**
 * BLE data transport implementation conforming to ISO 18013-5 mdoc
 * peripheral server mode.
 */
class DataTransportBlePeripheralServerMode(
    context: Context,
    role: Role,
    connectionMethod: ConnectionMethodBle,
    options: DataTransportOptions
) : DataTransportBle(context, role, connectionMethod, options) {
    private var characteristicStateUuid = UUID.fromString("00000001-a123-48ce-896b-4c76973373e6")
    private var characteristicClient2ServerUuid = UUID.fromString("00000002-a123-48ce-896b-4c76973373e6")
    private var characteristicServer2ClientUuid = UUID.fromString("00000003-a123-48ce-896b-4c76973373e6")

    // Note: Ident UUID not used in peripheral server mode
    /**
     * In _mdoc peripheral server mode_ the _mdoc_ acts as the GATT server and the _mdoc reader_ acts as the
     * GATT client. According to ISO 18013-5 Table A.1 this means that in _mdoc peripheral server mode_
     * the GATT server (for the _mdoc_) should advertise
     * UUID 0000000A-A123-48CE896B-4C76973373E6 and the GATT client (for the _mdoc reader_) should
     * connect to that UUID.
     */
    private var characteristicL2CAPUuidMdoc = UUID.fromString("0000000a-a123-48ce-896b-4c76973373e6")
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var gattClient: GattClient? = null
    private var scanner: BluetoothLeScanner? = null
    private var encodedEDeviceKeyBytes: ByteArray? = null
    private var timeScanningStartedMillis: Long = 0

    /**
     * Callback to receive information about the advertisement process.
     */
    private var advertiseCallback: AdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {}
        override fun onStartFailure(errorCode: Int) {
            reportError(Error("BLE advertise failed with error code $errorCode"))
        }
    }

    // a flag to prevent multiple GattClient connects which cause to multiple
    // new GattClient instances and to crashes
    // https://stackoverflow.com/a/38276808/4940838
    private var isConnecting = false

    private var scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Logger.d(TAG, "onScanCallback: callbackType=$callbackType result=$result")
            // if we already scanned and connect to device we don't want to
            // reconnect to another GattClient instance.
            if (isConnecting) {
                return
            }
            isConnecting = true
            scanningTimeMillis = System.currentTimeMillis() - timeScanningStartedMillis
            Logger.i(TAG, "Scanned for $scanningTimeMillis" + " milliseconds. "
                        + "Connecting to device with address ${result.device.address}")
            connectToDevice(result.device)
            if (scanner != null) {
                Logger.d(TAG, "Stopped scanning for UUID $serviceUuid")
                try {
                    scanner!!.stopScan(this)
                } catch (e: SecurityException) {
                    reportError(e)
                }
                scanner = null
            }
            // TODO: Investigate. When testing with Reader C (which is on iOS) we get two callbacks
            //  and thus a NullPointerException when calling stopScan().
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            Logger.w(TAG, "Ignoring unexpected onBatchScanResults")
        }

        override fun onScanFailed(errorCode: Int) {
            reportError(Error("BLE scan failed with error code $errorCode"))
        }
    }
    
    private var gattServer: GattServer? = null
    private var l2capClient: L2CAPClient? = null
    
    private fun connectToDevice(device: BluetoothDevice) {
        reportConnecting()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            options.bleUseL2CAP &&
            connectionMethod.peripheralServerModePsm != null
        ) {
            Logger.i(
                TAG, "Have L2CAP PSM from engagement, connecting directly " +
                        "(psm = ${connectionMethod.peripheralServerModePsm})"
            )
            connectL2CAP(device, connectionMethod.peripheralServerModePsm!!)
        } else {
            connectGatt(device)
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private fun connectL2CAP(device: BluetoothDevice, psm: Int) {
        l2capClient = L2CAPClient(context, object : L2CAPClient.Listener {
            override fun onPeerConnected() {
                reportConnected()
            }

            override fun onPeerDisconnected() {
                reportDisconnected()
            }

            override fun onMessageReceived(data: ByteArray) {
                reportMessageReceived(data)
            }

            override fun onError(error: Throwable) {
                reportError(error)
            }
        })
        l2capClient!!.connect(device, psm)
    }

    private fun connectGatt(device: BluetoothDevice) {
        var characteristicL2CAPUuid: UUID? = null
        if (options.bleUseL2CAP) {
            characteristicL2CAPUuid = characteristicL2CAPUuidMdoc
        }
        gattClient = GattClient(
            context,
            serviceUuid!!.toJavaUuid(), encodedEDeviceKeyBytes,
            characteristicStateUuid, characteristicClient2ServerUuid,
            characteristicServer2ClientUuid, null,
            characteristicL2CAPUuid
        )
        gattClient!!.listener = object : GattClient.Listener {
            override fun onPeerConnected() {
                reportConnected()
            }

            override fun onPeerDisconnected() {
                if (gattClient != null) {
                    gattClient!!.listener = null
                    gattClient!!.disconnect()
                    gattClient = null
                }
                reportDisconnected()
            }

            override fun onMessageReceived(data: ByteArray) {
                reportMessageReceived(data)
            }

            override fun onTransportSpecificSessionTermination() {
                reportTransportSpecificSessionTermination()
            }

            override fun onError(error: Throwable) {
                reportError(error)
            }
        }
        gattClient!!.clearCache = options.bleClearCache
        gattClient!!.connect(device)
    }

    override fun setEDeviceKeyBytes(encodedEDeviceKeyBytes: ByteArray) {
        this.encodedEDeviceKeyBytes = encodedEDeviceKeyBytes
    }

    private fun connectAsMdoc() {
        val bluetoothManager = context.getSystemService(
            BluetoothManager::class.java
        )
        val bluetoothAdapter = bluetoothManager.adapter

        // TODO: It would be nice if we got get the MAC address that will be assigned to
        //  this advertisement so we can send it to the mDL reader, out of band. Android
        //  currently doesn't have any APIs to do this but it's possible this could be
        //  added without violating the security/privacy goals behind removing identifiers.
        //

        // TODO: Check if BLE is enabled and error out if not so...
        var characteristicL2CAPUuid: UUID? = null
        if (options.bleUseL2CAP) {
            characteristicL2CAPUuid = characteristicL2CAPUuidMdoc
        }
        gattServer = GattServer(
            context, bluetoothManager, serviceUuid!!.toJavaUuid(),
            encodedEDeviceKeyBytes,
            characteristicStateUuid, characteristicClient2ServerUuid,
            characteristicServer2ClientUuid, null,
            characteristicL2CAPUuid
        )
        gattServer!!.listener = object : GattServer.Listener {
            override fun onPeerConnected() {
                Logger.d(TAG, "onPeerConnected")
                reportConnected()
                // No need to advertise anymore since we now have a client...
                if (bluetoothLeAdvertiser != null) {
                    Logger.d(TAG, "Stopping advertising UUID $serviceUuid")
                    try {
                        bluetoothLeAdvertiser!!.stopAdvertising(advertiseCallback)
                    } catch (e: SecurityException) {
                        reportError(e)
                    }
                    bluetoothLeAdvertiser = null
                }
            }

            override fun onPeerDisconnected() {
                Logger.d(TAG, "onPeerDisconnected")
                reportDisconnected()
            }

            override fun onMessageReceived(data: ByteArray) {
                Logger.d(TAG, "onMessageReceived")
                reportMessageReceived(data)
            }

            override fun onTransportSpecificSessionTermination() {
                Logger.d(TAG, "onTransportSpecificSessionTermination")
                reportTransportSpecificSessionTermination()
            }

            override fun onError(error: Throwable) {
                Logger.d(TAG, "onError", error)
                reportError(error)
            }
        }
        if (!gattServer!!.start()) {
            reportError(Error("Error starting Gatt Server"))
            gattServer!!.stop()
            gattServer = null
            return
        }
        connectionMethodToReturn.peripheralServerModePsm = gattServer!!.psm
        bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (bluetoothLeAdvertiser == null) {
            reportError(Error("Failed to create BLE advertiser"))
            gattServer!!.stop()
            gattServer = null
        } else {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()
            val data = AdvertiseData.Builder()
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(ParcelUuid(serviceUuid!!.toJavaUuid()))
                .build()
            Logger.d(TAG, "Started advertising UUID $serviceUuid")
            try {
                bluetoothLeAdvertiser!!.startAdvertising(settings, data, advertiseCallback)
            } catch (e: SecurityException) {
                reportError(e)
            }
        }
    }

    private fun connectAsMdocReader() {
        // Start scanning...
        bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager!!.getAdapter()
        val macAddress = connectionMethod.peripheralServerModeMacAddress
        if (macAddress != null) {
            Logger.i(TAG, "MAC address provided, no scanning needed")
            val device = bluetoothAdapter.getRemoteDevice(macAddress)
            connectToDevice(device)
            return
        }
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUuid!!.toJavaUuid()))
            .build()
        val settings = ScanSettings.Builder()
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        timeScanningStartedMillis = System.currentTimeMillis()
        Logger.d(TAG, "Started scanning for UUID $serviceUuid")
        scanner = bluetoothAdapter.bluetoothLeScanner
        val filterList: MutableList<ScanFilter> = ArrayList()
        filterList.add(filter)
        try {
            scanner!!.startScan(filterList, settings, scanCallback)
        } catch (e: SecurityException) {
            reportError(e)
        }
    }

    override fun connect() {
        // TODO: Check if BLE is enabled and error out if not so...
        if (role === Role.MDOC) {
            connectAsMdoc()
        } else {
            connectAsMdocReader()
        }
    }

    override fun close() {
        inhibitCallbacks()
        if (bluetoothLeAdvertiser != null) {
            Logger.d(TAG, "Stopping advertising UUID $serviceUuid")
            try {
                bluetoothLeAdvertiser!!.stopAdvertising(advertiseCallback)
            } catch (e: SecurityException) {
                Logger.e(TAG, "Caught SecurityException while shutting down: $e")
            }
            bluetoothLeAdvertiser = null
        }
        if (gattServer != null) {
            gattServer!!.listener = null
            gattServer!!.stop()
            gattServer = null
        }
        if (scanner != null) {
            try {
                scanner!!.stopScan(scanCallback)
            } catch (e: SecurityException) {
                Logger.e(TAG, "Caught SecurityException while shutting down: $e")
            }
            scanner = null
        }
        if (gattClient != null) {
            gattClient!!.listener = null
            gattClient!!.disconnect()
            gattClient = null
        }
        if (l2capClient != null) {
            l2capClient!!.disconnect()
            l2capClient = null
        }
    }

    override fun sendMessage(data: ByteArray) {
        require(data.size != 0) { "Data to send cannot be empty" }
        if (l2capClient != null) {
            l2capClient!!.sendMessage(data)
        } else if (gattServer != null) {
            gattServer!!.sendMessage(data)
        } else if (gattClient != null) {
            gattClient!!.sendMessage(data)
        }
    }

    override fun sendTransportSpecificTerminationMessage() {
        if (gattServer == null) {
            if (gattClient == null) {
                reportError(Error("Transport-specific termination not available"))
                return
            }
            gattClient!!.sendTransportSpecificTermination()
            return
        }
        gattServer!!.sendTransportSpecificTermination()
    }

    override fun supportsTransportSpecificTerminationMessage(): Boolean {
        if (gattServer != null) {
            return gattServer!!.supportsTransportSpecificTerminationMessage()
        } else if (gattClient != null) {
            return gattClient!!.supportsTransportSpecificTerminationMessage()
        }
        return false
    }

    companion object {
        private const val TAG = "DataTransportBlePSM" // limit to <= 23 chars
    }
}
