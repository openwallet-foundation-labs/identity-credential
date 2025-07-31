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
package org.multipaz.testapp

import android.content.ComponentName
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import org.multipaz.util.Logger

/**
 * Change the NFC routing to the Host (app) or SE (Secure Element) as needed to serve DirectAccess feature.
 */
object AidRegistrationUtil {
    private const val TAG: String = "AidRegistrationUtil"
    /** NFC Type 4 Tag. */
    private const val AID_TYPE_4_TAG_NDEF_APPLICATION: String = "D2760000850101"
    /** Defined in ISO 18013-5:2021 clause 8.3.3.1.2. */
    private const val AID_MDL_DATA_TRANSFER: String = "A0000002480400"
    private const val NFC_ENGAGEMENT_HANDLER: String = "org.multipaz.testapp.TestAppMdocNdefService"
    private const val OFF_HOST_NFC_PRESENTATION_HANDLER: String = "org.multipaz.testapp.TestAppMdocNfcDataTransferService"

    /**
     * Switch the NFC routing to the Host (app).
     * Invoked on device Power On event.
     *
     * @param context Context.
     */
    fun routeAidsToHost(context: Context) {
        Logger.w(TAG, "routeAidsToHost")
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        if (nfcAdapter == null) {
            return
        }
        val cardEmulation = CardEmulation.getInstance(nfcAdapter!!)
        val nfcEngagementHandlerComponent = ComponentName(context, NFC_ENGAGEMENT_HANDLER)
        val offHostNfcPresentationHandlerComponent = ComponentName(context, OFF_HOST_NFC_PRESENTATION_HANDLER)
        if (!cardEmulation.removeAidsForService(
                offHostNfcPresentationHandlerComponent,
                CardEmulation.CATEGORY_OTHER
            )
        ) {
            Logger.w(TAG, "Failed to remove aids for off host")
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

    /**
     * Switch the NFC routing to the Secure Element (SE).
     * Invoked on device Power Off event.
     *
     * @param context Context.
     */
    fun routeAidsToSe(context: Context) {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        if (nfcAdapter == null) return

        val cardEmulation = CardEmulation.getInstance(nfcAdapter)
        val nfcEngagementHandlerComponent = ComponentName(context, NFC_ENGAGEMENT_HANDLER)
        val offHostNfcPresentationHandlerComponent = ComponentName(context, OFF_HOST_NFC_PRESENTATION_HANDLER)
        if (!cardEmulation.removeAidsForService(nfcEngagementHandlerComponent, CardEmulation.CATEGORY_OTHER)
        ) {
            Logger.w(TAG, "Failed to unregister AIDs for host")
        }
        if (!cardEmulation.registerAidsForService(
                offHostNfcPresentationHandlerComponent,
                CardEmulation.CATEGORY_OTHER,
                listOf(AID_TYPE_4_TAG_NDEF_APPLICATION, AID_MDL_DATA_TRANSFER)
            )
        ) {
            Logger.w(TAG, "Failed to dynamically register offHostNfcDataTransferHandlerComponent")
        }
    }
}