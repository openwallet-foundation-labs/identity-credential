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

import android.nfc.NdefMessage
import android.nfc.NdefRecord

class NfcConstants {
    companion object {
        val statusWordOK: ByteArray = byteArrayOfInts(0x90, 0x00)
        val statusWordInstructionNotSupported: ByteArray = byteArrayOfInts(0x6D, 0x00)
        val statusWordFileNotFound: ByteArray = byteArrayOfInts(0x6A, 0x82)
        val statusWordEndOfFileReached: ByteArray = byteArrayOfInts(0x62, 0x82)
        val statusWordWrongParameters: ByteArray = byteArrayOfInts(0x6B, 0x00)

        private const val hsRecordTNF: Short = 0x01
        private val hsRecordType: ByteArray = byteArrayOfInts(0x48, 0x73)
        private val hsRecordId: ByteArray? = null
        private val hsRecordPayload: ByteArray = byteArrayOfInts(
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
        private val hsRecordPayloadWifi: ByteArray = byteArrayOfInts(
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

        private const val bluetoothLERecordTNF: Short = 0x02 // type = RFC 2046 (MIME)
        // type name = "application/vnd.bluetooth.le.oob"
        private val bluetoothLERecordType: ByteArray = "application/vnd.bluetooth.le.oob".toByteArray()
        private val bluetoothLERecordId: ByteArray = "0".toByteArray()

        // Wifi Aware Carrier Data Record
        private const val wifiAwareRecordTNF: Short = 0x02 // type = RFC 2046 (MIME)
        private val wifiAwareRecordType = "application/vnd.wfa.nan".toByteArray()
        private val wifiAwareRecordId = "W".toByteArray()

        private const val deviceEngagementTNF: Short = 0x04 // type = external
        // type name = "iso.org:18013:deviceengagement"
        private val deviceEngagementType: ByteArray = "iso.org:18013:deviceengagement".toByteArray()
        // id = "mDL"
        private val deviceEngagementId: ByteArray = byteArrayOfInts(0x6D, 0x44, 0x4C)

        private val hcRecord = NdefRecord(hsRecordTNF, hsRecordType, hsRecordId, hsRecordPayload)
        private val hcRecordWifi =
            NdefRecord(hsRecordTNF, hsRecordType, hsRecordId, hsRecordPayloadWifi)

        fun createBLEStaticHandoverRecord(
            deviceEngagementPayload: ByteArray,
            blePeripheralMode: Boolean,
            bleCentralMode: Boolean
        ): NdefMessage {

            val bluetoothLEPayload: ByteArray
            // both modes are supported
            if (blePeripheralMode && bleCentralMode) {
                // When the mDL supports both modes, the mDL reader should act as BLE central mode.
                bluetoothLEPayload = byteArrayOf(
                    0x02, // LE Role length = 2
                    0x1C, // LE Role data type
                    0x02  // Peripheral and Central Role supported, Peripheral Role preferred for connection establishment
                )
            } else {
                // only central client mode supported
                bluetoothLEPayload = if (bleCentralMode) {
                    byteArrayOf(
                        0x02, // LE Role length = 2
                        0x1C, // LE Role data type
                        0x01  // Central mode only
                    )
                } else {
                    // only peripheral server mode supported
                    byteArrayOf(
                        0x02, // LE Role length = 2
                        0x1C, // LE Role data type
                        0x00  // Peripheral mode only
                    )
                }
            }

            val bluetoothLERecord = NdefRecord(
                bluetoothLERecordTNF,
                bluetoothLERecordType,
                bluetoothLERecordId,
                bluetoothLEPayload
            )

            val deviceEngagementRecord = NdefRecord(
                deviceEngagementTNF,
                deviceEngagementType,
                deviceEngagementId,
                deviceEngagementPayload
            )

            return NdefMessage(arrayOf(hcRecord, bluetoothLERecord, deviceEngagementRecord))
        }

        fun createWiFiAwareStaticHandoverRecord(
            deviceEngagementPayload: ByteArray,
            wifiPassphrase: String?,
            wifi5GHzBandSupported: Boolean
        ): NdefMessage {

            //Cipher Suite Info
            val payloadCipherSuite = listOf<Byte>(
                0x02, // Length 2 octets
                0x01, // Data Type 0x01 - Cipher Suite Info
                0x01 // Cipher Suite ID Info (1 – NCS-SK-128 Cipher Suite)
            )

            //Password Info
            val payloadPasswordInfo = mutableListOf<Byte>()
            if (wifiPassphrase != null) {
                payloadPasswordInfo.addAll(
                    listOf(
                        0x21, // Length 33 octets
                        0x03 // Data Type 0x03 - Password Info
                    )
                )
                payloadPasswordInfo.addAll(wifiPassphrase.toByteArray(Charsets.UTF_8).toList())
            } else {
                payloadPasswordInfo.addAll(
                    listOf(
                        0x01, // Length 33 octets
                        0x03 // Data Type 0x03 - Password Info
                    )
                )
            }

            //Band Info
            val payloadBandinfo = if (wifi5GHzBandSupported)
                listOf<Byte>(
                    0x02, // Length 2 octets
                    0x04, // Data Type 0x04 - Band Info
                    0x28  // Bit 2: 2.4 GHz + Bit 4: 4.9 and 5 GHz
                )
            else
                listOf<Byte>(
                    0x02, // Length 2 octets
                    0x04, // Data Type 0x04 - Band Info
                    0x14  // Bit 2: 2.4 GHz
                )

            val payload = mutableListOf<Byte>()
            payload.addAll(payloadCipherSuite)
            payload.addAll(payloadPasswordInfo)
            payload.addAll(payloadBandinfo)

            val wifiAwareRecord = NdefRecord(
                wifiAwareRecordTNF,
                wifiAwareRecordType,
                wifiAwareRecordId,
                payload.toByteArray()
            )

            val deviceEngagementRecord = NdefRecord(
                deviceEngagementTNF,
                deviceEngagementType,
                deviceEngagementId,
                deviceEngagementPayload
            )

            return NdefMessage(arrayOf(hcRecordWifi, wifiAwareRecord, deviceEngagementRecord))
        }
    }
}