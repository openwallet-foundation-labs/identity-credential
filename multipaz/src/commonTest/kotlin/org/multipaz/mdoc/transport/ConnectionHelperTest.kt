package org.multipaz.mdoc.transport

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPublicKey
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodNfc
import org.multipaz.nfc.CommandApdu
import org.multipaz.nfc.Nfc
import org.multipaz.mdoc.role.MdocRole
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionHelperTest {
    private lateinit var senderKey: EcPublicKey

    @BeforeTest
    fun setup() = runTest {
        senderKey = Crypto.createEcPrivateKey(EcCurve.P256).use { it.publicKey }
    }

    @Test
    fun returnsWhileSelectedTransportIsStillConnecting() = runTest {
        val transport = TestTransport()
        val (caller, result) = waitFor(transport)
        try {
            runCurrent()
            transport.connecting()
            runCurrent()
            assertTrue(result.isCompleted, "waitForConnection must return before open completes")
            assertSame(transport, result.await())
            assertEquals(MdocTransport.State.CONNECTING, transport.state.value)
            assertFalse(transport.openCompleted)
            assertFalse(transport.openCancelled)
        } finally {
            caller.cancel()
            caller.join()
        }
    }

    @Test
    fun selectedOpenRemainsOwnedBySessionAfterWaiterReturns() = runTest {
        val transport = TestTransport()
        val (caller, result) = waitFor(transport)
        try {
            runCurrent()
            transport.connecting()
            runCurrent()
            assertTrue(result.isCompleted)
            assertTrue(caller.isActive)
            transport.finishOpen.complete(Unit)
            runCurrent()
            assertTrue(caller.isActive)
            assertTrue(transport.openCompleted)
            assertEquals(MdocTransport.State.CONNECTED, transport.state.value)
            assertEquals(0, transport.closeCalls)
        } finally {
            caller.cancel()
            caller.join()
        }
    }

    @Test
    fun returnsWhenOpenConnectsImmediately() = runTest {
        val transport = TestTransport().apply { finishOpen.complete(Unit) }
        val (caller, result) = waitFor(transport)
        runCurrent()
        assertSame(transport, result.await())
        assertEquals(MdocTransport.State.CONNECTED, transport.state.value)
        caller.cancel()
        caller.join()
        assertEquals(1, transport.closeCalls)
    }

    @Test
    fun cancellationBeforeSelectionCancelsOpenAndClosesAllTransports() = runTest {
        val first = TestTransport()
        val second = TestTransport()
        val (caller, result) = waitFor(first, second)
        runCurrent()
        caller.cancel()
        caller.join()
        assertTrue(result.isCancelled)
        for (transport in listOf(first, second)) {
            assertTrue(transport.openCancelled)
            assertTrue(transport.closeCalls > 0)
            assertEquals(MdocTransport.State.CLOSED, transport.state.value)
        }
        assertFalse(caller.children.any())
    }

    @Test
    fun cancellationAfterSelectionCancelsAndClosesSelectedOpen() = runTest {
        val transport = TestTransport()
        val (caller, result) = waitFor(transport)
        try {
            runCurrent()
            transport.connecting()
            runCurrent()
            assertTrue(result.isCompleted)
            assertSame(transport, result.await())
        } finally {
            caller.cancel()
            caller.join()
        }
        assertTrue(transport.openCancelled)
        assertEquals(MdocTransport.State.CLOSED, transport.state.value)
        assertFalse(caller.children.any())
    }

    @Test
    fun losingTransportIsClosedAndItsBlockedOpenIsCancelled() = runTest {
        val winner = TestTransport()
        val loser = TestTransport()
        val (caller, result) = waitFor(winner, loser)
        try {
            runCurrent()
            winner.connecting()
            runCurrent()
            assertTrue(result.isCompleted)
            assertSame(winner, result.await())
            assertTrue(loser.openCancelled)
            assertEquals(MdocTransport.State.CLOSED, loser.state.value)
            assertFalse(winner.openCancelled)
        } finally {
            caller.cancel()
            caller.join()
        }
    }

    @Test
    fun simultaneousConnectionAttemptsSelectOnlyOneTransport() = runTest {
        val first = TestTransport()
        val second = TestTransport()
        val (caller, result) = waitFor(first, second)
        try {
            runCurrent()
            first.connecting()
            second.connecting()
            runCurrent()
            assertTrue(result.isCompleted)
            val winner = result.await()
            val loser = if (winner === first) second else first
            assertEquals(MdocTransport.State.CONNECTING, winner.state.value)
            assertEquals(MdocTransport.State.CLOSED, loser.state.value)
            assertTrue(loser.openCancelled)
        } finally {
            caller.cancel()
            caller.join()
        }
    }

    @Test
    fun failedTransportDoesNotPreventAnotherFromBeingSelected() = runTest {
        val failed = TestTransport()
        val winner = TestTransport()
        val (caller, result) = waitFor(failed, winner)
        try {
            runCurrent()
            failed.failOpen()
            winner.connecting()
            runCurrent()
            assertTrue(result.isCompleted)
            assertSame(winner, result.await())
            assertTrue(failed.closeCalls > 0)
        } finally {
            caller.cancel()
            caller.join()
        }
    }

    @Test
    fun allFailedTransportsThrowAndLeaveNoOpenJobs() = runTest {
        val first = TestTransport()
        val second = TestTransport()
        val (caller, result) = waitFor(first, second)
        runCurrent()
        first.failOpen()
        second.failOpen()
        runCurrent()
        assertTrue(result.isCompleted)
        assertFailsWith<IllegalStateException> { result.await() }
        caller.join()
        assertFalse(caller.children.any())
        assertTrue(first.closeCalls > 0)
        assertTrue(second.closeCalls > 0)
    }

    @Test
    fun selectedOpenFailureClosesTransportAndFinishesItsJob() = runTest {
        val transport = TestTransport()
        val (caller, result) = waitFor(transport)
        try {
            runCurrent()
            transport.connecting()
            runCurrent()
            assertTrue(result.isCompleted)
            assertSame(transport, result.await())
            transport.failOpen()
            runCurrent()
            caller.cancel()
            caller.join()
            assertTrue(transport.closeCalls > 0)
            assertEquals(MdocTransport.State.FAILED, transport.state.value)
            assertFalse(caller.children.any())
        } finally {
            caller.cancel()
            caller.join()
        }
    }

    @Test
    fun openExceptionWithoutStateUpdateDoesNotLeaveWaiterSuspended() = runTest {
        val transport = TestTransport(IllegalStateException("Synthetic open failure"))
        val (caller, result) = waitFor(transport)
        try {
            runCurrent()
            assertTrue(result.isCompleted)
            assertFailsWith<IllegalStateException> { result.await() }
            assertEquals(MdocTransport.State.CLOSED, transport.state.value)
        } finally {
            caller.cancel()
            caller.join()
        }
    }

    @Test
    fun cleanupFailureStillClosesOtherTransportsAndCancelsTheirJobs() = runTest {
        val winner = TestTransport()
        val closeError = IllegalStateException("Synthetic close failure")
        val loser = TestTransport(closeError = closeError)
        val (caller, result) = waitFor(winner, loser)
        try {
            runCurrent()
            winner.connecting()
            runCurrent()
            assertTrue(result.isCompleted)
            assertEquals(closeError.message, assertFailsWith<IllegalStateException> { result.await() }.message)
            assertEquals(MdocTransport.State.CLOSED, winner.state.value)
            assertTrue(winner.openCancelled)
            assertTrue(loser.openCancelled)
            caller.join()
            assertFalse(caller.children.any())
        } finally {
            caller.cancel()
            caller.join()
        }
    }

    @Test
    fun openAndCloseErrorsDoNotLeaveWaiterSuspended() = runTest {
        val transport = TestTransport(
            openError = IllegalStateException("Synthetic open failure"),
            closeError = IllegalStateException("Synthetic close failure")
        )
        val (caller, result) = waitFor(transport)
        try {
            runCurrent()
            assertTrue(result.isCompleted)
            assertFailsWith<IllegalStateException> { result.await() }
            caller.join()
            assertFalse(caller.children.any())
        } finally {
            caller.cancel()
            caller.join()
        }
    }

    @Test
    fun emptyListFailsWithoutLaunchingOpenJobs() = runTest {
        assertFailsWith<IllegalStateException> { MdocConnectionSession(emptyList(), senderKey) }
    }

    @Test
    fun invalidInitialStateDoesNotOpenAnyTransport() = runTest {
        val first = TestTransport()
        val invalid = TestTransport().apply { state.value = MdocTransport.State.CONNECTED }
        assertFailsWith<IllegalStateException> { MdocConnectionSession(listOf(first, invalid), senderKey) }
        assertFalse(first.openStarted)
        assertFalse(invalid.openStarted)
    }

    @Test
    fun asyncWaiterCompletesBeforeSelectedOpen() = runTest {
        val transport = TestTransport()
        val session = session(transport)
        try {
            val waiter = async { session.waitForConnection() }
            runCurrent()
            transport.connecting()
            runCurrent()
            assertTrue(waiter.isCompleted)
            assertSame(transport, waiter.await())
            assertFalse(transport.openCompleted)
            transport.finishOpen.complete(Unit)
            runCurrent()
            assertTrue(transport.openCompleted)
        } finally {
            session.close()
            session.awaitClosed()
        }
        assertEquals(1, transport.closeCalls)
    }

    @Test
    fun closeBeforeSessionStartsClosesAllTransports() = runTest {
        val first = TestTransport()
        val second = TestTransport()
        val session = session(first, second)
        session.close()
        session.close()
        session.awaitClosed()
        assertEquals(1, first.closeCalls)
        assertEquals(1, second.closeCalls)
        assertFailsWith<CancellationException> { session.waitForConnection() }
    }

    @Test
    fun selectedFailureIsObservableWhenSessionTerminates() = runTest {
        val transport = TestTransport()
        val session = session(transport)
        val waiter = async { session.waitForConnection() }
        runCurrent()
        transport.connecting()
        runCurrent()
        waiter.await()
        transport.finishOpen.completeExceptionally(IllegalStateException("Synthetic connection failure"))
        runCurrent()
        val error = assertFailsWith<IllegalStateException> { session.awaitClosed() }
        assertEquals("Synthetic connection failure", error.message)
        assertEquals(1, transport.closeCalls)
    }

    @Test
    fun closingSelectedTransportTerminatesSession() = runTest {
        val transport = TestTransport().apply { finishOpen.complete(Unit) }
        val session = session(transport)
        session.waitForConnection()
        transport.state.value = MdocTransport.State.CLOSED
        session.awaitClosed()
        assertEquals(1, transport.closeCalls)
    }

    @Test
    fun cleanupErrorsArePreservedAlongsideOpenFailure() = runTest {
        val session = session(TestTransport(
            openError = IllegalStateException("Synthetic open failure"),
            closeError = IllegalStateException("Synthetic close failure")
        ))
        val error = assertFailsWith<IllegalStateException> { session.awaitClosed() }
        assertEquals("Synthetic open failure", error.message)
        assertTrue(generateSequence<Throwable>(error) { it.cause }
            .flatMap { it.suppressedExceptions.asSequence() }
            .any { it.message == "Synthetic close failure" })
    }

    @Test
    fun duplicateTransportsAreRejectedWithoutOpening() = runTest {
        val transport = TestTransport()
        assertFailsWith<IllegalStateException> { session(transport, transport) }
        assertFalse(transport.openStarted)
    }

    @Test
    fun selectedFailureStateClosesEvenWhenOpenIsBlocked() = runTest {
        val transport = TestTransport()
        val session = session(transport)
        val waiter = async { session.waitForConnection() }
        runCurrent()
        transport.connecting()
        runCurrent()
        waiter.await()
        transport.state.value = MdocTransport.State.FAILED
        assertFailsWith<IllegalStateException> { session.awaitClosed() }
        assertTrue(transport.openCancelled)
        assertEquals(1, transport.closeCalls)
    }

    @Test
    fun openingDoesNotResumeOnStoppedWaiterDispatcher() = runTest {
        val delegate = StandardTestDispatcher(testScheduler)
        var stopped = false
        var lateDispatches = 0
        val callDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                if (stopped) lateDispatches += 1
                delegate.dispatch(context, block)
            }
        }
        val transport = TestTransport()
        val session = session(transport)
        try {
            val waiter = async(callDispatcher) {
                val selected = session.waitForConnection()
                stopped = true // SKIE stops its call-local dispatcher when the result is delivered.
                selected
            }
            runCurrent()
            transport.connecting()
            runCurrent()
            assertTrue(waiter.isCompleted)
            assertSame(transport, waiter.await())
            transport.finishOpen.complete(Unit)
            runCurrent()
            assertTrue(transport.openCompleted)
            assertEquals(0, lateDispatches)
        } finally {
            session.close()
            session.awaitClosed()
        }
        assertEquals(0, lateDispatches)
    }

    @Test
    fun closeCancellationStillClosesOtherTransports() = runTest {
        val first = TestTransport(closeError = CancellationException("Synthetic close cancellation"))
        val second = TestTransport()
        val session = session(first, second)
        session.close()
        assertFailsWith<CancellationException> { session.awaitClosed() }
        assertEquals(1, first.closeCalls)
        assertEquals(1, second.closeCalls)
        assertFalse(second.openStarted && !second.openCancelled)
    }

    @Test
    fun cancelledOwnerCanAwaitCleanup() = runTest {
        val transport = TestTransport()
        val session = session(transport)
        var cleanupFinished = false
        val caller = launch {
            try {
                session.waitForConnection()
                awaitCancellation()
            } finally {
                session.close()
                session.awaitClosed()
                cleanupFinished = true
            }
        }
        runCurrent()
        caller.cancel()
        caller.join()
        assertTrue(cleanupFinished)
        assertEquals(1, transport.closeCalls)
        assertTrue(transport.openCancelled)
    }

    @Test
    fun nfcOpenReturningIdleWaitsForSelectApdu() = runTest {
        assertNfcSelection(withOtherTransport = false)
    }

    @Test
    fun nfcRemainsCandidateAfterOpenReturnsWithAnotherTransport() = runTest {
        assertNfcSelection(withOtherTransport = true)
    }

    private suspend fun TestScope.assertNfcSelection(withOtherTransport: Boolean) {
        val nfc = NfcTransportMdoc(
            role = MdocRole.MDOC,
            options = MdocTransportOptions(),
            connectionMethod = MdocConnectionMethodNfc(0xffff, 0x10000)
        )
        val other = TestTransport()
        val session = session(*if (withOtherTransport) arrayOf(nfc, other) else arrayOf(nfc))
        val result = CompletableDeferred<MdocTransport>()
        val waiter = launch {
            try {
                result.complete(session.waitForConnection())
            } catch (error: Exception) {
                result.completeExceptionally(error)
            }
        }
        try {
            runCurrent()
            assertFalse(result.isCompleted, "A successful NFC open must keep waiting for SELECT")
            assertEquals(MdocTransport.State.IDLE, nfc.state.value)
            nfc.processApdu(
                CommandApdu(0, Nfc.INS_SELECT, Nfc.INS_SELECT_P1_APPLICATION, 0,
                    Nfc.ISO_MDOC_NFC_DATA_TRANSFER_APPLICATION_ID, 0),
                sendResponse = { }
            )
            runCurrent()
            assertTrue(result.isCompleted, "NFC must still be eligible after open returned")
            assertSame(nfc, result.await())
            assertEquals(MdocTransport.State.CONNECTED, nfc.state.value)
            if (withOtherTransport) {
                assertEquals(1, other.closeCalls)
                assertTrue(other.openCancelled)
            }
        } finally {
            session.close()
            try {
                session.awaitClosed()
            } finally {
                waiter.cancel()
                waiter.join()
            }
        }
    }

    @Test
    fun winnerOpenFailureDuringLoserCleanupIsNotPublished() = runTest {
        assertWinnerNotPublishedDuringCleanup(openFailure = true)
    }

    @Test
    fun winnerFailedDuringLoserCleanupIsNotPublished() = runTest {
        assertWinnerNotPublishedDuringCleanup(terminalState = MdocTransport.State.FAILED)
    }

    @Test
    fun winnerClosedDuringLoserCleanupIsNotPublished() = runTest {
        assertWinnerNotPublishedDuringCleanup(terminalState = MdocTransport.State.CLOSED)
    }

    private suspend fun TestScope.assertWinnerNotPublishedDuringCleanup(
        openFailure: Boolean = false,
        terminalState: MdocTransport.State? = null
    ) {
        val winner = TestTransport()
        val cleanupGate = CompletableDeferred<Unit>()
        val loser = TestTransport(closeGate = cleanupGate)
        val session = session(winner, loser)
        val result = CompletableDeferred<MdocTransport>()
        val waiter = launch {
            try {
                result.complete(session.waitForConnection())
            } catch (error: Exception) {
                result.completeExceptionally(error)
            }
        }
        try {
            runCurrent()
            winner.connecting()
            runCurrent()
            assertTrue(loser.closeStarted.isCompleted)
            assertFalse(result.isCompleted)
            if (openFailure) {
                winner.finishOpen.completeExceptionally(IllegalStateException("Failed during loser cleanup"))
            } else {
                winner.state.value = terminalState!!
            }
            runCurrent()
            cleanupGate.complete(Unit)
            runCurrent()
            val error = assertFailsWith<IllegalStateException> { result.await() }
            if (openFailure) assertEquals("Failed during loser cleanup", error.message)
        } finally {
            cleanupGate.complete(Unit)
            session.close()
            try {
                assertFailsWith<IllegalStateException> { session.awaitClosed() }
            } finally {
                waiter.cancel()
                waiter.join()
            }
        }
        assertEquals(1, winner.closeCalls)
        assertEquals(1, loser.closeCalls)
    }

    private fun TestScope.session(vararg transports: MdocTransport) =
        MdocConnectionSession(transports.toList(), senderKey, StandardTestDispatcher(testScheduler))

    private fun TestScope.waitFor(vararg transports: TestTransport): Pair<Job, CompletableDeferred<MdocTransport>> {
        val result = CompletableDeferred<MdocTransport>()
        val session = session(*transports)
        val caller = launch {
            try {
                result.complete(session.waitForConnection())
                awaitCancellation()
            } catch (error: CancellationException) {
                result.cancel(error)
                throw error
            } catch (error: Exception) {
                result.completeExceptionally(error)
            } finally {
                session.close()
                withContext(NonCancellable) {
                    try {
                        session.awaitClosed()
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        // Tests below assert the error using the explicit termination API.
                    }
                }
            }
        }
        return caller to result
    }

    private class TestTransport(
        private val openError: Exception? = null,
        private val closeError: Exception? = null,
        private val closeGate: CompletableDeferred<Unit>? = null
    ) : MdocTransport() {
        override val state = MutableStateFlow(State.IDLE)
        override val role = MdocRole.MDOC
        override val connectionMethod = MdocConnectionMethodNfc(0xffff, 0x10000)
        override val scanningTime: Duration? = null
        val finishOpen = CompletableDeferred<Unit>()
        val closeStarted = CompletableDeferred<Unit>()
        var openStarted = false
        var openCompleted = false
        var openCancelled = false
        var closeCalls = 0

        fun connecting() { state.value = State.CONNECTING }

        fun failOpen() {
            state.value = State.FAILED
            finishOpen.completeExceptionally(IllegalStateException("Synthetic connection failure"))
        }

        override suspend fun advertise() { state.value = State.ADVERTISING }

        override suspend fun open(eSenderKey: EcPublicKey) {
            openStarted = true
            try {
                openError?.let { throw it }
                finishOpen.await()
                state.value = State.CONNECTED
                openCompleted = true
            } catch (error: CancellationException) {
                openCancelled = true
                throw error
            }
        }

        override suspend fun close() {
            yield() // Cleanup must also work when called from a cancelled coroutine.
            closeCalls += 1
            closeStarted.complete(Unit)
            closeGate?.await()
            closeError?.let { throw it }
            if (state.value != State.FAILED) state.value = State.CLOSED
            // Deliberately leave open blocked: the helper must cancel its losing jobs.
        }

        override suspend fun sendMessage(message: ByteArray) { throw UnsupportedOperationException() }
        override suspend fun waitForMessage(): ByteArray { throw UnsupportedOperationException() }
    }
}
