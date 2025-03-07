/*
 * Copyright 2025 The Android Open Source Project
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
package org.multipaz_credential.wallet.dynamicregistration

import android.content.ComponentName
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import org.multipaz.util.Logger

object AidRegistrationUtil {

    private const val TAG: String = "AidRegistrationUtil"
    // NFC Type 4 Tag
    private const val AID_TYPE_4_TAG_NDEF_APPLICATION: String = "D2760000850101"
    // Defined in ISO 18013-5:2021 clause 8.3.3.1.2
    private const val AID_MDL_DATA_TRANSFER: String = "A0000002480400"
    private const val NFC_ENGAGEMENT_HANDLER: String =
        "org.multipaz_credential.wallet.NfcEngagementHandler"
    private const val OFF_HOST_NFC_PRESENTATION_HANDLER: String =
        "org.multipaz_credential.wallet.dynamicregistration.OffHostNfcDataTransferHandler"

    fun routeAidsToHost(context: Context) {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        if (nfcAdapter == null) {
            return
        }
        val cardEmulation = CardEmulation.getInstance(nfcAdapter!!)
        val nfcEngagementHandlerComponent =
            ComponentName(context, NFC_ENGAGEMENT_HANDLER)
        val offHostNfcPresentationHandlerComponent =
            ComponentName(context, OFF_HOST_NFC_PRESENTATION_HANDLER)
        if (!cardEmulation.removeAidsForService(
                offHostNfcPresentationHandlerComponent,
                CardEmulation.CATEGORY_OTHER
            )
        ) {
            Logger.d(TAG, "Failed to remove aids for off host")
        }
        if (!cardEmulation.registerAidsForService(
                nfcEngagementHandlerComponent,
                CardEmulation.CATEGORY_OTHER,
                listOf(AID_TYPE_4_TAG_NDEF_APPLICATION)
            )
        ) {
            Logger.d(TAG, "Failed to dynamically register nfcEngagementHandlerComponent")
        }
    }

    fun routeAidsToSe(context: Context) {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        if (nfcAdapter == null) {
            return
        }
        val cardEmulation = CardEmulation.getInstance(nfcAdapter)
        val offHostNfcPresentationHandlerComponent =
            ComponentName(context, OFF_HOST_NFC_PRESENTATION_HANDLER)
        val nfcEngagementHandlerComponent =
            ComponentName(context, NFC_ENGAGEMENT_HANDLER)
        if (!cardEmulation.removeAidsForService(
                nfcEngagementHandlerComponent,
                CardEmulation.CATEGORY_OTHER
            )
        ) {
            Logger.d(TAG, "Failed to unregister aids for host")
        }
        if (!cardEmulation.registerAidsForService(
                offHostNfcPresentationHandlerComponent,
                CardEmulation.CATEGORY_OTHER,
                listOf(AID_TYPE_4_TAG_NDEF_APPLICATION, AID_MDL_DATA_TRANSFER)
            )
        ) {
            Logger.d(TAG, "Failed to dynamically register offHostNfcDataTransferHandlerComponent")
        }
    }
}