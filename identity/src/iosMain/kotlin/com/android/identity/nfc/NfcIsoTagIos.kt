package com.android.identity.nfc

import com.android.identity.util.toByteArray
import com.android.identity.util.toKotlinError
import com.android.identity.util.toNSData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreNFC.NFCISO7816APDU
import platform.CoreNFC.NFCISO7816TagProtocol
import platform.CoreNFC.NFCErrorDomain
import platform.CoreNFC.NFCReaderTransceiveErrorTagResponseError
import kotlin.coroutines.resumeWithException

internal class NfcIsoTagIos(val tag: NFCISO7816TagProtocol): NfcIsoTag() {
    companion object {
        private const val TAG = "NfcIsoTagIos"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun transceive(apdu: CommandApdu): ResponseApdu {
        return suspendCancellableCoroutine<ResponseApdu> { continuation ->
            val apdu = NFCISO7816APDU(apdu.encode().toNSData())
            tag.sendCommandAPDU(apdu, { responseData, sw1, sw2, error ->
                if (error != null) {
                    val kotlinError = if (error.domain == NFCErrorDomain &&
                        error.code == NFCReaderTransceiveErrorTagResponseError) {
                        NfcTagLostException("Tag was lost during transceive", error.toKotlinError())
                    } else {
                        error.toKotlinError()
                    }
                    continuation.resumeWithException(kotlinError)
                } else {
                    val responseApduData = responseData!!.toByteArray() + byteArrayOf(sw1.toByte(), sw2.toByte())
                    val responseApdu = ResponseApdu.decode(responseApduData)
                    continuation.resume(responseApdu, null)
                }
            })
        }
    }
}
