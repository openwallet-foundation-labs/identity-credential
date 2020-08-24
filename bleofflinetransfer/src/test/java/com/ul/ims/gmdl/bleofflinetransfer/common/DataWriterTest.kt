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

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class DataWriterTest {

    @Mock
    lateinit var eventListener: BleEventListener

    @Mock
    lateinit var bluetoothGatt: BluetoothGatt

    @Mock
    lateinit var holderCharacteristic: BluetoothGattCharacteristic

    private lateinit var dataWriter: DataWriter

    private val chunckSize = 1

    private val bytes = byteArrayOf(
        0xfa.toByte(), 0xfb.toByte(), 0xfc.toByte(), 0xfd.toByte(), 0xfe.toByte(), 0xde.toByte()
    )

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        dataWriter = DataWriter(
            bytes,
            chunckSize,
            bluetoothGatt,
            holderCharacteristic,
            eventListener
        )
    }

    @Test
    fun setAsFirstPartTest() {
        val result = dataWriter.setAsFirstPart()

        Assert.assertNotNull(result)
        Assert.assertArrayEquals(byteArrayOf(0x01.toByte(), 0xfa.toByte()), result)
    }

    @Test
    fun setAsLastPart() {
        val result = dataWriter.setAsLastPart()

        Assert.assertNotNull(result)
        Assert.assertArrayEquals(byteArrayOf(0x00.toByte()) + bytes, result)
    }
}