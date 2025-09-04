package org.multipaz.nfc

import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import kotlinx.coroutines.withContext
import org.multipaz.util.Logger
import kotlin.coroutines.CoroutineContext

class NfcIsoTagAndroid(
    private val tag: IsoDep,
    private val tagContext: CoroutineContext,
): NfcIsoTag() {
    companion object {
        private const val TAG = "NfcIsoTagAndroid"
    }

    override val maxTransceiveLength: Int
        get() = tag.maxTransceiveLength

    override suspend fun transceive(command: CommandApdu): ResponseApdu {
        val encodedCommand = command.encode()
        check(encodedCommand.size <= maxTransceiveLength) {
            "APDU is ${encodedCommand.size} bytes which exceeds maxTransceiveLength of $maxTransceiveLength bytes"
        }
        // Because transceive() blocks the calling thread, we want to ensure this runs in the
        // dedicated thread that was created for interacting with the tag and which onTagDiscovered()
        // was called in.
        //
        val responseApduData = withContext(tagContext) {
            try {
                Logger.dHex(TAG, "transceive: Sending APDU", encodedCommand)
                tag.transceive(encodedCommand)
            } catch (e: TagLostException) {
                throw NfcTagLostException("Tag was lost", e)
            }
        }
        Logger.dHex(TAG, "transceive: Received APDU", responseApduData)
        return ResponseApdu.decode(responseApduData)
    }
}
