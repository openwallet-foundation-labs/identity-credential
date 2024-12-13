package com.android.identity.testapp

import android.content.Intent
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.os.Vibrator
import androidx.core.content.ContextCompat
import com.android.identity.cbor.DataItem
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import com.android.identity.mdoc.connectionmethod.ConnectionMethodBle
import com.android.identity.mdoc.nfc.MdocNfcEngagementHelper
import com.android.identity.testapp.presentation.MdocPresentationMechanism
import com.android.identity.mdoc.transport.MdocTransport
import com.android.identity.mdoc.transport.MdocTransportFactory
import com.android.identity.mdoc.transport.MdocTransportOptions
import com.android.identity.nfc.CommandApdu
import com.android.identity.testapp.presentation.PresentationModel
import com.android.identity.util.AndroidContexts
import com.android.identity.util.Logger
import com.android.identity.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// TODO: whether to use static or negotiated handover + other settings should be
//  configurable in IsoMdocProximitySharingScreen

class NdefService: HostApduService() {
    companion object {
        private val TAG = "NdefService"

        private var engagement: MdocNfcEngagementHelper? = null
        private var listeningTransports = mutableListOf<MdocTransport>()
        private var disableEngagementJob: Job? = null
    }

    private fun vibrate(pattern: List<Int>) {
        val vibrator = ContextCompat.getSystemService<Vibrator>(
            AndroidContexts.applicationContext,
            Vibrator::class.java
        )
        vibrator?.vibrate(pattern.map { it.toLong() }.toLongArray(), -1)
    }

    private fun vibrateError() {
        vibrate(listOf(0, 500))
    }

    private fun vibrateSuccess() {
        vibrate(listOf(0, 100, 50, 100))
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onCreate() {
        super.onCreate()
        MainActivity.initBouncyCastle()
    }

    private var started = false

    private fun startEngagement() {
        if (disableEngagementJob != null) {
            disableEngagementJob?.cancel()
            disableEngagementJob = null
        }
        closeListeningTransports()

        val eDeviceKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val timeStarted = Clock.System.now()

        val presentationModel = PresentationModel.getInstance()
        presentationModel.reset()
        presentationModel.setWaiting()

        val intent = Intent(applicationContext, NfcPresentationActivity::class.java)
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
        )
        applicationContext.startActivity(intent)

        val staticHandoverMethods = mutableListOf<ConnectionMethod>()
        staticHandoverMethods.add(
            ConnectionMethodBle(
                supportsPeripheralServerMode = false,
                supportsCentralClientMode = true,
                peripheralServerModeUuid = null,
                centralClientModeUuid = UUID.randomUUID()
            )
        )

        engagement = MdocNfcEngagementHelper(
            eDeviceKey = eDeviceKey.publicKey,
            onHandoverComplete = { connectionMethods, encodedDeviceEngagement, handover ->
                vibrateSuccess()
                val duration = Clock.System.now() - timeStarted
                listenOnMethods(connectionMethods, encodedDeviceEngagement, handover, eDeviceKey, duration)
            },
            onError = { error ->
                Logger.w(TAG, "Engagement failed", error)
                vibrateError()
                engagement = null
            },
            staticHandoverMethods = null,
            negotiatedHandoverPicker = { connectionMethods -> connectionMethods.first() }
        )
    }

    private fun listenOnMethods(
        connectionMethods: List<ConnectionMethod>,
        encodedDeviceEngagement: ByteString,
        handover: DataItem,
        eDeviceKey: EcPrivateKey,
        engagementDuration: Duration,
    ) {
        for (connectionMethod in connectionMethods) {
            val transport = MdocTransportFactory.Default.createTransport(
                connectionMethod,
                MdocTransport.Role.MDOC,
                MdocTransportOptions()
            )
            listeningTransports.add(transport)
            CoroutineScope(Dispatchers.IO).launch {
                transport.open(eDeviceKey.publicKey)
                methodConnected(transport, encodedDeviceEngagement, handover, eDeviceKey, engagementDuration)
            }
        }
    }

    private fun methodConnected(
        transport: MdocTransport,
        encodedDeviceEngagement: ByteString,
        handover: DataItem,
        eDeviceKey: EcPrivateKey,
        engagementDuration: Duration,
    ) {
        val presentationModel = PresentationModel.getInstance()
        presentationModel.setRunning(
            MdocPresentationMechanism(
                transport = transport,
                eDeviceKey = eDeviceKey,
                encodedDeviceEngagement = encodedDeviceEngagement,
                handover = handover,
                engagementDuration = engagementDuration
            )
        )
        listeningTransports.remove(transport)
        closeListeningTransports()

        if (disableEngagementJob != null) {
            disableEngagementJob?.cancel()
            disableEngagementJob = null
        }
    }

    private fun closeListeningTransports() {
        for (transport in listeningTransports) {
            try {
                runBlocking { transport.close() }
            } catch (e: Throwable) {
                Logger.e(TAG, "Exception caught closing listening transport", e)
            }
        }
        listeningTransports.clear()
    }

    override fun processCommandApdu(encodedCommandApdu: ByteArray, extras: Bundle?): ByteArray? {
        if (!started) {
            started = true
            startEngagement()
        }

        try {
            engagement?.let {
                val commandApdu = CommandApdu.decode(encodedCommandApdu)
                val responseApdu = runBlocking { it.processApdu(commandApdu) }
                return responseApdu.encode()
            }
        } catch (e: Throwable) {
            Logger.e(TAG, "processCommandApdu", e)
            e.printStackTrace()
        }
        return null
    }

    override fun onDeactivated(reason: Int) {
        Logger.i(TAG, "onDeactivated: reason=$reason")

        disableEngagementJob = CoroutineScope(Dispatchers.IO).launch {
            val timeout = 15.seconds
            delay(timeout)
            Logger.w(TAG, "Reader didn't connect inside $timeout, closing")
            closeListeningTransports()
            val presentationModel = PresentationModel.getInstance()
            if (presentationModel.state.value == PresentationModel.State.WAITING) {
                presentationModel.setCompleted(Error("Reader didn't connect inside $timeout"))
            }
            engagement = null
            disableEngagementJob = null
        }
    }
}