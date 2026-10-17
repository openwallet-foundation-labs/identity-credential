package org.multipaz.nfc

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.multipaz.util.Logger
import kotlin.concurrent.Volatile

/**
 * Base class for NFC APDU services orchestrated by [NfcApduRouter].
 *
 * Incoming [CommandApdu]s and [NfcDeactivationReason] events received via [receiveCommandApdu]
 * and [deactivate] are sequentially dispatched on an internal coroutine running on
 * [Dispatchers.Default] to [processCommandApdu] and [processDeactivated].
 *
 * Subclasses are expected to override:
 * - [processCommandApdu]: to handle each incoming [CommandApdu] and return the corresponding
 *   [ResponseApdu]. Note that [NfcApduRouter] forwards the initial `SELECT APPLICATION` command
 *   itself to the matched service, so implementations are expected to check
 *   [CommandApdu.isApplicationSelect] in [processCommandApdu] (both to return the
 *   `SELECT APPLICATION` response and to reset per-tap state if a new `SELECT APPLICATION`
 *   arrives mid-field).
 * - [processDeactivated]: to clean up per-tap engagement or session state when the NFC link is
 *   deactivated or another application is selected.
 * - [start] / [stop] (optional): to perform service lifecycle setup or teardown (subclasses
 *   must call `super.start()` / `super.stop()`). Note that applications must **not** make
 *   assumptions about when [start] and [stop] are called relative to NFC taps: on Android, the
 *   system's `HostEmulationManager` may bind the underlying `HostApduService` eagerly (for
 *   example when `CardEmulation.setPreferredService()` is called by a foreground activity or for
 *   a default payment service) and keep a single instance alive across zero, one, or many NFC
 *   taps, or it may bind on-demand when a `SELECT AID` APDU arrives and unbind (`onDestroy()` ->
 *   [stop]) immediately upon NFC link loss while a BLE or multi-tap presentment transaction is
 *   still in progress. Per-tap state should therefore be managed via [processCommandApdu] and
 *   [processDeactivated] rather than [start] and [stop].
 */
abstract class NfcApduService {
    companion object {
        private const val TAG = "NfcApduService"
    }

    internal var sendResponseCallback: ((ResponseApdu) -> Unit)? = null

    /**
     * [CoroutineScope] tied to the lifecycle of this service (cancelled in [stop]).
     */
    protected val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private sealed class Event {
        data class Command(val commandApdu: CommandApdu) : Event()
        data class Deactivated(val reason: NfcDeactivationReason) : Event()
    }

    private val eventChannel = Channel<Event>(Channel.UNLIMITED)
    private var dispatchJob: Job? = null

    @Volatile
    private var currentCommandJob: Job? = null

    /**
     * Starts the service and its internal APDU dispatch coroutine.
     *
     * Subclasses overriding this method must call `super.start()`. Applications must not make
     * assumptions about when this is called relative to an NFC tap (for example, Android may
     * bind the underlying `HostApduService` well before any tap occurs or keep the same instance
     * bound across multiple taps).
     */
    open fun start() {
        dispatchJob = serviceScope.launch {
            for (event in eventChannel) {
                try {
                    when (event) {
                        is Event.Command -> {
                            val job = launch {
                                val responseApdu = processCommandApdu(event.commandApdu)
                                try {
                                    val callback = checkNotNull(sendResponseCallback) {
                                        "sendResponseCallback is not configured on this NfcApduService"
                                    }
                                    callback(responseApdu)
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Logger.w(TAG, "Error sending response APDU", e)
                                }
                            }
                            currentCommandJob = job
                            job.join()
                            currentCommandJob = null
                        }
                        is Event.Deactivated -> {
                            processDeactivated(event.reason)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        if (!isActive) {
                            throw e
                        }
                        Logger.i(TAG, "APDU processing cancelled (likely due to deactivation)")
                    } else {
                        Logger.e(TAG, "Error processing event from channel", e)
                    }
                }
            }
        }
    }

    /**
     * Stops the service and cancels its internal APDU dispatch coroutine.
     *
     * Subclasses overriding this method must call `super.stop()`. Applications must not make
     * assumptions about when this is called relative to an NFC tap or transaction (for example,
     * Android may unbind and destroy the underlying `HostApduService` immediately after NFC link
     * loss while a BLE or multi-tap transaction is still ongoing, or keep the service bound long
     * after a tap completes).
     */
    open fun stop() {
        currentCommandJob = null
        serviceScope.cancel()
        dispatchJob = null
        eventChannel.close()
    }

    /**
     * Enqueues an incoming [CommandApdu] for sequential processing by [processCommandApdu].
     *
     * @param commandApdu the incoming [CommandApdu].
     */
    fun receiveCommandApdu(commandApdu: CommandApdu) {
        val unused = eventChannel.trySend(Event.Command(commandApdu))
    }

    /**
     * Signals that the NFC link has been deactivated.
     *
     * Cancels any in-flight [processCommandApdu] execution and enqueues [reason] for sequential
     * cleanup by [processDeactivated].
     *
     * @param reason the [NfcDeactivationReason] for deactivation.
     */
    fun deactivate(reason: NfcDeactivationReason) {
        currentCommandJob?.cancel(CancellationException("NFC deactivated ($reason)"))
        val unused = eventChannel.trySend(Event.Deactivated(reason))
    }

    /**
     * Processes a [CommandApdu] sequentially on the service's background coroutine.
     *
     * Implementations are expected to handle the initial `SELECT APPLICATION` command
     * ([CommandApdu.isApplicationSelect]) as well as subsequent application-specific commands.
     *
     * @param commandApdu the [CommandApdu] to process.
     * @return the [ResponseApdu] to send back to the remote NFC reader.
     */
    protected abstract suspend fun processCommandApdu(commandApdu: CommandApdu): ResponseApdu

    /**
     * Processes a deactivation event sequentially on the service's background coroutine.
     *
     * @param reason the [NfcDeactivationReason] for deactivation.
     */
    protected open suspend fun processDeactivated(reason: NfcDeactivationReason) {}
}
