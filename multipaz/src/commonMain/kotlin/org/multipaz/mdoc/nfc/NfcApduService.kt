package org.multipaz.mdoc.nfc

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlin.concurrent.Volatile

/**
 * Interface for NFC APDU services orchestrated by [CombinedNfcService].
 *
 * @property sendResponse a function to send a response APDU via [CombinedNfcService].
 */
abstract class NfcApduService(
    private val sendResponse: (ByteArray) -> Unit
) {
    companion object {
        // Shared across all NfcApduService subclasses (MdocNdefService and MdocNfcV2Service)
        // so that a subsequent NFC tap on either service (or across recreated HostApduService
        // instances) can cancel and join any previous active proximity transaction.
        @Volatile
        internal var transactionJob: Job? = null
    }

    internal class TransactionSupersededException :
        CancellationException("Transaction superseded by a new NFC tap")

    /**
     * Called when [CombinedNfcService] is created by the OS.
     */
    open fun onCreate() {}

    /**
     * Called when [CombinedNfcService] is destroyed by the OS.
     */
    open fun onDestroy() {}

    /**
     * Called when APDUs arrive
     *
     * @param commandApdu the bytes of the command APDU.
     * @return the Response APDU or `null`.
     */
    open fun processCommandApdu(commandApdu: ByteArray): ByteArray? { return null }

    /**
     * Called when the service is deactivated.
     *
     * @param reason the reason for deactivation.
     */
    open fun onDeactivated(reason: Int) {}

    /**
     * Sends a response APDU.
     *
     * @param apdu the bytes of the Response APDU.
     */
    protected fun sendResponseApdu(apdu: ByteArray) {
        sendResponse(apdu)
    }
}
