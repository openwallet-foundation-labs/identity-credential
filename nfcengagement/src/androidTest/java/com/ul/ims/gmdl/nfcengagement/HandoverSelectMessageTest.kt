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

package com.ul.ims.gmdl.nfcengagement

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord

import com.ul.ims.gmdl.offlinetransfer.config.BleServiceMode
import org.junit.Assert.*

import org.junit.Test

class HandoverSelectMessageTest {
    // Hs Record
    private val hsRecordTNF: Short = 0x01
    private val hsRecordType = byteArrayOfInts(0x48, 0x73)
    private val hsRecordId: ByteArray? = null // Hs record has no ID attached
    private val hsRecordPayload = byteArrayOfInts(
        // ac record
        0x14, // version 1.4
        0xD1, // record header, TNF = 001 (Well known type)
        0x02, // record type length = 2 bytes
        0x08, // payload length = 8 bytes
        0x61, 0x63, // record type = "ac"
        0x01, // carrier flags. CPS = 1 "active"
        0x01, // carrier data reference length = 1
        0x30, // carrier data reference = "0"
        0x01, // auxiliary data reference count: 1
        0x03, // auxiliary data reference length = 1
        // auxiliary reference = "mDL"
        0x6D, 0x44, 0x4C
    )
    private val hsRecordPayloadWifi = byteArrayOfInts(
        // ac record
        0x14, // version 1.4
        0xD1, // record header, TNF = 001 (Well known type)
        0x02, // record type length = 2 bytes
        0x08, // payload length = 8 bytes
        0x61, 0x63, // record type = "ac"
        0x01, // carrier flags. CPS = 1 "active"
        0x01, // carrier data reference length = 1
        0x57, // carrier data reference = "W"
        0x01, // auxiliary data reference count: 1
        0x03, // auxiliary data reference length = 1
        // auxiliary reference = "mDL"
        0x6D, 0x44, 0x4C
    )

    private val hcRecord = NdefRecord(
        hsRecordTNF, hsRecordType, hsRecordId, hsRecordPayload
    )
    private val hcRecordWifi = NdefRecord(
        hsRecordTNF, hsRecordType, hsRecordId, hsRecordPayloadWifi
    )

    // Bluetooth LE Carrier Data Record
    private val bluetoothLERecordTNF: Short = 0x02 // type = RFC 2046 (MIME)
    private val bluetoothLERecordType = "application/vnd.bluetooth.le.oob".toByteArray()
    private val bluetoothLERecordId = "0".toByteArray()
    private val bluetoothLERecordPayload = byteArrayOfInts(
        0x02, // LE Role length = 2
        0x1C, // LE Role data type
        0x01  // Only central mode supported
    )
    private val bluetoothLERecord = NdefRecord(
        bluetoothLERecordTNF,
        bluetoothLERecordType,
        bluetoothLERecordId,
        bluetoothLERecordPayload
    )

    // Wifi Aware Carrier Data Record
    private val wifiAwareRecordTNF: Short = 0x02 // type = RFC 2046 (MIME)
    private val wifiAwareRecordType = "application/vnd.wfa.nan".toByteArray()
    private val wifiAwareRecordId = "W".toByteArray()
    private val wifiAwareRecordPayload = byteArrayOfInts(
        0x02, 0x01, 0x01, 0x21, 0x03,
        0x43, 0x30, 0x36, 0x45, 0x35,
        0x44, 0x35, 0x44, 0x37, 0x33,
        0x37, 0x36, 0x33, 0x35, 0x35,
        0x42, 0x39, 0x35, 0x41, 0x46,
        0x39, 0x30, 0x34, 0x30, 0x33,
        0x38, 0x41, 0x46, 0x33, 0x37,
        0x36, 0x31, 0x02, 0x04, 0x28
    )
    private val wifiAwareRecord = NdefRecord(
        wifiAwareRecordTNF,
        wifiAwareRecordType,
        wifiAwareRecordId,
        wifiAwareRecordPayload
    )

    // NFC Carrier Data Record
    private val nfcRecordTNF: Short = 0x02 // type = RFC 2046 (MIME)
    private val nfcRecordType = "iso.org:18013".toByteArray()
    private val nfcRecordId = "0".toByteArray()
    private val nfcRecordPayload = byteArrayOf(
        0x10, //mDL NFC Connection Handover Version. Major Version: 1, Minor Version: 0
        0xFF.toByte(), 0xFF.toByte() // Maximum length of command data field supported
    )
    private val nfcRecord = NdefRecord(
        nfcRecordTNF,
        nfcRecordType,
        nfcRecordId,
        nfcRecordPayload
    )

    // Device Engagement Auxiliary Data Record
    private val deviceEngagementTNF: Short = 0x04 // type = external
    private val deviceEngagementType = "iso.org:18013:deviceengagement".toByteArray()
    private val deviceEngagementId = "mDL".toByteArray()
    private val deviceEngagementPayload = byteArrayOf(
        0x85.toByte(), 0x63.toByte(), 0x31.toByte(), 0x2e.toByte(), 0x30.toByte(),
        0x82.toByte(), 0x01.toByte(), 0xd8.toByte(), 0x18.toByte(), 0x58.toByte(),
        0x4b.toByte(), 0xa4.toByte(), 0x01.toByte(), 0x02.toByte(), 0x20.toByte(),
        0x01.toByte(), 0x21.toByte(), 0x58.toByte(), 0x20.toByte(), 0x06.toByte(),
        0x62.toByte(), 0x60.toByte(), 0xbf.toByte(), 0x6a.toByte(), 0xc2.toByte(),
        0x9f.toByte(), 0x86.toByte(), 0x7d.toByte(), 0xa6.toByte(), 0x8f.toByte(),
        0x32.toByte(), 0xe4.toByte(), 0xbe.toByte(), 0x40.toByte(), 0x4b.toByte(),
        0x84.toByte(), 0x57.toByte(), 0xe4.toByte(), 0x9e.toByte(), 0x8f.toByte(),
        0x02.toByte(), 0xd0.toByte(), 0x84.toByte(), 0x1b.toByte(), 0x36.toByte(),
        0xe3.toByte(), 0x27.toByte(), 0xa3.toByte(), 0x48.toByte(), 0x63.toByte(),
        0xf0.toByte(), 0x22.toByte(), 0x58.toByte(), 0x20.toByte(), 0x85.toByte(),
        0x15.toByte(), 0x6b.toByte(), 0x0f.toByte(), 0x75.toByte(), 0x89.toByte(),
        0x7f.toByte(), 0x68.toByte(), 0x24.toByte(), 0xad.toByte(), 0xea.toByte(),
        0x5b.toByte(), 0xad.toByte(), 0x26.toByte(), 0x6b.toByte(), 0xc2.toByte(),
        0xc4.toByte(), 0x1d.toByte(), 0xaa.toByte(), 0xbe.toByte(), 0x23.toByte(),
        0x14.toByte(), 0x99.toByte(), 0xb4.toByte(), 0x2e.toByte(), 0xf7.toByte(),
        0x86.toByte(), 0x98.toByte(), 0xd9.toByte(), 0x61.toByte(), 0xcb.toByte(),
        0x42.toByte(), 0x81.toByte(), 0x83.toByte(), 0x02.toByte(), 0x01.toByte(),
        0xa2.toByte(), 0x00.toByte(), 0xf5.toByte(), 0x01.toByte(), 0xf5.toByte(),
        0xa0.toByte(), 0x80.toByte()
    )
    private val deviceEngagementRecord = NdefRecord(
        deviceEngagementTNF,
        deviceEngagementType,
        deviceEngagementId,
        deviceEngagementPayload
    )

    @Test
    fun testStaticHandoverBluetoothLE() {
        val ndefMessage = NdefMessage(arrayOf(hcRecord, bluetoothLERecord, deviceEngagementRecord))

        val handoverSelectMessage: HandoverSelectMessage
        try {
            handoverSelectMessage =
                HandoverSelectMessage(
                    ndefMessage,
                    null
                )
        } catch (e: FormatException) {
            fail(e.message)
            return
        }

        assertNotNull(handoverSelectMessage.deviceEngagementBytes)
        assertArrayEquals(
            deviceEngagementPayload, handoverSelectMessage.deviceEngagementBytes
        )

        assertNotNull(handoverSelectMessage.bleServiceMode)
        assertEquals(
            handoverSelectMessage.bleServiceMode,
            BleServiceMode.CENTRAL_CLIENT_MODE
        )
    }

    @Test
    fun testStaticHandoverCaseInsensitive() {
        val bluetoothLERecord = NdefRecord(
            bluetoothLERecordTNF,
            "aPpLiCaTiOn/VnD.bLuEtOoTh.Le.OoB".toByteArray(),
            bluetoothLERecordId,
            bluetoothLERecordPayload
        )

        val deviceEngagementRecord = NdefRecord(
            deviceEngagementTNF,
            "iSo.OrG:18013:DeViCeEnGaGeMeNt".toByteArray(),
            deviceEngagementId,
            deviceEngagementPayload
        )

        val ndefMessage = NdefMessage(arrayOf(hcRecord, bluetoothLERecord, deviceEngagementRecord))

        val handoverSelectMessage: HandoverSelectMessage
        try {
            handoverSelectMessage =
                HandoverSelectMessage(
                    ndefMessage,
                    null
                )
        } catch (e: FormatException) {
            fail(e.message)
            return
        }

        assertNotNull(handoverSelectMessage.deviceEngagementBytes)
        assertArrayEquals(
            deviceEngagementPayload, handoverSelectMessage.deviceEngagementBytes
        )

        assertNotNull(handoverSelectMessage.bleServiceMode)
        assertEquals(
            handoverSelectMessage.bleServiceMode,
            BleServiceMode.CENTRAL_CLIENT_MODE
        )
    }

    @Test
    fun testStaticHandoverWiFiAware() {
        val ndefMessage =
            NdefMessage(arrayOf(hcRecordWifi, wifiAwareRecord, deviceEngagementRecord))

        val handoverSelectMessage: HandoverSelectMessage
        try {
            handoverSelectMessage =
                HandoverSelectMessage(
                    ndefMessage,
                    null
                )
        } catch (e: FormatException) {
            fail(e.message)
            return
        }

        assertNotNull(handoverSelectMessage.deviceEngagementBytes)
        assertArrayEquals(
            deviceEngagementPayload, handoverSelectMessage.deviceEngagementBytes
        )
    }

    @Test
    fun testStaticHandoverNFC() {
        val ndefMessage = NdefMessage(arrayOf(hcRecord, nfcRecord, deviceEngagementRecord))

        val handoverSelectMessage: HandoverSelectMessage
        try {
            handoverSelectMessage =
                HandoverSelectMessage(
                    ndefMessage,
                    null
                )
        } catch (e: FormatException) {
            fail(e.message)
            return
        }

        assertNotNull(handoverSelectMessage.deviceEngagementBytes)
        assertArrayEquals(
            deviceEngagementPayload, handoverSelectMessage.deviceEngagementBytes
        )
    }

    @Test
    fun testStaticHandoverCarrierMissing() {
        val ndefMessage = NdefMessage(arrayOf(hcRecord, deviceEngagementRecord))

        try {
            HandoverSelectMessage(
                ndefMessage, null
            )
        } catch (e: FormatException) {
            return
        }

        fail("Expected exception not thrown by constructor")
    }

    @Test
    fun testStaticHandoverAuxiliaryDataMissing() {
        val ndefMessage = NdefMessage(arrayOf(hcRecord, bluetoothLERecord))

        try {
            HandoverSelectMessage(
                ndefMessage, null
            )
        } catch (e: FormatException) {
            return
        }

        fail("Expected exception not thrown by constructor")
    }

    private fun byteArrayOfInts(vararg ints: Int): ByteArray {
        val result = ByteArray(ints.size)

        for (i in result.indices) {
            result[i] = (ints[i] and 0xFF).toByte()
        }

        return result
    }
}