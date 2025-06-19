package org.multipaz.testapp

import android.content.Intent
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.ContextCompat
import org.multipaz.cbor.DataItem
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodBle
import org.multipaz.mdoc.nfc.MdocNfcEngagementHelper
import org.multipaz.models.presentment.MdocPresentmentMechanism
import org.multipaz.mdoc.transport.MdocTransportFactory
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.nfc.CommandApdu
import org.multipaz.models.presentment.PresentmentModel
import org.multipaz.models.presentment.PresentmentTimeout
import org.multipaz.context.initializeApplication
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodNfc
import org.multipaz.prompt.AndroidPromptModel
import org.multipaz.util.Logger
import org.multipaz.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteString
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.mdoc.transport.advertise
import org.multipaz.mdoc.transport.waitForConnection
import org.multipaz.nfc.ResponseApdu
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class NdefService: HostApduService() {
    companion object {
        private val TAG = "NdefService"

        private var engagement: MdocNfcEngagementHelper? = null
        private var disableEngagementJob: Job? = null
        private var listenForCancellationFromUiJob: Job? = null
        val promptModel = AndroidPromptModel()
        val presentmentModel: PresentmentModel by lazy {
            PresentmentModel().apply { setPromptModel(promptModel) }
        }
    }

    private fun vibrate(pattern: List<Int>) {
        val vibrator = ContextCompat.getSystemService(applicationContext, Vibrator::class.java)
        val vibrationEffect = VibrationEffect.createWaveform(pattern.map { it.toLong() }.toLongArray(), -1)
        vibrator?.vibrate(vibrationEffect)
    }

    private fun vibrateError() {
        vibrate(listOf(0, 500))
    }

    private fun vibrateSuccess() {
        vibrate(listOf(0, 100, 50, 100))
    }

    override fun onDestroy() {
        Logger.i(TAG, "onDestroy")
        super.onDestroy()
        commandApduListenJob?.cancel()
    }

    private lateinit var settingsModel: TestAppSettingsModel

    private var commandApduListenJob: Job? = null
    private val commandApduChannel = Channel<CommandApdu>(Channel.UNLIMITED)

    override fun onCreate() {
        Logger.i(TAG, "onCreate")
        super.onCreate()

        initializeApplication(applicationContext)

        // Essentially, start a coroutine on an I/O thread for handling incoming APDUs
        commandApduListenJob = CoroutineScope(Dispatchers.IO).launch {
            // Note: Every millisecond literally counts here because we're handling a
            // NFC tap and users tend to remove their phone from the reader really fast.
            //
            // So we don't really have time to call App.getInstance() which will initialize
            // all the dependencies, like the DocumentStore, trusts lists, and so on. We
            // just initialize the absolute minimum amount of things to get a NFC engagement
            // done with for now and defer the work in App.getInstance() until
            // CredmanPresentmentActivity is started in earnest.
            //
            // Since this is samples/testapp we do load the settings so it's possible to
            // experiment with various settings, e.g. whether to use static or negotiated
            // handover. Even loading settings slow and can take between 10-100ms.
            // Production-apps will want to just hardcode their settings here and
            // avoid this extra delay.
            //
            val t0 = Clock.System.now()
            settingsModel = TestAppSettingsModel.create(
                storage = platformStorage(),
                readOnly = true
            )
            platformCryptoInit(settingsModel)
            val t1 = Clock.System.now()
            Logger.i(TAG, "Settings loaded in ${(t1 - t0).inWholeMilliseconds} ms")

            while (true) {
                val commandApdu = commandApduChannel.receive()
                val responseApdu = processCommandApdu(commandApdu)
                if (responseApdu != null) {
                    sendResponseApdu(responseApdu.encode())
                }
            }
        }
    }

    private var started = false

    private fun startEngagement() {
        Logger.i(TAG, "startEngagement")

        disableEngagementJob?.cancel()
        disableEngagementJob = null
        listenForCancellationFromUiJob?.cancel()
        listenForCancellationFromUiJob = null

        val eDeviceKey = Crypto.createEcPrivateKey(settingsModel.presentmentSessionEncryptionCurve.value)
        val timeStarted = Clock.System.now()

        presentmentModel.reset()
        presentmentModel.setConnecting()

        // The UI consuming [PresentationModel] - for example the [Presentment] composable in this library - may
        // have a cancel button which will trigger COMPLETED state when pressed. Need to listen for that.
        //
        listenForCancellationFromUiJob = presentmentModel.presentmentScope.launch {
            presentmentModel.state
                .collect { state ->
                    if (state == PresentmentModel.State.COMPLETED) {
                        disableEngagementJob?.cancel()
                        disableEngagementJob = null
                    }
                }
        }

        val intent = Intent(applicationContext, NfcPresentmentActivity::class.java)
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
        )
        applicationContext.startActivity(intent)

        fun negotiatedHandoverPicker(connectionMethods: List<MdocConnectionMethod>): MdocConnectionMethod {
            Logger.i(TAG, "Negotiated Handover available methods: $connectionMethods")
            for (prefix in settingsModel.presentmentNegotiatedHandoverPreferredOrder.value) {
                for (connectionMethod in connectionMethods) {
                    if (connectionMethod.toString().startsWith(prefix)) {
                        Logger.i(TAG, "Using method $connectionMethod")
                        return connectionMethod
                    }
                }
            }
            Logger.i(TAG, "Using method ${connectionMethods.first()}")
            return connectionMethods.first()
        }

        val negotiatedHandoverPicker: ((connectionMethods: List<MdocConnectionMethod>) -> MdocConnectionMethod)? =
            if (settingsModel.presentmentUseNegotiatedHandover.value) {
                { connectionMethods -> negotiatedHandoverPicker(connectionMethods) }
            } else {
                null
            }

        var staticHandoverConnectionMethods: List<MdocConnectionMethod>? = null
        if (!settingsModel.presentmentUseNegotiatedHandover.value) {
            staticHandoverConnectionMethods = mutableListOf<MdocConnectionMethod>()
            val bleUuid = UUID.randomUUID()
            if (settingsModel.presentmentBleCentralClientModeEnabled.value) {
                staticHandoverConnectionMethods.add(
                    MdocConnectionMethodBle(
                        supportsPeripheralServerMode = false,
                        supportsCentralClientMode = true,
                        peripheralServerModeUuid = null,
                        centralClientModeUuid = bleUuid,
                    )
                )
            }
            if (settingsModel.presentmentBlePeripheralServerModeEnabled.value) {
                staticHandoverConnectionMethods.add(
                    MdocConnectionMethodBle(
                        supportsPeripheralServerMode = true,
                        supportsCentralClientMode = false,
                        peripheralServerModeUuid = bleUuid,
                        centralClientModeUuid = null,
                    )
                )
            }
            if (settingsModel.presentmentNfcDataTransferEnabled.value) {
                staticHandoverConnectionMethods.add(
                    MdocConnectionMethodNfc(
                        commandDataFieldMaxLength = 0xffff,
                        responseDataFieldMaxLength = 0x10000
                    )
                )
            }
        }

        // TODO: Listen on methods _before_ starting the engagement helper so we can send the PSM
        //   for mdoc Peripheral Server mode when using NFC Static Handover.
        //
        engagement = MdocNfcEngagementHelper(
            eDeviceKey = eDeviceKey.publicKey,
            onHandoverComplete = { connectionMethods, encodedDeviceEngagement, handover ->
                vibrateSuccess()
                val duration = Clock.System.now() - timeStarted
                listenOnMethods(
                    connectionMethods = connectionMethods,
                    settingsModel = settingsModel,
                    encodedDeviceEngagement = encodedDeviceEngagement,
                    handover = handover,
                    eDeviceKey = eDeviceKey,
                    engagementDuration = duration
                )
            },
            onError = { error ->
                Logger.w(TAG, "Engagement failed", error)
                error.printStackTrace()
                vibrateError()
                engagement = null
            },
            staticHandoverMethods = staticHandoverConnectionMethods,
            negotiatedHandoverPicker = negotiatedHandoverPicker
        )
    }

    private fun listenOnMethods(
        connectionMethods: List<MdocConnectionMethod>,
        settingsModel: TestAppSettingsModel,
        encodedDeviceEngagement: ByteString,
        handover: DataItem,
        eDeviceKey: EcPrivateKey,
        engagementDuration: Duration,
    ) {
        presentmentModel.presentmentScope.launch {
            val transports = connectionMethods.advertise(
                role = MdocRole.MDOC,
                transportFactory = MdocTransportFactory.Default,
                options = MdocTransportOptions(
                    bleUseL2CAP = settingsModel.readerBleL2CapEnabled.value
                ),
            )
            val transport = transports.waitForConnection(
                eSenderKey = eDeviceKey.publicKey,
            )
            presentmentModel.setMechanism(
                MdocPresentmentMechanism(
                    transport = transport,
                    eDeviceKey = eDeviceKey,
                    encodedDeviceEngagement = encodedDeviceEngagement,
                    handover = handover,
                    engagementDuration = engagementDuration,
                    allowMultipleRequests = settingsModel.presentmentAllowMultipleRequests.value
                )
            )
            disableEngagementJob?.cancel()
            disableEngagementJob = null
            listenForCancellationFromUiJob?.cancel()
            listenForCancellationFromUiJob = null
        }
    }

    // Called by coroutine running in I/O thread, see onCreate() for details
    private suspend fun processCommandApdu(commandApdu: CommandApdu): ResponseApdu? {
        if (!started) {
            started = true
            startEngagement()
        }

        try {
            engagement?.let {
                val responseApdu = it.processApdu(commandApdu)
                return responseApdu
            }
        } catch (e: Throwable) {
            Logger.e(TAG, "Error processing APDU in MdocNfcEngagementHandler", e)
            e.printStackTrace()
        }
        return null
    }

    // Called by OS when an APDU arrives
    override fun processCommandApdu(encodedCommandApdu: ByteArray, extras: Bundle?): ByteArray? {
        // Bounce the APDU to processCommandApdu() above via the coroutine in I/O thread set up in onCreate()
        commandApduChannel.trySend(CommandApdu.decode(encodedCommandApdu))
        return null
    }

    override fun onDeactivated(reason: Int) {
        Logger.i(TAG, "onDeactivated: reason=$reason")
        started = false
        // If the reader hasn't connected by the time NFC interaction ends, make sure we only
        // wait for a limited amount of time.
        if (presentmentModel.state.value == PresentmentModel.State.CONNECTING) {
            val timeout = 15.seconds
            Logger.i(TAG, "Reader hasn't connected at NFC deactivation time, scheduling $timeout timeout for closing")
            disableEngagementJob = CoroutineScope(Dispatchers.IO).launch {
                delay(timeout)
                if (presentmentModel.state.value == PresentmentModel.State.CONNECTING) {
                    presentmentModel.setCompleted(PresentmentTimeout("Reader didn't connect inside $timeout, closing"))
                }
                engagement = null
                disableEngagementJob = null
            }
        }
    }
}