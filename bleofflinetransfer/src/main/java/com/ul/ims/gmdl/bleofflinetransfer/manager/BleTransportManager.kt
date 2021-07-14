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

package com.ul.ims.gmdl.bleofflinetransfer.manager

import android.content.Context
import android.util.Log
import com.ul.ims.gmdl.bleofflinetransfer.R
import com.ul.ims.gmdl.bleofflinetransfer.READY_FOR_TRANSMISSION
import com.ul.ims.gmdl.bleofflinetransfer.TERMINATE_TRANSMISSION
import com.ul.ims.gmdl.bleofflinetransfer.central.BleCentralConnection
import com.ul.ims.gmdl.bleofflinetransfer.common.BleEventListener
import com.ul.ims.gmdl.bleofflinetransfer.config.BleTransportConfigurations
import com.ul.ims.gmdl.bleofflinetransfer.exceptions.BluetoothException
import com.ul.ims.gmdl.bleofflinetransfer.exceptions.TransportLayerException
import com.ul.ims.gmdl.bleofflinetransfer.peripheral.BlePeripheralConnection
import com.ul.ims.gmdl.bleofflinetransfer.utils.BleUtils
import com.ul.ims.gmdl.offlinetransfer.config.AppMode
import com.ul.ims.gmdl.offlinetransfer.config.BleServiceMode
import com.ul.ims.gmdl.offlinetransfer.transportLayer.EventType
import com.ul.ims.gmdl.offlinetransfer.transportLayer.ITransportEventListener
import com.ul.ims.gmdl.offlinetransfer.transportLayer.ITransportLayer
import com.ul.ims.gmdl.offlinetransfer.transportLayer.TransportManager
import java.util.*

/**
 * BLE specific transport manager
 */
class BleTransportManager(
    private val context: Context,
    private val appMode: AppMode,
    private val bleServiceMode: BleServiceMode,
    private var bleUUID: UUID?
) : BleEventListener, TransportManager {

    private var readyForNextFile: Boolean = false
    private var centralBleConnection : BleCentralConnection? = null
    private var peripheralBleConnection : BlePeripheralConnection? = null
    private var transportEventListener: ITransportEventListener? = null

    init {
        setupTransportLayers()
    }

    override fun setTransportProgressListener(transportEventListener: ITransportEventListener) {
        this.transportEventListener = transportEventListener
    }

    companion object {
        const val TAG = "BleTransportManager"
    }

    private fun setupTransportLayers() {
        val uuid = bleUUID ?: UUID.randomUUID()
        bleUUID = uuid
        val bleServiceCharacteristics =
            BleTransportConfigurations(bleServiceMode, uuid).getBleServiceCharacteristics()

        when (bleServiceMode) {
            BleServiceMode.CENTRAL_CLIENT_MODE -> {
                Log.d(
                    TAG, String.format(
                        context.resources.getString(R.string.setup_central_client), appMode
                    )
                )
                when (appMode) {
                    AppMode.HOLDER -> {
                        centralBleConnection = BleCentralConnection(
                            context,
                            bleServiceCharacteristics, BleUtils.getBluetoothAdapter(context),
                            bleServiceMode
                        )
                        getCentralConnection().setEventDelegate(this)
                    }
                    AppMode.VERIFIER -> {
                        peripheralBleConnection = BlePeripheralConnection(
                            context,
                            bleServiceCharacteristics, BleUtils.getBluetoothManager(context)
                        )
                        getPeripheralConnection().setEventDelegate(this)
                    }
                }
            }
            BleServiceMode.PERIPHERAL_SERVER_MODE -> {
                Log.d(
                    TAG, String.format(
                        context.resources.getString(R.string.setup_peripheral_server), appMode
                    )
                )
                when (appMode) {
                    AppMode.HOLDER -> {
                        peripheralBleConnection = BlePeripheralConnection(
                            context,
                            bleServiceCharacteristics, BleUtils.getBluetoothManager(context)
                        )
                        getPeripheralConnection().setEventDelegate(this)
                    }
                    AppMode.VERIFIER -> {
                        centralBleConnection = BleCentralConnection(
                            context,
                            bleServiceCharacteristics, BleUtils.getBluetoothAdapter(context),
                            bleServiceMode
                        )
                        getCentralConnection().setEventDelegate(this)
                    }
                }
            }
            BleServiceMode.UNKNOWN -> {
                throw BluetoothException("Unknown Bluetooth LE Role, cannot set up transport manager")
            }
        }
    }

    override fun onBLEEvent(string: String, eventType: EventType) {

        when(bleServiceMode) {
            BleServiceMode.CENTRAL_CLIENT_MODE -> {
                when (appMode) {
                    AppMode.HOLDER -> {
                        onCentralEvent(string, eventType)
                    }
                    AppMode.VERIFIER -> {
                        onPeripheralEvent(string, eventType)
                    }
                }
            }
            BleServiceMode.PERIPHERAL_SERVER_MODE -> {
                when (appMode) {
                    AppMode.HOLDER -> {
                        onPeripheralEvent(string, eventType)
                    }
                    AppMode.VERIFIER -> {
                        onCentralEvent(string, eventType)
                    }
                }
            }
        }
    }

    private fun onPeripheralEvent(string: String, eventType: EventType) {
        Log.i("onPeripheralEvent", "$eventType: $string")

        when (eventType) {
            EventType.STATE_READY_FOR_TRANSMISSION -> {
                getTransportProgressDelegate().onEvent(
                    EventType.STATE_READY_FOR_TRANSMISSION,
                    EventType.STATE_READY_FOR_TRANSMISSION.description)
            }
            EventType.TRANSFER_IN_PROGRESS -> {
                getTransportProgressDelegate().onEvent(
                    EventType.TRANSFER_IN_PROGRESS,
                    EventType.TRANSFER_IN_PROGRESS.description)
            }
            EventType.STATE_TERMINATE_TRANSMISSION -> {
                getTransportProgressDelegate().onEvent(
                    EventType.TRANSFER_COMPLETE,
                    EventType.TRANSFER_COMPLETE.description
                )
                getPeripheralConnection().writeToState(TERMINATE_TRANSMISSION)
                getTransportLayer().closeConnection()
            }
            EventType.ERROR, EventType.GATT_DISCONNECTED -> {
                getTransportProgressDelegate().onEvent(
                    EventType.TRANSFER_COMPLETE,
                    EventType.TRANSFER_COMPLETE.description
                )
                getPeripheralConnection().closeConnection()
            }

            else -> Log.i(javaClass.simpleName, "$eventType: $string")
        }
    }

    private fun onCentralEvent(string: String, eventType: EventType) {
        Log.i("onCentralEvent", "$eventType: $string")

        when(eventType) {
            EventType.CAN_CONNECT -> {
                getCentralConnection().startConnecting()
            }
            EventType.SERVICE_CONNECTED -> {
                getCentralConnection().verifyReaderIdent()
            }
            EventType.VERIFIER_VERIFIED -> {
                // This action triggers the request to be sent by the Verifier
                getCentralConnection().writeToState(READY_FOR_TRANSMISSION)
                getTransportProgressDelegate().onEvent(EventType.GATT_CONNECTED,
                    EventType.GATT_CONNECTED.description)

                if (bleServiceMode == BleServiceMode.PERIPHERAL_SERVER_MODE) {
                    getTransportProgressDelegate().onEvent(
                        EventType.STATE_READY_FOR_TRANSMISSION,
                        EventType.STATE_READY_FOR_TRANSMISSION.description)
                }
            }
            EventType.TRANSFER_IN_PROGRESS -> {
                getTransportProgressDelegate().onEvent(
                    EventType.TRANSFER_IN_PROGRESS,
                    EventType.TRANSFER_IN_PROGRESS.description)
            }
            EventType.STATE_TERMINATE_TRANSMISSION -> {
                getTransportLayer().closeConnection()
            }
            EventType.NO_DEVICE_FOUND -> {
                getTransportProgressDelegate().onEvent(
                    EventType.NO_DEVICE_FOUND,
                    EventType.NO_DEVICE_FOUND.description)
            }

            EventType.GATT_DISCONNECTED -> {
                getTransportLayer().closeConnection()
            }

            EventType.ERROR -> {
                getTransportProgressDelegate().onEvent(
                    EventType.ERROR,
                    EventType.ERROR.description)
            }
            else -> Log.i(javaClass.simpleName, "$eventType: $string")
        }
    }

    override fun getTransportLayer(): ITransportLayer {
        return when (bleServiceMode) {
            BleServiceMode.CENTRAL_CLIENT_MODE -> {
                when (appMode) {
                    AppMode.HOLDER -> getCentralConnection()
                    AppMode.VERIFIER -> getPeripheralConnection()
                }
            }
            BleServiceMode.PERIPHERAL_SERVER_MODE -> {
                when (appMode) {
                    AppMode.HOLDER -> getPeripheralConnection()
                    AppMode.VERIFIER -> getCentralConnection()
                }
            }
            BleServiceMode.UNKNOWN -> {
                throw BluetoothException("Unknown BLE service mode")
            }
        }
    }

    private fun getCentralConnection(): BleCentralConnection {
        return centralBleConnection ?: throw BluetoothException("Transport layer is null")
    }

    private fun getPeripheralConnection(): BlePeripheralConnection{
        return peripheralBleConnection ?: throw BluetoothException("Transport layer is null")
    }

    override fun setReadyForNextFile(boolean: Boolean) {
        this.readyForNextFile = boolean
        getPeripheralConnection().setReadyForNextFile(boolean)
        Log.i(javaClass.simpleName, "Ready to read next file.")
    }

    private fun getTransportProgressDelegate(): ITransportEventListener {
        return transportEventListener ?: throw TransportLayerException("TransportProgressDelegate is null")
    }
}