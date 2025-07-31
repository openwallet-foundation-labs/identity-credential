package org.multipaz.testapp.AidRegistration

import android.content.ComponentName
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import org.multipaz.context.applicationContext
import org.multipaz.util.Logger

/**
 * Change the NFC routing to the Host (app) or SE (Secure Element) as needed to serve DirectAccess feature.
 */

private const val TAG: String = "AidRegistrationUtil"

/** NFC Type 4 Tag. */
private const val AID_TYPE_4_TAG_NDEF_APPLICATION: String = "D2760000850101"

/** Defined in ISO 18013-5:2021 clause 8.3.3.1.2. */
private const val AID_MDL_DATA_TRANSFER: String = "A0000002480400"
private const val NFC_ENGAGEMENT_HANDLER: String = "org.multipaz.testapp.TestAppMdocNdefService"
private const val OFF_HOST_NFC_PRESENTATION_HANDLER: String = "org.multipaz.testapp.TestAppMdocNfcDataTransferService"

/**
 * Switch the NFC routing to the Host (app).
 */
actual fun routeNfcAidsToHost() {
    Logger.i(TAG, "Nfc route AIDs to Host")
    val nfcAdapter = NfcAdapter.getDefaultAdapter(applicationContext)
    if (nfcAdapter == null) {
        return
    }
    val cardEmulation = CardEmulation.getInstance(nfcAdapter!!)
    val nfcEngagementHandlerComponent = ComponentName(applicationContext, NFC_ENGAGEMENT_HANDLER)
    val offHostNfcPresentationHandlerComponent = ComponentName(applicationContext, OFF_HOST_NFC_PRESENTATION_HANDLER)
    if (!cardEmulation.removeAidsForService(
            offHostNfcPresentationHandlerComponent,
            CardEmulation.CATEGORY_OTHER
        )
    ) {
        Logger.w(TAG, "Failed to remove aids for off host (could be not set)")
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
 */
actual fun routeNfcAidsToSe() {
    Logger.w(TAG, "Nfc route AIDs to SE")
    val nfcAdapter = NfcAdapter.getDefaultAdapter(applicationContext)
    if (nfcAdapter == null) return

    val cardEmulation = CardEmulation.getInstance(nfcAdapter)
    val nfcEngagementHandlerComponent = ComponentName(applicationContext, NFC_ENGAGEMENT_HANDLER)
    val offHostNfcPresentationHandlerComponent = ComponentName(applicationContext, OFF_HOST_NFC_PRESENTATION_HANDLER)
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
