package com.android.identity.wallet.transfer

import android.content.Context
import com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper
import com.android.identity.android.mdoc.engagement.QrEngagementHelper
import com.android.identity.android.mdoc.transport.DataTransport
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcPublicKey
import com.android.identity.wallet.util.PreferencesHelper
import com.android.identity.wallet.util.log
import com.android.identity.wallet.util.mainExecutor

class QrCommunicationSetup(
    private val context: Context,
    private val onConnecting: () -> Unit,
    private val onDeviceRetrievalHelperReady: (deviceRetrievalHelper: DeviceRetrievalHelper) -> Unit,
    private val onNewDeviceRequest: (request: ByteArray) -> Unit,
    private val onDisconnected: (transportSpecificTermination: Boolean) -> Unit,
    private val onCommunicationError: (error: Throwable) -> Unit,
) {

    private val settings = PreferencesHelper.apply { initialize(context) }
    private val connectionSetup = ConnectionSetup(context)
    private val eDeviceKey = Crypto.createEcPrivateKey(settings.getEphemeralKeyCurveOption())

    private var deviceRetrievalHelper: DeviceRetrievalHelper? = null
    private lateinit var qrEngagement: QrEngagementHelper

    val deviceEngagementUriEncoded: String
        get() = qrEngagement.deviceEngagementUriEncoded

    private val qrEngagementListener = object : QrEngagementHelper.Listener {

        override fun onDeviceConnecting() {
            log("QR Engagement: Device Connecting")
            onConnecting()
        }

        override fun onDeviceConnected(transport: DataTransport) {
            if (deviceRetrievalHelper != null) {
                log("OnDeviceConnected for QR engagement -> ignoring due to active presentation")
                return
            }
            log("OnDeviceConnected via QR: qrEngagement=$qrEngagement")
            val builder = DeviceRetrievalHelper.Builder(
                context = context,
                listener = deviceRetrievalHelperListener,
                executor = context.mainExecutor(),
                eDeviceKey = eDeviceKey
            )
            deviceRetrievalHelper =
                builder
                    .useForwardEngagement(
                        transport = transport,
                        deviceEngagement = qrEngagement.deviceEngagement,
                        handover = qrEngagement.handover
                    )
                    .build()

            qrEngagement.close()
            onDeviceRetrievalHelperReady(requireNotNull(deviceRetrievalHelper))
        }

        override fun onError(error: Throwable) {
            log("QR onError: ${error.message}")
            onCommunicationError(error)
        }
    }

    private val deviceRetrievalHelperListener = object : DeviceRetrievalHelper.Listener {
        override fun onEReaderKeyReceived(eReaderKey: EcPublicKey) {
            log("DeviceRetrievalHelper Listener (QR): OnEReaderKeyReceived")
        }

        override fun onDeviceRequest(deviceRequestBytes: ByteArray) {
            log("DeviceRetrievalHelper Listener (QR): OnDeviceRequest")
            onNewDeviceRequest(deviceRequestBytes)
        }

        override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {
            log("DeviceRetrievalHelper Listener (QR): onDeviceDisconnected")
            onDisconnected(transportSpecificTermination)
        }

        override fun onError(error: Throwable) {
            log("DeviceRetrievalHelper Listener (QR): onError -> ${error.message}")
            onCommunicationError(error)
        }
    }

    fun configure() {
        qrEngagement = QrEngagementHelper.Builder(
            context = context,
            eDeviceKey = eDeviceKey.publicKey,
            options = connectionSetup.getConnectionOptions(),
            listener = qrEngagementListener,
            executor = context.mainExecutor()
        ).setConnectionMethods(connectionSetup.getConnectionMethods())
            .build()
    }

    fun close() =
        try {
            qrEngagement.close()
        } catch (exception: RuntimeException) {
            log("Error closing QR engagement", exception)
        }
}