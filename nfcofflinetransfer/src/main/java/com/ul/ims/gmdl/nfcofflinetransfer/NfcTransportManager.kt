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

package com.ul.ims.gmdl.nfcofflinetransfer

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.Tag
import android.util.Log
import com.ul.ims.gmdl.nfcofflinetransfer.holder.NfcTransferApduService
import com.ul.ims.gmdl.nfcofflinetransfer.holder.NfcTransferHolder
import com.ul.ims.gmdl.nfcofflinetransfer.holder.NfcTransferHolder.Companion.ACTION_NFC_TRANSFER_CALLBACK
import com.ul.ims.gmdl.nfcofflinetransfer.reader.NfcTransferReader
import com.ul.ims.gmdl.offlinetransfer.config.AppMode
import com.ul.ims.gmdl.offlinetransfer.transportLayer.ITransportEventListener
import com.ul.ims.gmdl.offlinetransfer.transportLayer.ITransportLayer
import com.ul.ims.gmdl.offlinetransfer.transportLayer.TransportManager

/**
 * NFC Specific Transport Manager
 */
class NfcTransportManager(
    private val context: Context,
    private val appMode: AppMode,
    private val nfcTag: Tag?,
    private val apduCommandLength: Int?
) : TransportManager {

    private var transportEventListener: ITransportEventListener? = null

    private lateinit var nfcService: ITransportLayer
    private lateinit var nfcReceiverHolder: NfcTransferHolder

    init {
        setupTransportLayer()
    }

    companion object {
        private const val LOG_TAG = "NfcTransportManager"
    }

    override fun setTransportProgressListener(transportEventListener: ITransportEventListener) {
        this.transportEventListener = transportEventListener
    }

    private fun setupTransportLayer() {
        nfcService = when (appMode) {
            AppMode.HOLDER -> {
                nfcReceiverHolder = NfcTransferHolder(context, this)
                val filter = IntentFilter(ACTION_NFC_TRANSFER_CALLBACK)
                context.registerReceiver(nfcReceiverHolder, filter)

                // Nfc Transfer service
                Intent(context, NfcTransferApduService::class.java).also { intent ->
                    context.startService(intent)
                }


                nfcReceiverHolder
            }
            AppMode.VERIFIER -> {
                NfcTransferReader(this, nfcTag, apduCommandLength)
            }
            else -> throw UnsupportedOperationException("Unknown AppMode")
        }

    }

    override fun getTransportLayer(): ITransportLayer {
        return when (appMode) {
            AppMode.HOLDER -> {
                nfcService
            }
            AppMode.VERIFIER -> {
                nfcService
            }
        }
    }

    fun close() {
        Log.d(LOG_TAG, "close")
        try {
            context.unregisterReceiver(nfcReceiverHolder)
        } catch (e: IllegalArgumentException) {
            // Ignore error when trying to unregister receiver
            Log.e(LOG_TAG, "Ignored error: ${e.message}")
        }
        try {
            Intent(context, NfcTransferApduService::class.java).also { intent ->
                context.stopService(intent)
            }
        } catch (e: IllegalArgumentException) {
            // Ignore error when trying to unregister receiver
            Log.e(LOG_TAG, "Ignored error: ${e.message}")
        }
    }

    override fun setReadyForNextFile(boolean: Boolean) {
        Log.i(LOG_TAG, "Ready to read next file.")
        throw UnsupportedOperationException("Not applicable for NFC")
    }

    fun getTransportProgressDelegate(): ITransportEventListener {
        return transportEventListener
            ?: throw IllegalStateException("TransportProgressDelegate is null")
    }
}