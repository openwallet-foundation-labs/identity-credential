package com.android.identity.nfc

import com.android.identity.util.Logger
import com.android.identity.util.toByteArray
import com.android.identity.util.toKotlinError
import com.android.identity.util.toNSData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreNFC.NFCISO7816APDU
import platform.CoreNFC.NFCISO7816TagProtocol
import kotlin.coroutines.resumeWithException

internal class NfcIsoTagIos(val tag: NFCISO7816TagProtocol): NfcIsoTag() {

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun transceive(apduData: ByteArray): ByteArray {
        return suspendCancellableCoroutine<ByteArray> { continuation ->
            Logger.iHex(TAG, "Sending APDU", apduData)
            val apdu = NFCISO7816APDU(apduData.toNSData())
            tag.sendCommandAPDU(apdu, { responseData, sw1, sw2, error ->
                if (error != null) {
                    continuation.resumeWithException(error.toKotlinError())
                } else {
                    val responseApdu = responseData!!.toByteArray() + byteArrayOf(sw1.toByte(), sw2.toByte())
                    Logger.iHex(TAG, "Response APDU", responseApdu)
                    continuation.resume(responseApdu, null)
                }
            })
        }
    }

    companion object {
        private const val TAG = "NfcIsoTagIos"
    }
}
