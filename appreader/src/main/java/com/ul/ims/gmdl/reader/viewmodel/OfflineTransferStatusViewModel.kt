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

package com.ul.ims.gmdl.reader.viewmodel

import android.app.Application
import android.nfc.Tag
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.ul.ims.gmdl.bleofflinetransfer.utils.BleUtils
import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement
import com.ul.ims.gmdl.cbordata.request.DataElements
import com.ul.ims.gmdl.cbordata.security.CoseKey
import com.ul.ims.gmdl.cbordata.security.mdlauthentication.Handover
import com.ul.ims.gmdl.offlinetransfer.appLayer.IofflineTransfer
import com.ul.ims.gmdl.offlinetransfer.config.AppMode
import com.ul.ims.gmdl.offlinetransfer.config.BleServiceMode
import com.ul.ims.gmdl.offlinetransfer.data.DataTypes
import com.ul.ims.gmdl.offlinetransfer.transportLayer.TransferChannels
import com.ul.ims.gmdl.offlinetransfer.utils.Resource
import com.ul.ims.gmdl.reader.offlineTransfer.OfflineTransferManager
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.util.*

class OfflineTransferStatusViewModel(val app: Application) : AndroidViewModel(app) {

    private var offlineTransferVerifier: IofflineTransfer? = null
    private var coseKey: CoseKey? = null
    private var liveDataMerger = MediatorLiveData<Resource<Any>>()
    private var bleServiceMode: BleServiceMode? = null
    private var bleUUID: UUID? = null

    fun setupWiFiVerifier(
        deviceEngagement: DeviceEngagement,
        handover: Handover,
        requestItems: DataElements,
        wifiPassphrase: String?
    ) {
        doAsync {
            coseKey = deviceEngagement.security?.coseKey

            coseKey?.let { key ->
                val builder = OfflineTransferManager.Builder()
                    .actAs(AppMode.VERIFIER)
                    .setContext(app.applicationContext)
                    .setDataType(DataTypes.CBOR)
                    .setTransferChannel(TransferChannels.WiFiAware)
                    .setWifiPassphrase(wifiPassphrase)
                    .setCoseKey(key)

                offlineTransferVerifier = builder.build()
                offlineTransferVerifier?.setupVerifier(key, requestItems, deviceEngagement, handover)

                uiThread {
                    offlineTransferVerifier?.data?.let { livedata ->
                        liveDataMerger.addSource(livedata) {
                            liveDataMerger.value = it
                        }
                    }
                }
            }
        }
    }

    fun setupNfcVerifier(
        deviceEngagement: DeviceEngagement,
        handover: Handover,
        requestItems: DataElements,
        tag: Tag,
        apduCommandLength: Int
    ) {
        doAsync {
            coseKey = deviceEngagement.security?.coseKey

            coseKey?.let { key ->
                val builder = OfflineTransferManager.Builder()
                    .actAs(AppMode.VERIFIER)
                    .setContext(app.applicationContext)
                    .setDataType(DataTypes.CBOR)
                    .setTransferChannel(TransferChannels.NFC)
                    .setCoseKey(key)
                    .setNfcTag(tag)
                    .setApduCommandLength(apduCommandLength)

                offlineTransferVerifier = builder.build()
                offlineTransferVerifier?.setupVerifier(key, requestItems, deviceEngagement, handover)

                uiThread {
                    offlineTransferVerifier?.data?.let { livedata ->
                        liveDataMerger.addSource(livedata) {
                            liveDataMerger.value = it
                        }
                    }
                }
            }
        }
    }

    fun setupBleVerifier(
        deviceEngagement: DeviceEngagement,
        handover: Handover,
        requestItems: DataElements,
        bleRole: BleServiceMode
    ) {
        doAsync {
            coseKey = deviceEngagement.security?.coseKey

            coseKey?.let { key ->
                val builder = OfflineTransferManager.Builder()
                    .actAs(AppMode.VERIFIER)
                    .setContext(app.applicationContext)
                    .setDataType(DataTypes.CBOR)
                    .setTransferChannel(TransferChannels.BLE)
                    .setCoseKey(key)

                // If BleServiceMode has not been determined, infer from DeviceEngagement structure
                if (bleRole == BleServiceMode.UNKNOWN) {
                    val bleTransportMethod = deviceEngagement.getBLETransferMethod()
                    bleTransportMethod?.let { bleTransport ->
                        // both modes are supported

                        if (bleTransport.retrievalOptions?.centralClient == true &&
                            bleTransport.retrievalOptions?.peripheralServer == true
                        ) {
                            // When the mDL supports both modes, the mDL reader should act as BLE central mode.
                            bleServiceMode = BleServiceMode.PERIPHERAL_SERVER_MODE
                            bleUUID = bleTransport.retrievalOptions?.peripheralServerUUID
                        } else {
                            // only central client mode supported
                            if (bleTransport.retrievalOptions?.centralClient == true) {
                                bleServiceMode = BleServiceMode.CENTRAL_CLIENT_MODE
                                bleUUID = bleTransport.retrievalOptions?.centralClientUUID
                            } else {
                                // only peripheral server mode supported
                                bleServiceMode = BleServiceMode.PERIPHERAL_SERVER_MODE
                                bleUUID = bleTransport.retrievalOptions?.peripheralServerUUID
                            }
                        }
                    }
                } else {
                    bleServiceMode = bleRole
                }

                bleServiceMode?.let { bleMode ->
                    if (isBleModeSupported(bleMode)) {
                        builder.setBleServiceMode(bleMode)
                        builder.setBleUUID(bleUUID)

                        offlineTransferVerifier = builder.build()
                        offlineTransferVerifier?.setupVerifier(key, requestItems, deviceEngagement, handover)

                        uiThread {
                            offlineTransferVerifier?.data?.let { livedata ->
                                liveDataMerger.addSource(livedata) {
                                    liveDataMerger.value = it
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun getTransferData(): LiveData<Resource<Any>>? {
        return liveDataMerger
    }

    fun tearDown() {
        doAsync {
            offlineTransferVerifier?.tearDown()
        }
    }

    private fun isBleModeSupported(bleServiceMode: BleServiceMode): Boolean {
        return when (bleServiceMode) {
            BleServiceMode.PERIPHERAL_SERVER_MODE -> {
                BleUtils.isPeripheralSupported(app.applicationContext)
            }
            BleServiceMode.CENTRAL_CLIENT_MODE -> {
                BleUtils.isCentralModeSupported(app.applicationContext)
            }
            BleServiceMode.UNKNOWN -> false
        }
    }
}