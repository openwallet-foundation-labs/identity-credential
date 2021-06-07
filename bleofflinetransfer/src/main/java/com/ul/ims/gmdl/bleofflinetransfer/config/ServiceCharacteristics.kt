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

package com.ul.ims.gmdl.bleofflinetransfer.config

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattDescriptor.*
import android.os.ParcelUuid
import java.util.*

data class ServiceCharacteristics(
    val serviceUuid : UUID,
    val stateUuid : UUID,
    val client2ServerUuid : UUID,
    val server2ClientUuid : UUID,
    val identUuid : UUID
) {
    val serviceParcelUuid = ParcelUuid(serviceUuid)

    val server2ClientCharacteristic = BluetoothGattCharacteristic(
        server2ClientUuid,
        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_WRITE
    )
//        .apply {
//        val descriptor = BluetoothGattDescriptor(server2ClientUuid, PERMISSION_WRITE)
//        descriptor.value = DISABLE_NOTIFICATION_VALUE
//
//        this.addDescriptor(descriptor)
//    }

    val client2ServerCharacteristic = BluetoothGattCharacteristic(client2ServerUuid,
        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
        BluetoothGattCharacteristic.PERMISSION_WRITE
    )

    val stateCharacteristic = BluetoothGattCharacteristic(stateUuid,
        BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
        BluetoothGattCharacteristic.PERMISSION_WRITE
    )
//        .apply {
//        val descriptor = BluetoothGattDescriptor(server2ClientUuid, PERMISSION_WRITE)
//        descriptor.value = ENABLE_NOTIFICATION_VALUE
//
//        this.addDescriptor(descriptor)
//    }

    val identCharacteristic = BluetoothGattCharacteristic(identUuid,
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ
    )
}