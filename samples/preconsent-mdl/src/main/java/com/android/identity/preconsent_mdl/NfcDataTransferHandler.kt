/*
 * Copyright (C) 2023 Google LLC
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

package org.multipaz.preconsent_mdl

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.android.identity.android.mdoc.transport.DataTransportNfc
import org.multipaz.util.Logger

class NfcDataTransferHandler : HostApduService() {

    companion object {
        private val TAG = "NfcDataTransferHandler"
    }

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "onCreate")
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray? {
        Logger.i(TAG, "processCommandApdu")
        return DataTransportNfc.processCommandApdu(this, commandApdu)
    }

    override fun onDeactivated(reason: Int) {
        Logger.i(TAG, "onDeactivated $reason")
        DataTransportNfc.onDeactivated(reason)
    }
}