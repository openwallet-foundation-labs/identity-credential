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

package com.ul.ims.gmdl.reader.offlineTransfer

import android.content.Context
import com.ul.ims.gmdl.cbordata.security.CoseKey
import com.ul.ims.gmdl.offlinetransfer.config.AppMode
import com.ul.ims.gmdl.offlinetransfer.config.BleServiceMode
import com.ul.ims.gmdl.offlinetransfer.data.DataTypes
import com.ul.ims.gmdl.offlinetransfer.transportLayer.TransferChannels

class OfflineTransferManager {
    class Builder {
        private var context: Context? = null
        private var actAs: AppMode? = null

        // Default data type is CBOR
        private var dataType: DataTypes = DataTypes.CBOR

        // Default transfer method is BLE
        private var transferChannel: TransferChannels = TransferChannels.BLE

        // So far the supported mode is only mDL Central Client
        private var bleServiceMode: BleServiceMode? = BleServiceMode.PERIPHERAL_SERVER_MODE

        // Wifi passphrase is only set on NFC engagement
        private var wifiPassphrase: String? = null

        // coseKey
        private var coseKey: CoseKey? = null

        fun setCoseKey(coseKey: CoseKey) = apply {
            this.coseKey = coseKey
        }

        fun setTransferChannel(transferChannel: TransferChannels) = apply {
            this.transferChannel = transferChannel
        }

        fun setDataType(dataType: DataTypes) = apply {
            this.dataType = dataType
        }

        fun setContext(context: Context) = apply {
            this.context = context
        }

        fun actAs(actAs: AppMode) = apply {
            this.actAs = actAs
        }

        fun setBleServiceMode(bleServiceMode: BleServiceMode) = apply {
            this.bleServiceMode = bleServiceMode
        }

        fun build(): CborManager {
            context?.let { ctx ->
                actAs?.let { actor ->
                    bleServiceMode?.let { bleMode ->
                        coseKey?.let { ck ->
                            return when (dataType) {
                                DataTypes.CBOR -> CborManager(
                                    ctx,
                                    actor,
                                    transferChannel,
                                    bleMode,
                                    ck.getPublicKey().encoded,
                                    wifiPassphrase
                                )
                                else -> throw IllegalArgumentException("Mandatory fields not set")
                            }
                        }
                    }
                }
            }
            throw IllegalArgumentException("Mandatory fields not set")
        }

        fun setWifiPassphrase(wifiPassphrase: String?) = apply {
            this.wifiPassphrase = wifiPassphrase
        }
    }
}