package org.multipaz.testapp

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import org.multipaz.mdoc.transport.NfcTransportMdoc
import org.multipaz.util.Logger

class MdocNfcDataTransferService: HostApduService() {
    companion object {
        private val TAG = "MdocNfcDataTransferService"
    }

    override fun onDestroy() {
        Logger.i(TAG, "onDestroy")
        super.onDestroy()
    }

    override fun onCreate() {
        Logger.i(TAG, "onCreate")
        super.onCreate()
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray? {
        Logger.i(TAG, "processCommandApdu")
        try {
            NfcTransportMdoc.processCommandApdu(
                commandApdu = commandApdu,
                sendResponse = { responseApdu -> sendResponseApdu(responseApdu) }
            )
        } catch (e: Throwable) {
            Logger.e(TAG, "processCommandApdu", e)
            e.printStackTrace()
        }
        return null
    }

    override fun onDeactivated(reason: Int) {
        Logger.i(TAG, "onDeactivated $reason")
        NfcTransportMdoc.onDeactivated()
    }
}