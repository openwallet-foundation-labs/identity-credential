package org.multipaz.mdoc.nfc

import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import org.multipaz.nfc.CommandApdu
import org.multipaz.nfc.Nfc
import org.multipaz.nfc.ResponseApdu
import org.multipaz.util.Logger
import org.multipaz.util.toHex

private const val TAG = "CombinedNfcService"

/**
 * A service which can route NFC APDUs to multiple other [NfcApduService] instances.
 *
 * On Android, this subclasses `HostApduService` via [CombinedNfcServiceBase] so that a single
 * OS service can route to multiple handlers (which is required because
 * `CardEmulation.setPreferredService()` can only handle a single service).
 */
abstract class CombinedNfcService : CombinedNfcServiceBase() {

    protected lateinit var services: Map<ByteString, NfcApduService>

    /**
     * Returns a service mapping from AID to [NfcApduService]
     */
    abstract fun buildServices(): Map<ByteString, NfcApduService>

    override fun onCreate() {
        super.onCreate()

        services = buildServices()

        services.forEach { (_, service) ->
            service.onCreate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        services.forEach { (_, service) ->
            service.onDestroy()
        }
    }

    // Track which AID the reader selected so subsequent APDUs are routed correctly
    private var activeAid: ByteString? = null

    override fun processCommandApdu(encodedCommandApdu: ByteArray): ByteArray? {
        Logger.dHex(TAG, "Handling Command APDU", encodedCommandApdu)

        // With Extended APDUs it's possible we get a partial APDU so gracefully handle if decoding fails. Simply
        // log and discard, we'll likely get hit with an onDeactivated call soon anyway.
        //
        val commandApdu = try {
            CommandApdu.decode(encodedCommandApdu)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "Error decoding APDU", e)
            return null
        }
        if (commandApdu.ins == Nfc.INS_SELECT && commandApdu.p1 == Nfc.INS_SELECT_P1_APPLICATION) {
            activeAid = commandApdu.payload
            Logger.i(TAG, "AID ${activeAid!!.toHex()} has been selected")
        }

        // Route the APDU to the appropriate handler based on the active session
        activeAid?.let { activeAid ->
            services[activeAid]?.let { service ->
                return service.processCommandApdu(encodedCommandApdu)
            }
            Logger.w(TAG, "No handler for AID ${activeAid.toHex()}, returning 6a82")
            return ResponseApdu(Nfc.RESPONSE_STATUS_ERROR_FILE_OR_APPLICATION_NOT_FOUND).encode()
        }
        return null
    }

    override fun onDeactivated(reason: Int) {
        Logger.d(TAG, "onDeactivated reason $reason")
        activeAid?.let { activeAid ->
            services[activeAid]?.onDeactivated(reason)
        }
        activeAid = null
    }
}

/**
 * Platform base class for [CombinedNfcService].
 *
 * On Android this extends `android.nfc.cardemulation.HostApduService`.
 */
expect abstract class CombinedNfcServiceBase() {
    /**
     * Called when the service is first created.
     */
    open fun onCreate()

    /**
     * Called when the service is no longer used and is being destroyed.
     */
    open fun onDestroy()

    /**
     * Called when a command APDU has been received from a remote device.
     *
     * @param encodedCommandApdu the command APDU that was received.
     * @return a response APDU to send back synchronously, or `null` if the response will be sent later via [sendResponseApdu].
     */
    abstract fun processCommandApdu(encodedCommandApdu: ByteArray): ByteArray?

    /**
     * Called when the NFC link has been deactivated or deselected.
     *
     * @param reason the reason for deactivation.
     */
    abstract fun onDeactivated(reason: Int)

    /**
     * Sends a response APDU back to the remote device.
     *
     * @param responseApdu the response APDU bytes.
     */
    fun sendResponseApdu(responseApdu: ByteArray)
}

internal expect fun vibrateMdocNfcError()
internal expect fun vibrateMdocNfcSuccess()
