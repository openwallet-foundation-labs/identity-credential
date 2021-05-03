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

package com.ul.ims.gmdl.nfcofflinetransfer.holder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.ul.ims.gmdl.nfcofflinetransfer.holder.NfcTransferHolder.Companion.ACTION_NFC_TRANSFER_APDU_CALLBACK
import com.ul.ims.gmdl.nfcofflinetransfer.holder.NfcTransferHolder.Companion.EXTRA_NFC_TRANSFER_APDU_RESPONSE
import com.ul.ims.gmdl.nfcofflinetransfer.model.ApduCommand
import com.ul.ims.gmdl.nfcofflinetransfer.model.ApduResponse
import com.ul.ims.gmdl.nfcofflinetransfer.model.DataField
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcConstants.Companion.selectAid
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcConstants.Companion.statusWordChainingResponse
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcConstants.Companion.statusWordFileNotFound
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcConstants.Companion.statusWordInstructionNotSupported
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcConstants.Companion.statusWordOK
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcConstants.Companion.statusWrongLength
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcUtils.createBERTLV
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcUtils.getBERTLVValue
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcUtils.toHexString
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcUtils.toInt
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcUtils.twoBytesToInt

class NfcTransferApduService : HostApduService() {

    companion object {
        private const val LOG_TAG = "NfcTransferApduService"
        private const val defaultMaxLength = 255
    }

    // CBor Interpreter
    private var maxCommandLength: Int = defaultMaxLength

    private val nfcReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            Log.d(
                LOG_TAG,
                "onReceive: ${intent.getByteArrayExtra(EXTRA_NFC_TRANSFER_APDU_RESPONSE)}"
            )
            if (intent.hasExtra(EXTRA_NFC_TRANSFER_APDU_RESPONSE)) {
                intent.getByteArrayExtra(EXTRA_NFC_TRANSFER_APDU_RESPONSE)?.let {
                    sendResponse(it)
                }
            }
        }
    }

    enum class CommandType {
        SELECT_BY_AID,
        SELECT_FILE,
        ENVELOPE,
        RESPONSE,
        READ_BINARY,
        UPDATE_BINARY,
        OTHER
    }

    private var requestDataField = mutableListOf<Byte>()
    private var responseDataField: DataField? = null

    @Suppress("UNCHECKED_CAST")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(LOG_TAG, "onStartCommand(intent: $intent, flags: $flags, startId: $startId)")

        requestDataField = mutableListOf()
        responseDataField = null

        val filter = IntentFilter(ACTION_NFC_TRANSFER_APDU_CALLBACK)
        applicationContext.registerReceiver(nfcReceiver, filter)

        return super.onStartCommand(intent, flags, startId)
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray? {
        Log.d(LOG_TAG, "Command -> " + toHexString(commandApdu))

        val response = when (getCommandType(commandApdu)) {
            CommandType.SELECT_BY_AID -> handleSelect(commandApdu)
            CommandType.SELECT_FILE -> statusWordInstructionNotSupported
            CommandType.READ_BINARY -> statusWordInstructionNotSupported
            CommandType.UPDATE_BINARY -> statusWordInstructionNotSupported
            CommandType.OTHER -> statusWordInstructionNotSupported
            CommandType.ENVELOPE -> handleEnvelope(commandApdu)
            CommandType.RESPONSE -> handleResponse(commandApdu)
        }

        Log.d(LOG_TAG, "Response -> " + toHexString(response))
        return response
    }

    private fun handleSelect(commandApdu: ByteArray?): ByteArray {
        val selectCommand = ApduCommand.Builder().decode(commandApdu).build()
        if (selectCommand.dataField == null) {
            return statusWrongLength
        } else {
            if (!selectAid.contentEquals(selectCommand.dataField)) {
                return statusWordFileNotFound
            }
        }
        return statusWordOK
    }

    private fun handleEnvelope(commandApdu: ByteArray?): ByteArray? {
        val envelopCommand = ApduCommand.Builder().decode(commandApdu).build()
        if (envelopCommand.lc == null) {
            return statusWrongLength
        }
        if (envelopCommand.dataField == null) {
            return statusWordFileNotFound
        }
        envelopCommand.dataField.let { dataField ->

            requestDataField.addAll(dataField.toList())
            Log.d(LOG_TAG, "requestDataField -> " + toHexString(requestDataField.toByteArray()))

            // Check if envelope command is not the last command
            if (envelopCommand.isChain()) {
                return statusWordOK
            }
            maxCommandLength =
                envelopCommand.le?.let { le ->
                    when (le.size) {
                        3 -> {
                            val len = twoBytesToInt(le.copyOfRange(1, 3))
                            if (len == 0) 65536 else len
                        }
                        2 -> {
                            val len = twoBytesToInt(le)
                            if (len == 0) 65536 else len
                        }
                        1 -> {
                            val len = toInt(le[0])
                            if (len == 0) 256 else len
                        }
                        else -> defaultMaxLength
                    }
                } ?: defaultMaxLength

            // Value from BER data object as mdl CBOR request
            val receivedRequest = getBERTLVValue(requestDataField.toByteArray())
            Log.d(
                LOG_TAG,
                "receivedRequest -> " + toHexString(getBERTLVValue(requestDataField.toByteArray()))
            )


            // Send the request to the transfer session to generate response
            sendReceivedRequest(receivedRequest)
            return null
        }
    }

    private fun sendResponse(response: ByteArray) {
        // Create a DO'53' with the mdl CBOR response
        responseDataField =
            DataField(
                createBERTLV(response)
            )

        sendResponseApdu(getDataCommandResponse())
    }

    private fun handleResponse(commandApdu: ByteArray?): ByteArray {
        Log.d(LOG_TAG, "handleResponse: (${commandApdu?.size}) ${toHexString(commandApdu)}")

        val responseCommand = ApduCommand.Builder().decode(commandApdu).build()

        maxCommandLength =
            responseCommand.le?.let { le ->
                when (le.size) {
                    3 -> {
                        val len = twoBytesToInt(le.copyOfRange(1, 3))
                        if (len == 0) 65536 else len
                    }
                    2 -> {
                        val len = twoBytesToInt(le)
                        if (len == 0) 65536 else len
                    }
                    1 -> {
                        val len = toInt(le[0])
                        if (len == 0) 256 else len
                    }
                    else -> defaultMaxLength
                }
            } ?: defaultMaxLength

        return getDataCommandResponse()
    }

    private fun getDataCommandResponse(): ByteArray {
        responseDataField?.let { respDataField ->
            val resp = respDataField.getNextChunk(maxCommandLength)
            val sw1sw2 = if (respDataField.hasMoreBytes()) {
                // Send the size of the next chunk
                // if not extended length status should have the length of remaining bytes
                if (respDataField.size() <= 255) {
                    val sw2 = if (respDataField.size() > maxCommandLength)
                        maxCommandLength
                    else
                        respDataField.size()
                    // Add SW1 - SW2 to response ('61XX' more data available)
                    byteArrayOf(statusWordChainingResponse[0], sw2.toByte())
                } else {
                    // Add SW1 - SW2 to response ('6100' more data available)
                    statusWordChainingResponse
                }
            } else statusWordOK // Add SW1 - SW2 to response ('9000' No further qualification)

            if (statusWordOK.contentEquals(sw1sw2)) {
                // when there is no more data to be sent
                updateUITransferComplete()
            }

            return ApduResponse.Builder().setResponse(resp, sw1sw2).build().encode()
        }

        // in case of any error inform UI that this transfer is complete
        updateUITransferComplete()
        return statusWordFileNotFound
    }

    private fun sendReceivedRequest(data: ByteArray?) {
        Intent().also { intent ->
            intent.action = NfcTransferHolder.ACTION_NFC_TRANSFER_CALLBACK
            intent.setPackage(NfcTransferHolder.PACKAGE_NFC_TRANSFER_CALLBACK)
            intent.putExtra(NfcTransferHolder.EXTRA_NFC_TRANSFER_REQUEST, data)
            sendBroadcast(intent)
        }
    }

    private fun updateUITransferComplete() {
        Intent().also { intent ->
            intent.action = NfcTransferHolder.ACTION_NFC_TRANSFER_CALLBACK
            intent.setPackage(NfcTransferHolder.PACKAGE_NFC_TRANSFER_CALLBACK)
            sendBroadcast(intent)
        }
    }

    private fun getCommandType(commandApdu: ByteArray?): CommandType {
        commandApdu?.let { apdu ->
            if (apdu.size < 3) {
                return CommandType.OTHER
            }

            val ins = toInt(apdu[1])
            val p1 = toInt(apdu[2])

            if (ins == 0xA4) {
                if (p1 == 0x04) {
                    return CommandType.SELECT_BY_AID
                } else if (p1 == 0x00) {
                    return CommandType.SELECT_FILE
                }
            } else if (ins == 0xB0) {
                return CommandType.READ_BINARY
            } else if (ins == 0xD6) {
                return CommandType.UPDATE_BINARY
            } else if (ins == 0xC3) {
                return CommandType.ENVELOPE
            } else if (ins == 0xC0) {
                return CommandType.RESPONSE
            }
        }

        return CommandType.OTHER
    }

    override fun onDeactivated(p0: Int) {
        Log.d(LOG_TAG, "onDeactivated: $p0 ")
        applicationContext.unregisterReceiver(nfcReceiver)
    }
}
