package org.multipaz.mdoc.transport

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPublicKey
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodBle
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BleTransportTest {
    private lateinit var senderKey: EcPublicKey
    private val testUuid = UUID.fromString("b3d52ac4-a1b6-4b51-a22e-78ee55ef6eb6")

    @BeforeTest
    fun setup() = runTest {
        senderKey = Crypto.createEcPrivateKey(EcCurve.P256).use { it.publicKey }
    }

    @Test
    fun peripheralMdocCancelAdvertiseClosesPeripheralManager() = runTest {
        val manager = FakeBlePeripheralManager()
        val transport = BleTransportPeripheralMdoc(
            role = MdocRole.MDOC,
            options = MdocTransportOptions(),
            peripheralManager = manager,
            uuid = testUuid,
        )

        val advertiseDeferred = async { transport.advertise() }
        runCurrent()
        assertFalse(advertiseDeferred.isCompleted)
        assertEquals(0, manager.closeCalls)

        advertiseDeferred.cancel()
        runCurrent()

        assertTrue(advertiseDeferred.isCancelled)
        assertTrue(manager.closeCalls > 0)
        assertEquals(MdocTransport.State.CLOSED, transport.state.value)
    }

    @Test
    fun peripheralMdocCancelOpenClosesPeripheralManager() = runTest {
        val manager = FakeBlePeripheralManager().apply {
            powerOnDeferred.complete(Unit)
            advertiseServiceDeferred.complete(Unit)
        }
        val transport = BleTransportPeripheralMdoc(
            role = MdocRole.MDOC,
            options = MdocTransportOptions(),
            peripheralManager = manager,
            uuid = testUuid,
        )

        val openDeferred = async { transport.open(senderKey) }
        runCurrent()
        assertEquals(MdocTransport.State.ADVERTISING, transport.state.value)
        assertFalse(openDeferred.isCompleted)
        assertEquals(0, manager.closeCalls)

        openDeferred.cancel()
        runCurrent()

        assertTrue(openDeferred.isCancelled)
        assertTrue(manager.closeCalls > 0)
        assertEquals(MdocTransport.State.CLOSED, transport.state.value)
    }

    @Test
    fun peripheralMdocCloseCancelsAndAwaitsInFlightOpen() = runTest {
        val manager = FakeBlePeripheralManager().apply {
            powerOnDeferred.complete(Unit)
            advertiseServiceDeferred.complete(Unit)
        }
        val transport = BleTransportPeripheralMdoc(
            role = MdocRole.MDOC,
            options = MdocTransportOptions(),
            peripheralManager = manager,
            uuid = testUuid,
        )

        val openDeferred = async { transport.open(senderKey) }
        runCurrent()
        assertEquals(MdocTransport.State.ADVERTISING, transport.state.value)

        transport.close()
        runCurrent()

        assertTrue(openDeferred.isCancelled)
        assertTrue(manager.closeCalls > 0)
        assertEquals(MdocTransport.State.CLOSED, transport.state.value)
    }

    @Test
    fun centralMdocCancelOpenClosesCentralManager() = runTest {
        val manager = FakeBleCentralManager().apply {
            powerOnDeferred.complete(Unit)
        }
        val transport = BleTransportCentralMdoc(
            role = MdocRole.MDOC,
            options = MdocTransportOptions(),
            centralManager = manager,
            uuid = testUuid,
            psm = null,
        )

        val openDeferred = async { transport.open(senderKey) }
        runCurrent()
        assertEquals(MdocTransport.State.SCANNING, transport.state.value)
        assertFalse(openDeferred.isCompleted)
        assertEquals(0, manager.closeCalls)

        openDeferred.cancel()
        runCurrent()

        assertTrue(openDeferred.isCancelled)
        assertTrue(manager.closeCalls > 0)
        assertEquals(MdocTransport.State.CLOSED, transport.state.value)
    }

    @Test
    fun centralMdocReaderCancelOpenClosesPeripheralManager() = runTest {
        val manager = FakeBlePeripheralManager().apply {
            powerOnDeferred.complete(Unit)
            advertiseServiceDeferred.complete(Unit)
        }
        val transport = BleTransportCentralMdocReader(
            role = MdocRole.MDOC_READER,
            options = MdocTransportOptions(),
            peripheralManager = manager,
            uuid = testUuid,
        )

        val openDeferred = async { transport.open(senderKey) }
        runCurrent()
        assertEquals(MdocTransport.State.ADVERTISING, transport.state.value)
        assertFalse(openDeferred.isCompleted)
        assertEquals(0, manager.closeCalls)

        openDeferred.cancel()
        runCurrent()

        assertTrue(openDeferred.isCancelled)
        assertTrue(manager.closeCalls > 0)
        assertEquals(MdocTransport.State.CLOSED, transport.state.value)
    }

    @Test
    fun peripheralMdocReaderCancelOpenClosesCentralManager() = runTest {
        val manager = FakeBleCentralManager().apply {
            powerOnDeferred.complete(Unit)
        }
        val transport = BleTransportPeripheralMdocReader(
            role = MdocRole.MDOC_READER,
            options = MdocTransportOptions(),
            centralManager = manager,
            uuid = testUuid,
            psm = null,
        )

        val openDeferred = async { transport.open(senderKey) }
        runCurrent()
        assertEquals(MdocTransport.State.SCANNING, transport.state.value)
        assertFalse(openDeferred.isCompleted)
        assertEquals(0, manager.closeCalls)

        openDeferred.cancel()
        runCurrent()

        assertTrue(openDeferred.isCancelled)
        assertTrue(manager.closeCalls > 0)
        assertEquals(MdocTransport.State.CLOSED, transport.state.value)
    }

    private class FakeBlePeripheralManager : BlePeripheralManager {
        override val incomingMessages = Channel<ByteArray>(Channel.UNLIMITED)
        override val l2capPsm: Int? = null
        override val usingL2cap: Boolean = false

        val powerOnDeferred = CompletableDeferred<Unit>()
        val advertiseServiceDeferred = CompletableDeferred<Unit>()
        val waitForClientDeferred = CompletableDeferred<Unit>()
        var closeCalls = 0

        override fun setUuids(
            stateCharacteristicUuid: UUID,
            client2ServerCharacteristicUuid: UUID,
            server2ClientCharacteristicUuid: UUID,
            identCharacteristicUuid: UUID?,
            l2capCharacteristicUuid: UUID?,
            startL2capServer: Boolean
        ) {}

        override fun setCallbacks(
            onError: (error: Throwable) -> Unit,
            onClosed: () -> Unit
        ) {}

        override suspend fun waitForPowerOn() {
            powerOnDeferred.await()
        }

        override suspend fun advertiseService(uuid: UUID) {
            advertiseServiceDeferred.await()
        }

        override suspend fun setESenderKey(eSenderKey: EcPublicKey) {}

        override suspend fun waitForStateCharacteristicWriteOrL2CAPClient() {
            waitForClientDeferred.await()
        }

        override suspend fun writeToStateCharacteristic(value: Int) {}

        override suspend fun sendMessage(message: ByteArray) {}

        override fun close() {
            closeCalls += 1
            incomingMessages.close()
        }
    }

    private class FakeBleCentralManager : BleCentralManager {
        override val incomingMessages = Channel<ByteArray>(Channel.UNLIMITED)
        override val l2capPsm: Int? = null
        override val usingL2cap: Boolean = false

        val powerOnDeferred = CompletableDeferred<Unit>()
        val waitForPeripheralDeferred = CompletableDeferred<Unit>()
        var closeCalls = 0

        override fun setUuids(
            stateCharacteristicUuid: UUID,
            client2ServerCharacteristicUuid: UUID,
            server2ClientCharacteristicUuid: UUID,
            identCharacteristicUuid: UUID?,
            l2capUuid: UUID?
        ) {}

        override fun setCallbacks(
            onError: (error: Throwable) -> Unit,
            onClosed: () -> Unit
        ) {}

        override suspend fun waitForPowerOn() {
            powerOnDeferred.await()
        }

        override suspend fun waitForPeripheralWithUuid(uuid: UUID) {
            waitForPeripheralDeferred.await()
        }

        override suspend fun connectToPeripheral() {}
        override suspend fun requestMtu() {}
        override suspend fun peripheralDiscoverServices(uuid: UUID) {}
        override suspend fun peripheralDiscoverCharacteristics() {}
        override suspend fun checkReaderIdentMatches(eSenderKey: EcPublicKey) {}
        override suspend fun subscribeToCharacteristics() {}
        override suspend fun writeToStateCharacteristic(value: Int) {}
        override suspend fun sendMessage(message: ByteArray) {}
        override suspend fun connectL2cap(psm: Int) {}

        override fun close() {
            closeCalls += 1
            incomingMessages.close()
        }
    }
}
