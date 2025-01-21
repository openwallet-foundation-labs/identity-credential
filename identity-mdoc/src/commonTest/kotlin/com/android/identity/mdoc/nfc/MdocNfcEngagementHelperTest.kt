package com.android.identity.mdoc.nfc

import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.EcPublicKeyDoubleCoordinate
import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import com.android.identity.mdoc.connectionmethod.ConnectionMethodBle
import com.android.identity.mdoc.connectionmethod.ConnectionMethodNfc
import com.android.identity.nfc.CommandApdu
import com.android.identity.nfc.Nfc
import com.android.identity.nfc.NfcCommandFailedException
import com.android.identity.nfc.NfcIsoTag
import com.android.identity.nfc.ResponseApdu
import com.android.identity.util.UUID
import com.android.identity.util.fromHex
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * This test is mainly for verifying the correct functionality of MdocNfcEngagementHelper (the wallet side)
 * but it also exercises and verifies mdocReaderNfcHandover() and other things on the reader side.
 */
class MdocNfcEngagementHelperTest {

    // Provides access to MdocNfcEngagementHelper via NfcIsoTag abstraction
    class LoopbackIsoTag(val engagementHelper: MdocNfcEngagementHelper): NfcIsoTag() {
        val transcript = StringBuilder()

        override val maxTransceiveLength: Int
            get() = 65536

        override suspend fun transceive(command: CommandApdu): ResponseApdu {
            transcript.appendLine("${command}")
            val response = engagementHelper.processApdu(command)
            transcript.appendLine("${response}")
            return response
        }
    }

    private fun getConnectionMethods(): List<ConnectionMethod> {
        // Include all ConnectionMethods that can exist in OOB data. Use static identifiers
        // to ensure we get the same transcripts every time.
        val bleUuid = UUID.fromString("b3d52ac4-a1b6-4b51-a22e-78ee55ef6eb6")
        return listOf(
            ConnectionMethodBle(
                supportsPeripheralServerMode = false,
                supportsCentralClientMode = true,
                peripheralServerModeUuid = null,
                centralClientModeUuid = bleUuid
            ),
            ConnectionMethodBle(
                supportsPeripheralServerMode = true,
                supportsCentralClientMode = false,
                peripheralServerModeUuid = bleUuid,
                centralClientModeUuid = null
            ),
            ConnectionMethodNfc(
                commandDataFieldMaxLength = 0xffff,
                responseDataFieldMaxLength = 0x10000
            )
        )
    }

    private fun getEDeviceKeyPub(): EcPublicKey {
        // Use static key to ensure we get the same transcripts every time.
        return EcPublicKeyDoubleCoordinate(
            curve = EcCurve.P256,
            x = "7104f7e2c2e95ca76482c0c963d454b7e5d053c5b59ce89d00ff7c7d7ab6ff7d".fromHex(),
            y = "f442821292c2453ec67c75233ea56e1734c211ae26b259fdf232b5b3d82b1ba2".fromHex()
        )
    }

    @Test
    fun testStaticHandover() = runTest {
        val staticHandoverConnectionMethods = getConnectionMethods()

        val eDeviceKeyPub = getEDeviceKeyPub()
        val engagementHelper = MdocNfcEngagementHelper(
            eDeviceKey = eDeviceKeyPub,
            onHandoverComplete = { connectionMethods, encodedDeviceEngagement, handover ->
                assertEquals(staticHandoverConnectionMethods, connectionMethods)
            },
            onError = { error ->
                fail("onError should not be called with $error")
            },
            staticHandoverMethods = staticHandoverConnectionMethods,
        )

        val tag = LoopbackIsoTag(engagementHelper)
        val handoverResult = mdocReaderNfcHandover(
            tag = tag,
            negotiatedHandoverConnectionMethods = getConnectionMethods(),
        )
        assertNotNull(handoverResult)
        assertEquals(staticHandoverConnectionMethods, handoverResult.connectionMethods)

        assertEquals(
            """
                CommandApdu(cla=0, ins=164, p1=4, p2=0, payload=ByteString(size=7 hex=d2760000850101), le=0)
                ResponseApdu(status=36864, payload=ByteString(size=0))
                CommandApdu(cla=0, ins=164, p1=0, p2=12, payload=ByteString(size=2 hex=e103), le=0)
                ResponseApdu(status=36864, payload=ByteString(size=0))
                CommandApdu(cla=0, ins=176, p1=0, p2=0, payload=ByteString(size=0), le=15)
                ResponseApdu(status=36864, payload=ByteString(size=15 hex=000f207fff7fff0406e1047fff00ff))
                CommandApdu(cla=0, ins=164, p1=0, p2=12, payload=ByteString(size=2 hex=e104), le=0)
                ResponseApdu(status=36864, payload=ByteString(size=0))
                CommandApdu(cla=0, ins=176, p1=0, p2=0, payload=ByteString(size=0), le=2)
                ResponseApdu(status=36864, payload=ByteString(size=2 hex=0146))
                CommandApdu(cla=0, ins=176, p1=0, p2=2, payload=ByteString(size=0), le=326)
                ResponseApdu(status=36864, payload=ByteString(size=326 hex=91022d487315910209616301013001046d646f63110209616301013001046d646f6351020b616301036e666301046d646f631c1e580469736f2e6f72673a31383031333a646576696365656e676167656d656e746d646f63a20063312e30018201d818584ba4010220012158207104f7e2c2e95ca76482c0c963d454b7e5d053c5b59ce89d00ff7c7d7ab6ff7d225820f442821292c2453ec67c75233ea56e1734c211ae26b259fdf232b5b3d82b1ba21a2015016170706c69636174696f6e2f766e642e626c7565746f6f74682e6c652e6f6f6230021c011107b66eef55ee782ea2514bb6a1c42ad5b31a2015016170706c69636174696f6e2f766e642e626c7565746f6f74682e6c652e6f6f6230021c001107b66eef55ee782ea2514bb6a1c42ad5b35c110a0369736f2e6f72673a31383031333a6e66636e6663010301ffff0402010000))
            """.trimIndent().trim(),
            tag.transcript.toString().trim()
        )
    }

    @Test
    fun testNegotiatedHandover() = runTest {
        val negotiatedHandoverConnectionMethods = getConnectionMethods()

        val eDeviceKeyPub = getEDeviceKeyPub()
        val engagementHelper = MdocNfcEngagementHelper(
            eDeviceKey = eDeviceKeyPub,
            onHandoverComplete = { connectionMethods, encodedDeviceEngagement, handover ->
                assertEquals(1, connectionMethods.size)
            },
            onError = { error ->
                fail("onError should not be called with $error")
            },
            negotiatedHandoverPicker = { methods -> methods.first() }
        )

        val tag = LoopbackIsoTag(engagementHelper)
        val handoverResult = mdocReaderNfcHandover(
            tag = tag,
            negotiatedHandoverConnectionMethods = negotiatedHandoverConnectionMethods,
        )
        assertNotNull(handoverResult)
        assertEquals(1, handoverResult.connectionMethods.size)
        assertEquals(
            negotiatedHandoverConnectionMethods.first(),
            handoverResult.connectionMethods.first()
        )

        assertEquals(
            """
                CommandApdu(cla=0, ins=164, p1=4, p2=0, payload=ByteString(size=7 hex=d2760000850101), le=0)
                ResponseApdu(status=36864, payload=ByteString(size=0))
                CommandApdu(cla=0, ins=164, p1=0, p2=12, payload=ByteString(size=2 hex=e103), le=0)
                ResponseApdu(status=36864, payload=ByteString(size=0))
                CommandApdu(cla=0, ins=176, p1=0, p2=0, payload=ByteString(size=0), le=15)
                ResponseApdu(status=36864, payload=ByteString(size=15 hex=000f207fff7fff0406e1047fff0000))
                CommandApdu(cla=0, ins=164, p1=0, p2=12, payload=ByteString(size=2 hex=e104), le=0)
                ResponseApdu(status=36864, payload=ByteString(size=0))
                CommandApdu(cla=0, ins=176, p1=0, p2=0, payload=ByteString(size=0), le=2)
                ResponseApdu(status=36864, payload=ByteString(size=2 hex=001f))
                CommandApdu(cla=0, ins=176, p1=0, p2=2, payload=ByteString(size=0), le=31)
                ResponseApdu(status=36864, payload=ByteString(size=31 hex=d1021a5470101375726e3a6e66633a736e3a68616e646f76657200000fffff))
                CommandApdu(cla=0, ins=214, p1=0, p2=0, payload=ByteString(size=27 hex=0019d1021454731375726e3a6e66633a736e3a68616e646f766572), le=0)
                ResponseApdu(status=36864, payload=ByteString(size=0))
                CommandApdu(cla=0, ins=176, p1=0, p2=0, payload=ByteString(size=0), le=2)
                ResponseApdu(status=36864, payload=ByteString(size=2 hex=0006))
                CommandApdu(cla=0, ins=176, p1=0, p2=2, payload=ByteString(size=0), le=6)
                ResponseApdu(status=36864, payload=ByteString(size=6 hex=d10201546500))
                CommandApdu(cla=0, ins=214, p1=0, p2=0, payload=ByteString(size=237 hex=00eb91021e487215910204616301013000110204616301013000510206616301036e6663001c1e060a69736f2e6f72673a31383031333a726561646572656e676167656d656e746d646f63726561646572a10063312e301a2015016170706c69636174696f6e2f766e642e626c7565746f6f74682e6c652e6f6f6230021c001107b66eef55ee782ea2514bb6a1c42ad5b31a2015016170706c69636174696f6e2f766e642e626c7565746f6f74682e6c652e6f6f6230021c011107b66eef55ee782ea2514bb6a1c42ad5b35c110a0369736f2e6f72673a31383031333a6e66636e6663010301ffff0402010000), le=0)
                ResponseApdu(status=36864, payload=ByteString(size=0))
                CommandApdu(cla=0, ins=176, p1=0, p2=0, payload=ByteString(size=0), le=2)
                ResponseApdu(status=36864, payload=ByteString(size=2 hex=00ba))
                CommandApdu(cla=0, ins=176, p1=0, p2=2, payload=ByteString(size=0), le=186)
                ResponseApdu(status=36864, payload=ByteString(size=186 hex=91020f487315d10209616301013001046d646f631c1e580469736f2e6f72673a31383031333a646576696365656e676167656d656e746d646f63a20063312e30018201d818584ba4010220012158207104f7e2c2e95ca76482c0c963d454b7e5d053c5b59ce89d00ff7c7d7ab6ff7d225820f442821292c2453ec67c75233ea56e1734c211ae26b259fdf232b5b3d82b1ba25a2003016170706c69636174696f6e2f766e642e626c7565746f6f74682e6c652e6f6f6230021c01))
            """.trimIndent().trim(),
            tag.transcript.toString().trim()
        )
    }

    private fun testNfcEngagementHelper(
        testBlock: suspend (tag: NfcIsoTag) -> Unit
    ): Pair<Boolean, Throwable?> {
        var handoverSuccess = false
        var handoverError: Throwable? = null
        val helper = MdocNfcEngagementHelper(
            eDeviceKey = Crypto.createEcPrivateKey(EcCurve.P256).publicKey,
            onHandoverComplete = { connectionMethods, encodedDeviceEngagement, handover -> handoverSuccess = true },
            onError = { error -> handoverError = error },
            staticHandoverMethods = getConnectionMethods(),
        )
        runBlocking { testBlock(LoopbackIsoTag(helper)) }
        return Pair(handoverSuccess, handoverError)
    }

    @Test
    fun testWrongApplicationIdSelected() {
        var (handoverSuccess, handoverError) = testNfcEngagementHelper { tag ->
            // Off by one from the NDEF AID
            assertFailsWith(NfcCommandFailedException::class) {
                tag.selectApplication(ByteString("D2760000850102".fromHex()))
            }
        }
        assertFalse(handoverSuccess)
        assertNotNull(handoverError)
        assertEquals(
            "SelectApplication: Expected NDEF AID but got d2760000850102",
            handoverError.message
        )
    }

    @Test
    fun testFiledIdBeforeApplicationSelectSelected() {
        var (handoverSuccess, handoverError) = testNfcEngagementHelper { tag ->
            // Fails because application is not yet selected
            assertFailsWith(NfcCommandFailedException::class) {
                tag.selectFile(Nfc.NDEF_CAPABILITY_CONTAINER_FILE_ID)
            }
        }
        assertFalse(handoverSuccess)
        assertNotNull(handoverError)
        assertEquals(
            "Error processing APDU: NDEF application not yet selected",
            handoverError.message
        )
    }

    @Test
    fun testUnexpectedFileIdSelected() {
        var (handoverSuccess, handoverError) = testNfcEngagementHelper { tag ->
            tag.selectApplication(Nfc.NDEF_APPLICATION_ID)
            // Fails because the FileID is unknown
            assertFailsWith(NfcCommandFailedException::class) {
                tag.selectFile(42)
            }
        }
        assertFalse(handoverSuccess)
        assertNotNull(handoverError)
        assertEquals(
            "SelectFile: Unexpected File ID 42",
            handoverError.message
        )
    }

    // TODO: add more tests to exercise implementation of READ_BINARY and UPDATE_BINARY

    @Test
    fun testConstructor() {
        assertFailsWith(
            IllegalStateException::class,
            "Must use either static or negotiated handover, none are selected"
        ) {
            MdocNfcEngagementHelper(
                eDeviceKey = Crypto.createEcPrivateKey(EcCurve.P256).publicKey,
                onHandoverComplete = { connectionMethods, encodedDeviceEngagement, handover -> },
                onError = { error -> }
            )
        }

        assertFailsWith(
            IllegalStateException::class,
            "Can't use both static and negotiated handover at the same time"
        ) {
            MdocNfcEngagementHelper(
                eDeviceKey = Crypto.createEcPrivateKey(EcCurve.P256).publicKey,
                onHandoverComplete = { connectionMethods, encodedDeviceEngagement, handover -> },
                onError = { error -> },
                staticHandoverMethods = getConnectionMethods(),
                negotiatedHandoverPicker = { methods -> methods.first() }
            )
        }


        assertFailsWith(
            IllegalStateException::class,
            "Must have at least one ConnectionMethod for static handover"
        ) {
            MdocNfcEngagementHelper(
                eDeviceKey = Crypto.createEcPrivateKey(EcCurve.P256).publicKey,
                onHandoverComplete = { connectionMethods, encodedDeviceEngagement, handover -> },
                onError = { error -> },
                staticHandoverMethods = listOf()
            )
        }

        MdocNfcEngagementHelper(
            eDeviceKey = Crypto.createEcPrivateKey(EcCurve.P256).publicKey,
            onHandoverComplete = { connectionMethods, encodedDeviceEngagement, handover -> },
            onError = { error -> },
            negotiatedHandoverPicker = { methods -> methods.first() }
        )

        MdocNfcEngagementHelper(
            eDeviceKey = Crypto.createEcPrivateKey(EcCurve.P256).publicKey,
            onHandoverComplete = { connectionMethods, encodedDeviceEngagement, handover -> },
            onError = { error -> },
            staticHandoverMethods = getConnectionMethods()
        )
    }
}