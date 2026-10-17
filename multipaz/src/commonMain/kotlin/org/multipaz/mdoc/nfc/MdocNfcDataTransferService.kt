package org.multipaz.mdoc.nfc

import kotlinx.coroutines.CancellationException
import org.multipaz.mdoc.transport.NfcTransportMdoc
import org.multipaz.nfc.CommandApdu
import org.multipaz.nfc.Nfc
import org.multipaz.nfc.NfcApduRouter
import org.multipaz.nfc.NfcApduService
import org.multipaz.nfc.NfcDeactivationReason
import org.multipaz.nfc.ResponseApdu
import org.multipaz.util.Logger

/**
 * Service for implementing NFC data transfer according to ISO/IEC 18013-5:2021.
 *
 * Applications can register an instance of this class in [NfcApduRouter.buildServices]
 * for the mdoc NFC data transfer AID (`A0000002480400`).
 */
open class MdocNfcDataTransferService : NfcApduService() {
    companion object {
        private const val TAG = "MdocNfcDataTransferService"
    }

    override fun start() {
        Logger.i(TAG, "start")
        super.start()
    }

    override fun stop() {
        Logger.i(TAG, "stop")
        super.stop()
    }

    override suspend fun processCommandApdu(commandApdu: CommandApdu): ResponseApdu {
        Logger.i(TAG, "processCommandApdu")
        return try {
            NfcTransportMdoc.processCommandApdu(commandApdu = commandApdu)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.e(TAG, "processCommandApdu", e)
            ResponseApdu(Nfc.RESPONSE_STATUS_ERROR_NO_PRECISE_DIAGNOSIS)
        }
    }

    override suspend fun processDeactivated(reason: NfcDeactivationReason) {
        Logger.i(TAG, "processDeactivated $reason")
        NfcTransportMdoc.onDeactivated()
    }
}
