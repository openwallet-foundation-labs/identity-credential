package org.multipaz.mdoc.transport

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import org.multipaz.crypto.EcPublicKey
import org.multipaz.util.Logger

/**
 * Owns connection attempts until the selected transport is no longer needed.
 *
 * [waitForConnection] returns as soon as a peer starts connecting. Opening the selected transport
 * continues in this session, independently of the waiting coroutine and its dispatcher. This also
 * permits waiting using Kotlin `async` or a Swift async call with a call-local dispatcher.
 *
 * The owner must call [close] in a `finally` block (Swift: `defer`) and call [awaitClosed] to observe
 * connection or cleanup failures. Closing is safe from a cancelled caller and is idempotent.
 * Swift callers must await termination from an uncancelled task, for example
 * `await Task { try await session.awaitClosed() }.result`, so cancellation does not prevent SKIE
 * from entering the cleanup call.
 *
 * @param transports advertised or idle transports; ownership is transferred to this session.
 * @param eSenderKey `EDeviceKey` for forward engagement or `EReaderKey` for reverse engagement.
 * @throws IllegalStateException if the list is empty, contains duplicates, or a transport is neither
 *   [MdocTransport.State.ADVERTISING] nor [MdocTransport.State.IDLE]. Ownership is not transferred
 *   when validation fails.
 */
class MdocConnectionSession internal constructor(
    transports: List<MdocTransport>,
    private val eSenderKey: EcPublicKey,
    dispatcher: CoroutineDispatcher
) : AutoCloseable {
    private val transports = transports.toList().also { list ->
        check(list.isNotEmpty()) { "No transports to connect" }
        check(list.distinct().size == list.size) { "Duplicate transports" }
        list.forEach { transport ->
            check(transport.state.value == MdocTransport.State.ADVERTISING ||
                    transport.state.value == MdocTransport.State.IDLE) {
                "Expected state ADVERTISING or IDLE for $transport, got ${transport.state.value}"
            }
        }
    }
    private val owner = Job()
    private val closeRequested = CompletableDeferred<Unit>()
    private val selected = CompletableDeferred<MdocTransport>()
    private val termination = CompletableDeferred<Unit>()
    private val sessionJob = CoroutineScope(dispatcher + owner).launch {
        var failure: Exception? = null
        try {
            runSession()
        } catch (error: Exception) {
            failure = error
        }
        if (!selected.isCompleted) {
            selected.completeExceptionally(failure ?: CancellationException("Connection session closed"))
        }
        if (failure == null) termination.complete(Unit) else termination.completeExceptionally(failure)
        if (failure is CancellationException) throw failure
    }.also { job -> job.invokeOnCompletion { owner.complete() } }

    @Throws(IllegalStateException::class)
    constructor(transports: List<MdocTransport>, eSenderKey: EcPublicKey) :
            this(transports, eSenderKey, Dispatchers.Default)

    /**
     * Waits for the first [MdocTransport.State.CONNECTING] or [MdocTransport.State.CONNECTED] transport.
     * Other transports are closed before returning. Cancelling this wait closes the entire session.
     * After returning, the owner remains responsible for [close], including on cancellation.
     *
     * @throws Exception if no connection succeeds or closing another transport fails.
     * @throws CancellationException if the wait is cancelled or the session is closed before selection.
     */
    @Throws(Exception::class, CancellationException::class)
    suspend fun waitForConnection(): MdocTransport {
        try {
            return selected.await()
        } catch (error: CancellationException) {
            close()
            throw error
        }
    }

    /** Requests termination and cleanup without requiring a live coroutine or Swift async call. */
    override fun close() {
        closeRequested.complete(Unit)
    }

    /**
     * Waits for every opening job to finish and every transport to be closed.
     * Call [close] first when ending a successful session. Cleanup is awaited even by a cancelled caller.
     *
     * @throws Exception if opening the selected transport or closing any transport failed. Additional
     *   cleanup failures are attached as suppressed exceptions.
     */
    @Throws(Exception::class)
    suspend fun awaitClosed() = withContext(NonCancellable) {
        sessionJob.join()
        termination.await()
    }

    private suspend fun runSession() = coroutineScope {
        val opening = transports.associateWith { transport ->
            async {
                try {
                    transport.open(eSenderKey)
                    null
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Logger.e("MdocConnectionSession", "Error opening ${transport.connectionMethod}", error)
                    error
                }
            }
        }
        val waiting = transports.associateWith { transport ->
            async {
                val state = async {
                    transport.state.first { it != MdocTransport.State.IDLE &&
                            it != MdocTransport.State.ADVERTISING && it != MdocTransport.State.SCANNING }
                }
                try {
                    select {
                        state.onAwait { it == MdocTransport.State.CONNECTING || it == MdocTransport.State.CONNECTED }
                        opening.getValue(transport).onAwait { error ->
                            if (error != null) false else {
                                val observed = state.await()
                                observed == MdocTransport.State.CONNECTING || observed == MdocTransport.State.CONNECTED
                            }
                        }
                    }
                } finally {
                    state.cancel()
                }
            }
        }.toMutableMap()
        val closed = mutableSetOf<MdocTransport>()
        var failure: Exception? = null

        suspend fun closeTransports(toClose: List<MdocTransport>): Exception? {
            var closeFailure: Exception? = null
            toClose.forEach { opening.getValue(it).cancel() }
            for (transport in toClose) {
                if (closed.add(transport)) {
                    try {
                        transport.close()
                    } catch (error: Exception) {
                        if (closeFailure == null) closeFailure = error
                        else if (closeFailure !== error) closeFailure.addSuppressed(error)
                    }
                }
            }
            toClose.map { opening.getValue(it) }.joinAll()
            return closeFailure
        }

        try {
            var winner: MdocTransport? = null
            while (waiting.isNotEmpty() && winner == null) {
                val candidate = select<Pair<MdocTransport, Boolean>?> {
                    closeRequested.onAwait { null }
                    waiting.forEach { (transport, ready) -> ready.onAwait { transport to it } }
                } ?: return@coroutineScope
                waiting.remove(candidate.first)
                if (candidate.second && (candidate.first.state.value == MdocTransport.State.CONNECTING ||
                            candidate.first.state.value == MdocTransport.State.CONNECTED)) {
                    winner = candidate.first
                }
            }
            if (winner == null) {
                val errors = opening.values.filter { it.isCompleted }.mapNotNull { it.await() }
                val error = errors.firstOrNull() ?: IllegalStateException("No transport connected")
                errors.drop(1).filter { it !== error }.forEach { error.addSuppressed(it) }
                throw error
            }
            waiting.values.forEach { it.cancel() }
            closeTransports(transports.filter { it !== winner })?.let { throw it }
            if (closeRequested.isCompleted) return@coroutineScope
            // Closing losers may suspend while the selected connection fails or terminates.
            val selectedOpen = opening.getValue(winner)
            if (selectedOpen.isCompleted) selectedOpen.await()?.let { throw it }
            check(winner.state.value == MdocTransport.State.CONNECTING ||
                    winner.state.value == MdocTransport.State.CONNECTED) {
                "Selected transport terminated before selection: ${winner.state.value}"
            }
            selected.complete(winner)

            val terminal = async {
                winner.state.first { it == MdocTransport.State.FAILED || it == MdocTransport.State.CLOSED }
            }
            try {
                val opened = select<Boolean> {
                    closeRequested.onAwait { false }
                    opening.getValue(winner).onAwait {
                        it?.let { error -> throw error }
                        true
                    }
                    terminal.onAwait {
                        check(it != MdocTransport.State.FAILED) { "Selected transport failed" }
                        false
                    }
                }
                if (opened) {
                    select<Unit> {
                        closeRequested.onAwait { }
                        terminal.onAwait { check(it != MdocTransport.State.FAILED) { "Selected transport failed" } }
                    }
                }
            } finally {
                terminal.cancel()
            }
        } catch (error: Exception) {
            failure = error
            throw error
        } finally {
            waiting.values.forEach { it.cancel() }
            withContext(NonCancellable) {
                closeTransports(transports)?.let { cleanupError ->
                    if (failure == null) throw cleanupError
                    if (failure !== cleanupError) failure.addSuppressed(cleanupError)
                }
            }
        }
    }
}
