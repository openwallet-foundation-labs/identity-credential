package com.android.identity.nfc

import android.nfc.tech.IsoDep
import com.android.identity.util.Logger

internal class NfcIsoTagAndroid(val tag: IsoDep): NfcIsoTag() {
    override suspend fun transceive(apduData: ByteArray): ByteArray {
        // TODO: run in thread instead of blocking thread for coroutine...
        Logger.iHex(TAG, "Sending APDU", apduData)
        val responseApdu = tag.transceive(apduData)
        Logger.iHex(TAG, "Response APDU", responseApdu)
        return responseApdu
    }

    companion object {
        private const val TAG = "NfcIsoTagAndroid"
    }
}
