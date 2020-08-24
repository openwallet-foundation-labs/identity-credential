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

package com.ul.ims.gmdl.nfcofflinetransfer.reader

import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.util.Log
import com.ul.ims.gmdl.nfcofflinetransfer.NfcTransportManager
import com.ul.ims.gmdl.nfcofflinetransfer.model.ApduCommand
import com.ul.ims.gmdl.nfcofflinetransfer.model.ApduResponse
import com.ul.ims.gmdl.nfcofflinetransfer.model.DataField
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcConstants.Companion.selectAid
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcConstants.Companion.statusWordOK
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcUtils.createBERTLV
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcUtils.getBERTLVValue
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcUtils.toHexString
import com.ul.ims.gmdl.offlinetransfer.executorLayer.IExecutorEventListener
import com.ul.ims.gmdl.offlinetransfer.transportLayer.EventType
import com.ul.ims.gmdl.offlinetransfer.transportLayer.ITransportLayer
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread

class NfcTransferReader(
    private val transportManager: NfcTransportManager,
    private val nfcTag: Tag?,
    private val apduCommandLength: Int?
) : ITransportLayer {
    companion object {
        private const val LOG_TAG = "NfcTransferReader"
        private const val defaultMaxLength = 255
    }

    private lateinit var isoDep: IsoDep
    private var responseData = mutableListOf<Byte>()

    // Listener to notify the executor layer that we've got data
    private var executorEventListener: IExecutorEventListener? = null

    override fun setEventListener(eventListener: IExecutorEventListener?) {
        Log.d(LOG_TAG, "setEventListener")
        executorEventListener = eventListener
    }

    override fun closeConnection() {
        Log.d(LOG_TAG, "initialize")
        if (isoDep.isConnected) {
            isoDep.close()
        }
    }

    override fun inititalize(publicKeyHash: ByteArray?) {
        Log.d(LOG_TAG, "initialize")

        isoDep = IsoDep.get(nfcTag) ?: return // IsoDep is not supported by this Tag.
        isoDep.timeout = 10000
        isoDep.connect()

        // Uses tag after initiate the offline transfer
        connect()

        responseData = mutableListOf()
    }

    override fun write(data: ByteArray?) {
        doAsync {
            Log.d(LOG_TAG, "write data: ${toHexString(data)}")

            if (isoDep.isConnected) {
                // Inform UI that transfer has started
                executorEventListener?.onEvent(
                    EventType.TRANSFER_IN_PROGRESS.description,
                    EventType.TRANSFER_IN_PROGRESS.ordinal
                )
                uiThread {
                    transportManager.getTransportProgressDelegate().onEvent(
                        EventType.TRANSFER_IN_PROGRESS,
                        EventType.TRANSFER_IN_PROGRESS.description
                    )
                }
                // Data Field - as BER-TLV DO'53'
                val dataField =
                    DataField(
                        createBERTLV(data)
                    )
                val chunkLength = apduCommandLength ?: defaultMaxLength

                while (dataField.hasMoreBytes()) {
                    val dataChunk = dataField.getNextChunk(chunkLength)

                    val envelopeCommand = ApduCommand.Builder()
                        .setEnvelopeCommand(dataChunk, chunkLength, !dataField.hasMoreBytes())
                        .build()

                    val envelopeResponseBytes = isoDep.transceive(envelopeCommand.encode())
                    val envelopeResponse =
                        ApduResponse.Builder().decode(envelopeResponseBytes).build()

                    // Get SW1 - SW2 from response
                    Log.i(LOG_TAG, "ENVELOP: ${toHexString(envelopeResponse.encode())}")
                    // Get response mDL data only in the last chunk
                    if (!dataField.hasMoreBytes()) {
                        if (statusWordOK.contentEquals(envelopeResponse.sw1sw2)) {
                            if (envelopeResponse.dataField != null) {
                                executorEventListener?.onReceive(getBERTLVValue(envelopeResponse.dataField))
                            } else {
                                Log.e(LOG_TAG, "Error: response data field is null")
                                executorEventListener?.onEvent(
                                    EventType.ERROR.description,
                                    EventType.ERROR.ordinal
                                )
                                uiThread {
                                    transportManager.getTransportProgressDelegate().onEvent(
                                        EventType.ERROR,
                                        "Error: response data field is null"
                                    )
                                }
                            }
                        } else {
                            responseData =
                                envelopeResponse.dataField?.toMutableList() ?: mutableListOf()
                            // Call response command to receive the mDL data when there are more data
                            responseCommand(envelopeResponse.getSw2())
                        }
                    }
                }
            }
        }
    }

    private fun responseCommand(responseSW2: Byte) {
        doAsync {
            if (isoDep.isConnected) {

                val responseCommand = ApduCommand.Builder().setResponseCommand(
                    responseSW2.toInt(),
                    apduCommandLength ?: defaultMaxLength > 255
                ).build()

                Log.d(LOG_TAG, "responseCmd: ${toHexString(responseCommand.encode())}")
                try {
                    val responseResponseBytes = isoDep.transceive(responseCommand.encode())
                    val responseResponse =
                        ApduResponse.Builder().decode(responseResponseBytes).build()

                    responseResponse.dataField.let {
                        Log.d(LOG_TAG, "RESPONSE: (${it?.size}) ${toHexString(it)}")
                        responseData.addAll(it?.toList() ?: listOf())
                    }

                    // Check if it receive all data
                    if (!statusWordOK.contentEquals(responseResponse.sw1sw2)) {
                        // Call response command to receive the mDL data when there are more data
                        responseCommand(responseResponse.getSw2())
                        return@doAsync
                    }

                    Log.d(
                        LOG_TAG,
                        "RESPONSE COMPLETE: (${responseData.size}) ${toHexString(responseData.toByteArray())}"
                    )

                    executorEventListener?.onReceive(getBERTLVValue(responseData.toByteArray()))
                } catch (e: TagLostException) {
                    Log.e(LOG_TAG, "Error: ${e.message}", e)
                    executorEventListener?.onEvent(
                        EventType.ERROR.description,
                        EventType.ERROR.ordinal
                    )
                    uiThread {
                        transportManager.getTransportProgressDelegate().onEvent(
                            EventType.ERROR,
                            "Error: ${e.message}"
                        )
                    }
                }

            } else {
                // Send to UI that the connection is ready
                executorEventListener?.onEvent(
                    EventType.STATE_TERMINATE_TRANSMISSION.description,
                    EventType.STATE_TERMINATE_TRANSMISSION.ordinal
                )
                uiThread {
                    transportManager.getTransportProgressDelegate().onEvent(
                        EventType.NO_DEVICE_FOUND,
                        EventType.NO_DEVICE_FOUND.description
                    )
                }
            }
        }
    }

    override fun close() {
        Log.d(LOG_TAG, "Close")

    }

    private fun connect() {
        doAsync {
            if (isoDep.isConnected) {
                Log.d(LOG_TAG, "maxTransceiveLength: ${isoDep.maxTransceiveLength}")

                val selectCommand = ApduCommand.Builder().setSelectCommand(selectAid).build()

                val selectResponseBytes = isoDep.transceive(selectCommand.encode())
                val selectResponse = ApduResponse.Builder().decode(selectResponseBytes).build()
                Log.d(LOG_TAG, "SELECT: ${toHexString(selectResponse.encode())}")

                if (!statusWordOK.contentEquals(selectResponse.sw1sw2)) {
                    executorEventListener?.onEvent(
                        EventType.STATE_TERMINATE_TRANSMISSION.description,
                        EventType.STATE_TERMINATE_TRANSMISSION.ordinal
                    )
                    transportManager.getTransportProgressDelegate().onEvent(
                        EventType.NO_DEVICE_FOUND,
                        EventType.NO_DEVICE_FOUND.description
                    )
                    return@doAsync
                }

                // Send to UI that the connection is ready
                executorEventListener?.onEvent(
                    EventType.STATE_READY_FOR_TRANSMISSION.description,
                    EventType.STATE_READY_FOR_TRANSMISSION.ordinal
                )
                uiThread {
                    transportManager.getTransportProgressDelegate().onEvent(
                        EventType.SERVICE_CONNECTED,
                        EventType.SERVICE_CONNECTED.description
                    )
                }
            } else {
                // Send to UI that the connection is ready
                executorEventListener?.onEvent(
                    EventType.STATE_TERMINATE_TRANSMISSION.description,
                    EventType.STATE_TERMINATE_TRANSMISSION.ordinal
                )
                uiThread {
                    transportManager.getTransportProgressDelegate().onEvent(
                        EventType.NO_DEVICE_FOUND,
                        EventType.NO_DEVICE_FOUND.description
                    )
                }
            }
        }
    }
}