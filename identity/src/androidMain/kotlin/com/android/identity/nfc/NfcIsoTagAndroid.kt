package com.android.identity.nfc

import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

internal class NfcIsoTagAndroid(
    private val tag: IsoDep,
    private val tagContext: CoroutineContext,
): NfcIsoTag() {
    companion object {
        private const val TAG = "NfcIsoTagAndroid"
    }

    override suspend fun transceive(apdu: CommandApdu): ResponseApdu {
        // Because transceive() blocks the calling thread, we want to ensure this runs in the
        // dedicated thread that was created for interacting with the tag and which onTagDiscovered()
        // was called in.
        //
        val responseApduData = withContext(tagContext) {
            try {
                tag.transceive(apdu.encode())
            } catch (e: TagLostException) {
                throw NfcTagLostException("Tag was lost", e)
            }
        }
        return ResponseApdu.decode(responseApduData)
    }
}
