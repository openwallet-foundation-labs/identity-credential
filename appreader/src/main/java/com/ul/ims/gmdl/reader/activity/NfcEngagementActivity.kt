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

package com.ul.ims.gmdl.reader.activity

import android.app.Activity
import android.content.Intent
import android.nfc.*
import android.nfc.tech.Ndef
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.util.Pair
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcUtils
import com.ul.ims.gmdl.offlinetransfer.config.BleServiceMode
import com.ul.ims.gmdl.offlinetransfer.transportLayer.TransferChannels
import com.ul.ims.gmdl.reader.R
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.util.*

class NfcEngagementActivity : AppCompatActivity() {
    companion object {
        const val LOG_TAG = "NfcEngagementActivity"
        const val DEVICE_ENGAGEMENT_RESULT = "com.ul.ims.gmdl.DEVICE_ENGAGEMENT_RESULT"
        const val DEVICE_ENGAGEMENT_ERROR = "com.ul.ims.gmdl.DEVICE_ENGAGEMENT_ERROR"
        const val EXTRA_TRANSFER_METHOD = "com.ul.ims.gmdl.TRANSFER_METHOD"
        const val EXTRA_BLE_ROLE = "com.ul.ims.gmdl.EXTRA_BLE_ROLE"
        const val EXTRA_WIFI_PASSPHRASE = "com.ul.ims.gmdl.EXTRA_WIFI_PASSPHRASE"
        const val EXTRA_APDU_COM_LEN = "com.ul.ims.gmdl.EXTRA_APDU_COM_LEN"
        private const val READER_FLAGS = (NfcAdapter.FLAG_READER_NFC_A
                or NfcAdapter.FLAG_READER_NFC_B
                or NfcAdapter.FLAG_READER_NFC_F
                or NfcAdapter.FLAG_READER_NFC_V
                or NfcAdapter.FLAG_READER_NFC_BARCODE)
    }

    private var nfcAdapter: NfcAdapter? = null

    private val callback = ReaderModeCallback()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_engagement)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter?.isEnabled != true) {
            val resultIntent = Intent()

            resultIntent.putExtra(DEVICE_ENGAGEMENT_ERROR, "NFC Adapter is not present")

            setResult(RESULT_OK, resultIntent)
            finish()
            return
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(this, callback, READER_FLAGS, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    private inner class ReaderModeCallback : NfcAdapter.ReaderCallback {

        override fun onTagDiscovered(tag: Tag) {

            // Tell the adapter to ignore this tag for any consecutive reads. debounceMs needs to be
            //  greater than the polling interval of the adapter
            nfcAdapter?.ignore(tag, 1000, null, null)

            val techList = tag.techList

            Log.d(
                this.javaClass.simpleName,
                "Tag discovered: supported tech=" + Arrays.toString(techList)
            )

            for (tech in techList) {
                if (Ndef::class.java.name == tech) {
                    NdefReaderTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, tag)
                    break
                }
            }
        }
    }

    private inner class NdefReaderTask : AsyncTask<Tag, Void, Void>() {

        override fun doInBackground(vararg params: Tag): Void? {
            val tag = params[0]

            val ndef = Ndef.get(tag)
                ?: return null // NDEF is not supported by this Tag.

            val ndefMessage = ndef.cachedNdefMessage

            val handoverSelectMessage: HandoverSelectMessage
            try {
                handoverSelectMessage = HandoverSelectMessage(ndefMessage, ndef)


                handoverSelectMessage.deviceEngagementBytes?.let { deviceEngagement ->
                    val resultIntent = Intent()

                    resultIntent.putExtra(
                        DEVICE_ENGAGEMENT_RESULT,
                        deviceEngagement
                    )

                    resultIntent.putExtra(
                        EXTRA_TRANSFER_METHOD,
                        handoverSelectMessage.transferMethod
                    )

                    resultIntent.putExtra(
                        EXTRA_BLE_ROLE,
                        handoverSelectMessage.bleServiceMode
                    )

                    resultIntent.putExtra(
                        EXTRA_WIFI_PASSPHRASE,
                        handoverSelectMessage.wifiPassphrase
                    )

                    resultIntent.putExtra(
                        EXTRA_APDU_COM_LEN,
                        handoverSelectMessage.apduCommandLength
                    )

                    setResult(RESULT_OK, resultIntent)
                    finish()
                    return null
                }
            } catch (e: FormatException) {
                val resultIntent = Intent()

                resultIntent.putExtra(DEVICE_ENGAGEMENT_ERROR, e.message)

                setResult(RESULT_OK, resultIntent)
                finish()
                return null
            }

            val resultIntent = Intent()

            setResult(RESULT_OK, resultIntent)
            finish()
            return null
        }
    }


    override fun onBackPressed() {
        val resultIntent = Intent()

        setResult(Activity.RESULT_OK, resultIntent)

        super.onBackPressed()
    }

    class HandoverSelectMessage @Throws(FormatException::class)
    constructor(
        message: NdefMessage,
        private val tag: Ndef?
    ) {

        var deviceEngagementBytes: ByteArray? = null
            private set
        var transferMethod: TransferChannels? = null
            private set
        var bleServiceMode: BleServiceMode? = null
            private set
        var wifiPassphrase: String? = null
            private set
        var apduCommandLength: Int? = null
            private set

        init {
            processNDefTag(message)
        }

        @Throws(FormatException::class)
        private fun processNDefTag(message: NdefMessage) {

            // Step 1: get the records.
            // Step 2: figure out if it's static or negotiated handover
            // Step 3: parse out the required information

            val records = message.records

            if (records.isNotEmpty()) {
                val handoverSelectRecord = records[0]

                when {
                    Arrays.equals(handoverSelectRecord.type, HANDOVER_STATIC) ->
                        parseStaticHandover(
                            handoverSelectRecord,
                            Arrays.copyOfRange(records, 1, records.size)
                        )
                    Arrays.equals(handoverSelectRecord.type, HANDOVER_NEGOTIATED) ->
                        parseNegotiatedHandover(
                            handoverSelectRecord,
                            Arrays.copyOfRange(records, 1, records.size),
                            tag
                        )
                    else -> throw FormatException("Initial Ndef record is not of type Hs or Hr")
                }
            } else {
                throw FormatException("Not enough records supplied for Handover Select message")
            }
        }

        @Throws(FormatException::class)
        private fun parseStaticHandover(
            handoverSelectRecord: NdefRecord,
            additionalRecords: Array<NdefRecord>
        ) {
            // Determine from handover select record which reference
            // is the carrier data record, and which is the auxiliary data
            val carrierDataReference: ByteArray
            val auxiliaryDataReference: ByteArray
            try {
                val carrierAuxiliaryDataPair =
                    getCarrierAndAuxiliaryReferenceFromACRecord(handoverSelectRecord)

                carrierDataReference = carrierAuxiliaryDataPair.first
                auxiliaryDataReference = carrierAuxiliaryDataPair.second
            } catch (e: FormatException) {
                throw FormatException(
                    "Error while determining carrier and auxiliary methods: " + e.message
                )
            }

            val carrierRecord = Arrays.stream(additionalRecords)
                .filter { record -> Arrays.equals(record.id, carrierDataReference) }
                .findFirst()

            val auxiliaryRecord = Arrays.stream(additionalRecords)
                .filter { record -> Arrays.equals(record.id, auxiliaryDataReference) }
                .findFirst()

            // If either record is unavailable we cannot parse further
            if (!carrierRecord.isPresent || !auxiliaryRecord.isPresent) {
                throw FormatException("Carrier record and Auxiliary record specified in Hs record not present")
            }

            // Check the carrier data record has expected type according to transferMethod
            // and auxiliary record has correct type
            transferMethod = when {
                String(carrierRecord.get().type).equals(
                    APPLICATION_BT_LE_OOB,
                    true
                ) -> TransferChannels.BLE
                String(carrierRecord.get().type).equals(
                    APPLICATION_WIFI_AWARE,
                    true
                ) -> TransferChannels.WiFiAware
                String(carrierRecord.get().type).equals(
                    APPLICATION_ISO_MDL,
                    true
                ) -> TransferChannels.NFC
                else -> throw FormatException("Unable to determine transfer method from carrier record")
            }

            // If true, deviceEngagementBytes = auxiliaryRecord.getPayload()
            if (String(auxiliaryRecord.get().type).equals(AUXILIARY_DATA_MDL, true)) {
                if (String(carrierRecord.get().type).equals(APPLICATION_BT_LE_OOB, true)) {
                    extractBluetoothLEInfo(carrierRecord.get())
                }

                deviceEngagementBytes = auxiliaryRecord.get().payload
            } else {
                // If false, deviceEngagementBytes remains null
                throw FormatException("Incorrect types for carrier record and auxiliary record " + "given requested transfer method")
            }

            // Read passphrase to wifi aware from carrier data record
            try {
                if (carrierRecord.isPresent && TransferChannels.WiFiAware == transferMethod) {
                    wifiPassphrase = getPassphraseFromCarrierDataRecord(carrierRecord.get())
                }
            } catch (e: FormatException) {
                throw FormatException(
                    "Error on trying to read the Passphrase from carrier data record: ${e.message}"
                )
            }

            // Read maximum length of command data field supported by mobile device
            try {
                if (carrierRecord.isPresent && TransferChannels.NFC == transferMethod) {
                    apduCommandLength = getMaxLengthFromCarrierDataRecord(carrierRecord.get())
                }
            } catch (e: FormatException) {
                throw FormatException(
                    "Error on trying to read the maximum length of command data field" +
                            " from carrier data record: ${e.message}"
                )
            }
        }

        @Throws(FormatException::class)
        private fun extractBluetoothLEInfo(carrierRecord: NdefRecord) {
            val bleConnectionInfo = parseLTV(ByteBuffer.wrap(carrierRecord.payload))
                ?: throw FormatException("Bluetooth LE carrier record not formatted correctly")

            if (!bleConnectionInfo.containsKey(0x1C)) {
                throw FormatException("Bluetooth LE carrier record does not contain LE role")
            }

            val leRole = bleConnectionInfo[0x1C]

            bleServiceMode =
                if (leRole!!.contentEquals(LE_CENTRAL_ONLY) || leRole.contentEquals(LE_CENTRAL_PREF)) {
                    BleServiceMode.CENTRAL_CLIENT_MODE
                } else if (leRole.contentEquals(LE_PERIPHERAL_ONLY) || leRole.contentEquals(
                        LE_PERIPHERAL_PREF
                    )
                ) {
                    BleServiceMode.PERIPHERAL_SERVER_MODE
                } else {
                    throw FormatException("Bluetooth LE carrier record does not contain valid LE role")
                }
        }

        private fun parseNegotiatedHandover(
            handoverSelectRecord: NdefRecord,
            additionalRecords: Array<NdefRecord>, tag: Ndef?
        ) {
            // todo: implementation unclear from ISO 18013-5
        }

        companion object {
            private val HANDOVER_STATIC = byteArrayOf(0x48, 0x73) // Hs
            private val HANDOVER_NEGOTIATED = byteArrayOf(0x48, 0x72) // Hr

            private val LE_PERIPHERAL_ONLY = byteArrayOf(0x00)
            private val LE_CENTRAL_ONLY = byteArrayOf(0x01)
            private val LE_PERIPHERAL_PREF = byteArrayOf(0x02)
            private val LE_CENTRAL_PREF = byteArrayOf(0x03)

            private const val APPLICATION_BT_LE_OOB = "application/vnd.bluetooth.le.oob"
            private const val APPLICATION_WIFI_AWARE = "application/vnd.wfa.nan"
            private const val APPLICATION_ISO_MDL = "iso.org:18013"

            private const val AUXILIARY_DATA_MDL = "iso.org:18013:deviceengagement"

            @Throws(FormatException::class)
            private fun getCarrierAndAuxiliaryReferenceFromACRecord(
                handoverSelectRecord: NdefRecord?
            ): Pair<ByteArray, ByteArray> {

                if (handoverSelectRecord == null || handoverSelectRecord.payload == null) {
                    throw FormatException("AC Record Payload not present.")
                }
                try {
                    val handoverSelectRecordPayload = handoverSelectRecord.payload

                    // The handover select record payload is structured as follows (Index - Data):
                    //  0                       - NFC Connection Handover Specification Version
                    //  1                       - NDEF Record Header
                    //  2                       - Record Type Length (Z)
                    //  3                       - Payload Length (W)
                    //  4 to 4+Z-1              - Record Type
                    //  4+Z to 4+Z+W-1 (end)    - AC Record Payload

                    val recordTypeLength = handoverSelectRecordPayload[2].toInt().and(0xFF)

                    val acRecordPayload = Arrays.copyOfRange(
                        handoverSelectRecordPayload,
                        4 + recordTypeLength,
                        handoverSelectRecordPayload.size
                    )

                    // The payload of the AC Record is structured as follows (Index - Data)
                    //  0                       - Carrier Flags
                    //  1                       - Carrier Data Reference Length (X)
                    //  2 to 2+X-1              - Carrier Data Reference
                    //  2+X                     - Auxiliary Data Reference Count
                    //  3+X                     - Auxiliary Data Reference Length (Y)
                    //  4+X to 4+X+Y-1          - Auxiliary Data Reference

                    val carrierDataReferenceLength = acRecordPayload[1].toInt().and(0xFF)

                    val carrierDataReference = Arrays.copyOfRange(
                        acRecordPayload,
                        2,
                        2 + carrierDataReferenceLength
                    )

                    val auxiliaryDataReferenceLength =
                        acRecordPayload[3 + carrierDataReferenceLength].toInt().and(0xFF)

                    val auxiliaryDataReference = Arrays.copyOfRange(
                        acRecordPayload,
                        4 + carrierDataReferenceLength,
                        4 + carrierDataReferenceLength + auxiliaryDataReferenceLength
                    )

                    return Pair.create(carrierDataReference, auxiliaryDataReference)
                } catch (e: ArrayIndexOutOfBoundsException) {
                    throw FormatException("Reference length indicated longer than available bytes")
                }

            }

            @Throws(FormatException::class)
            private fun getPassphraseFromCarrierDataRecord(
                carrierDataRecord: NdefRecord?
            ): String {

                if (carrierDataRecord == null || carrierDataRecord.payload == null) {
                    throw FormatException("Carrier Data Record Payload not present.")
                }
                try {
                    val carrierDataRecordPayload = carrierDataRecord.payload

                    // The carrier data record payload for wifi is structured as follows (Index - Data):
                    //Cipher Suite Info
                    // 0                        - Length 2 octets
                    // 1                        - Data Type 0x01 - Cipher Suite Info
                    // 2                        - Cipher Suite ID Info (1 – NCS-SK-128 Cipher Suite)
                    //Password Info
                    // 3                        - Length 33 or 1 octets (X)
                    // 4                        - Data Type 0x03 - Password Info
                    // 5 to 5+X-1               - Passphrase info
                    //Band Info
                    // 5+X                      - Length 2 octets
                    // 6+X                      - Data Type 0x04 - Band Info
                    // 7+X                      - 0x14: 2.4 GHz or 0x28: 2.4 GHz + Bit 4: 4.9 and 5 GHz

                    val passphraseLength = carrierDataRecordPayload[3].toInt().and(0xFF)

                    if (passphraseLength < 33)
                        throw FormatException("Password info incorrect")

                    val passphraseData = Arrays.copyOfRange(
                        carrierDataRecordPayload,
                        5, 5 + passphraseLength - 1
                    )

                    Log.d(
                        LOG_TAG,
                        "getPassphraseFromCarrierDataRecord: ${passphraseData.toString(Charsets.UTF_8)}"
                    )

                    return passphraseData.toString(Charsets.UTF_8)
                } catch (e: ArrayIndexOutOfBoundsException) {
                    throw FormatException("Reference length indicated longer than available bytes")
                }

            }

            @Throws(FormatException::class)
            private fun getMaxLengthFromCarrierDataRecord(
                carrierDataRecord: NdefRecord?
            ): Int {

                if (carrierDataRecord == null || carrierDataRecord.payload == null) {
                    throw FormatException("Carrier Data Record Payload not present.")
                }
                try {
                    val carrierDataRecordPayload = carrierDataRecord.payload

                    // The carrier data record payload for NFC is structured as follows (Index - Data):
                    // 0x10,        - mDL NFC Connection Handover Version.
                    // 0xFF, 0xFF   - Maximum length of command data field supported by mobile device

                    if (carrierDataRecordPayload.size < 3) {
                        throw FormatException("Maximum length of command data field not present.")
                    }
                    val maxLength =
                        NfcUtils.twoBytesToInt(carrierDataRecordPayload.copyOfRange(1, 3))

                    Log.d(LOG_TAG, "getMaxLengthFromCarrierDataRecord: $maxLength")

                    return maxLength
                } catch (e: ArrayIndexOutOfBoundsException) {
                    throw FormatException("Reference length indicated longer than available bytes")
                }

            }

            private fun parseLTV(buffer: ByteBuffer): Map<Int, ByteArray>? {
                val map = HashMap<Int, ByteArray>()

                try {
                    while (buffer.hasRemaining()) {
                        val length = buffer.get().toInt().and(0xFF)
                        val tag = buffer.get().toInt().and(0xFF)
                        val value = ByteArray(length - 1)
                        buffer.get(value)

                        map[tag] = value
                    }
                } catch (e: BufferUnderflowException) {
                    return null
                }

                return map
            }
        }

    }
}