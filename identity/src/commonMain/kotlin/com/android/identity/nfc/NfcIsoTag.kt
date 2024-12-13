package com.android.identity.nfc

abstract class NfcIsoTag {

    abstract suspend fun transceive(apduData: ByteArray): ByteArray

    companion object {
        private const val TAG = "NfcIsoTag"
    }
}
