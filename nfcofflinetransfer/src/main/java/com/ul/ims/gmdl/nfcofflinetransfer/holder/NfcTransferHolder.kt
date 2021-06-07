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
import android.util.Log
import com.ul.ims.gmdl.nfcofflinetransfer.NfcTransportManager
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcUtils.toHexString
import com.ul.ims.gmdl.offlinetransfer.executorLayer.IExecutorEventListener
import com.ul.ims.gmdl.offlinetransfer.transportLayer.EventType
import com.ul.ims.gmdl.offlinetransfer.transportLayer.ITransportLayer

class NfcTransferHolder internal constructor(
    private val context: Context,
    private val transportManager: NfcTransportManager
) : ITransportLayer, BroadcastReceiver() {

    companion object {
        private const val LOG_TAG = "NfcServiceHolder"
        const val ACTION_NFC_TRANSFER_CALLBACK = "com.ul.ims.gmdl.ACTION_NFC_TRANSFER_CALLBACK"
        const val EXTRA_NFC_TRANSFER_REQUEST = "com.ul.ims.gmdl.EXTRA_NFC_TRANSFER_REQUEST"
        const val PACKAGE_NFC_TRANSFER_CALLBACK = "com.ul.ims.gmdl"
        const val ACTION_NFC_TRANSFER_APDU_CALLBACK =
            "com.ul.ims.gmdl.ACTION_NFC_TRANSFER_APDU_CALLBACK"
        const val EXTRA_NFC_TRANSFER_APDU_RESPONSE =
            "com.ul.ims.gmdl.EXTRA_NFC_TRANSFER_APDU_RESPONSE"
    }


    // Listener to notify the executor layer that we've got data
    private var executorEventListener: IExecutorEventListener? = null

    override fun setEventListener(eventListener: IExecutorEventListener?) {
        Log.d(LOG_TAG, "setEventListener")
        executorEventListener = eventListener
    }

    override fun closeConnection() {
        Log.d(LOG_TAG, "closeConnection")
        transportManager.close()
    }

    override fun inititalize(publicKeyHash: ByteArray?) {
        Log.d(LOG_TAG, "initialize")
    }

    override fun write(data: ByteArray?) {
        Log.d(LOG_TAG, "write: ${toHexString(data)}")
        Intent().also { intent ->
            intent.action = ACTION_NFC_TRANSFER_APDU_CALLBACK
            intent.setPackage(PACKAGE_NFC_TRANSFER_CALLBACK)
            intent.putExtra(EXTRA_NFC_TRANSFER_APDU_RESPONSE, data)
            context.sendBroadcast(intent)
        }

    }

    // Broadcast receiver to receive intent when NFC transfer is completed
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(LOG_TAG, "onReceive: $executorEventListener")
        if (intent.hasExtra(EXTRA_NFC_TRANSFER_REQUEST)) {
            executorEventListener?.onReceive(intent.getByteArrayExtra(EXTRA_NFC_TRANSFER_REQUEST))
        } else {
            // Inform UI that the transfer is completed
            transportManager.getTransportProgressDelegate()
                .onEvent(EventType.TRANSFER_COMPLETE, "Transfer completed")
        }
    }
}
