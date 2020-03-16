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

package com.ul.ims.gmdl.bleofflinetransfer.common

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import com.ul.ims.gmdl.offlinetransfer.executorLayer.IExecutorEventListener
import java.io.ByteArrayOutputStream

interface GattCommon {

    // BLE Characteristics as per ISO Spec
    var stateCharacteristic: BluetoothGattCharacteristic?
    var serverToClientCharacteristic: BluetoothGattCharacteristic?
    var clientToServerCharacteristic: BluetoothGattCharacteristic?
    var identCharacteristic: BluetoothGattCharacteristic?

    // MTU Size
    var maxSupportedMtu: Int

    // Object used to hold the received bytes after remove the first bit
    var dataStream: ByteArrayOutputStream

    // Internal Listener (BleCentralConnection, BlePeripheralConnection)
    var bleEventListener: BleEventListener?

    // Executor Listener (HolderExecutor, ListenerExecutor)
    var executorEventListener: IExecutorEventListener?

    // BluetoothGattService object
    var bluetoothGattService : BluetoothGattService?
}