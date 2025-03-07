package org.multipaz.mdoc.transport

import org.multipaz.crypto.EcPublicKey
import org.multipaz.mdoc.connectionmethod.ConnectionMethod
import org.multipaz.mdoc.connectionmethod.ConnectionMethodBle
import org.multipaz.util.Logger
import org.multipaz.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

internal class BleTransportPeripheralMdoc(
    override val role: Role,
    private val options: MdocTransportOptions,
    private val peripheralManager: BlePeripheralManager,
    private val uuid: UUID
) : MdocTransport() {
    companion object {
        private const val TAG = "BleTransportPeripheralMdoc"
    }

    private val mutex = Mutex()
    private var currentJob: Job? = null

    private val _state = MutableStateFlow<State>(State.IDLE)
    override val state: StateFlow<State> = _state.asStateFlow()

    override val connectionMethod: ConnectionMethod
        get() {
            val cm = ConnectionMethodBle(true, false, uuid, null)
            peripheralManager.l2capPsm?.let { cm.peripheralServerModePsm = it }
            return cm
        }

    init {
        peripheralManager.setUuids(
            stateCharacteristicUuid = UUID.fromString("00000001-a123-48ce-896b-4c76973373e6"),
            client2ServerCharacteristicUuid = UUID.fromString("00000002-a123-48ce-896b-4c76973373e6"),
            server2ClientCharacteristicUuid = UUID.fromString("00000003-a123-48ce-896b-4c76973373e6"),
            identCharacteristicUuid = null,
            l2capCharacteristicUuid = if (options.bleUseL2CAP) {
                UUID.fromString("0000000a-a123-48ce-896b-4c76973373e6")
            } else {
                null
            }
        )
        peripheralManager.setCallbacks(
            onError = { error ->
                runBlocking {
                    mutex.withLock {
                        failTransport(error)
                    }
                }
            },
            onClosed = {
                Logger.w(TAG, "BlePeripheralManager close")
                runBlocking {
                    mutex.withLock {
                        closeWithoutDelay()
                    }
                }
            }
        )
    }

    @OptIn(InternalCoroutinesApi::class)
    override suspend fun advertise() {
        mutex.withLock {
            check(_state.value == State.IDLE) { "Expected state IDLE, got ${_state.value}" }
            try {
                currentJob = CoroutineScope(currentCoroutineContext()).launch {
                    peripheralManager.waitForPowerOn()
                    peripheralManager.advertiseService(uuid)
                    _state.value = State.ADVERTISING
                }
                currentJob!!.join()
                if (currentJob!!.isCancelled) {
                    throw currentJob!!.getCancellationException()
                }
            } catch (error: Throwable) {
                failTransport(error)
                throw MdocTransportException("Failed while advertising", error)
            } finally {
                currentJob = null
            }
        }
    }

    override val scanningTime: Duration?
        get() = null

    @OptIn(InternalCoroutinesApi::class)
    override suspend fun open(eSenderKey: EcPublicKey) {
        mutex.withLock {
            check(_state.value == State.IDLE || _state.value == State.ADVERTISING) {
                "Expected state IDLE or ADVERTISING, got ${_state.value}"
            }
            try {
                currentJob = CoroutineScope(currentCoroutineContext()).launch {
                    if (_state.value != State.ADVERTISING) {
                        // Start advertising if we aren't already...
                        _state.value = State.ADVERTISING
                        peripheralManager.waitForPowerOn()
                        peripheralManager.advertiseService(uuid)
                    }
                    peripheralManager.setESenderKey(eSenderKey)
                    // Note: It's not really possible to know someone is connecting to us until they're _actually_
                    // connected. I mean, for all we know, someone could be BLE scanning us. So not really possible
                    // to go into State.CONNECTING...
                    peripheralManager.waitForStateCharacteristicWriteOrL2CAPClient()
                    _state.value = State.CONNECTED
                }
                currentJob!!.join()
                if (currentJob!!.isCancelled) {
                    throw currentJob!!.getCancellationException()
                }
            } catch (error: Throwable) {
                failTransport(error)
                throw MdocTransportException("Failed while opening transport", error)
            } finally {
                currentJob = null
            }
        }
    }

    override suspend fun waitForMessage(): ByteArray {
        mutex.withLock {
            check(_state.value == State.CONNECTED) { "Expected state CONNECTED, got ${_state.value}" }
        }
        try {
            return peripheralManager.incomingMessages.receive()
        } catch (error: Throwable) {
            if (_state.value == State.CLOSED) {
                throw MdocTransportClosedException("Transport was closed while waiting for message")
            } else {
                mutex.withLock {
                    failTransport(error)
                }
                throw MdocTransportException("Failed while waiting for message", error)
            }
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    override suspend fun sendMessage(message: ByteArray) {
        mutex.withLock {
            check(_state.value == State.CONNECTED) { "Expected state CONNECTED, got ${_state.value}" }
            if (message.isEmpty() && peripheralManager.usingL2cap) {
                throw MdocTransportTerminationException("Transport-specific termination not available with L2CAP")
            }
            try {
                currentJob = CoroutineScope(currentCoroutineContext()).launch {
                    if (message.isEmpty()) {
                        peripheralManager.writeToStateCharacteristic(BleTransportConstants.STATE_CHARACTERISTIC_END)
                    } else {
                        peripheralManager.sendMessage(message)
                    }
                }
                currentJob!!.join()
                if (currentJob!!.isCancelled) {
                    throw currentJob!!.getCancellationException()
                }
            } catch (error: Throwable) {
                failTransport(error)
                throw MdocTransportException("Failed while sending message", error)
            } finally {
                currentJob = null
            }
        }
    }

    private fun failTransport(error: Throwable) {
        check(mutex.isLocked) { "failTransport called without holding lock" }
        if (_state.value == State.FAILED || _state.value == State.CLOSED) {
            return
        }
        Logger.w(TAG, "Failing transport with error", error)
        peripheralManager.close()
        _state.value = State.FAILED
    }

    private fun closeWithoutDelay() {
        check(mutex.isLocked) { "closeWithoutDelay called without holding lock" }
        peripheralManager.close()
        _state.value = State.CLOSED
    }

    override suspend fun close() {
        currentJob?.cancel("close() was called")
        mutex.withLock {
            if (_state.value == State.FAILED || _state.value == State.CLOSED) {
                return
            }
            peripheralManager.close()
            _state.value = State.CLOSED
        }
    }
}
