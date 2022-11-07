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

package com.android.mdl.app.util

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.android.mdl.app.transfer.TransferManager

class NfcDataTransferHandler : HostApduService() {

    companion object {
        private const val LOG_TAG = "NfcDataTransferHandler"

        private val AID_FOR_MDL_DATA_TRANSFER : ByteArray = byteArrayOf(
            0xA0.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x02.toByte(),
            0x48.toByte(),
            0x04.toByte(),
            0x00.toByte())
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray? {
        Log.d(LOG_TAG, "processCommandApdu: Command-> ${FormatUtil.encodeToString(commandApdu)}")

        TransferManager.getInstance(applicationContext)
            .nfcProcessCommandApdu(this, AID_FOR_MDL_DATA_TRANSFER, commandApdu)

        return null
    }

    override fun onDeactivated(reason: Int) {
        Log.d(LOG_TAG, "onDeactivated: reason-> $reason")
        TransferManager.getInstance(applicationContext)
            .nfcOnDeactivated(this, AID_FOR_MDL_DATA_TRANSFER, reason)
    }
}
