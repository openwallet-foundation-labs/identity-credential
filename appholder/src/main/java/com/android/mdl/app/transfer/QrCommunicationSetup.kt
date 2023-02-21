package com.android.mdl.app.transfer

import android.content.Context
import com.android.identity.DataTransport
import com.android.identity.DeviceRetrievalHelper
import com.android.identity.PresentationSession
import com.android.identity.QrEngagementHelper
import com.android.mdl.app.util.log
import com.android.mdl.app.util.mainExecutor

class QrCommunicationSetup(
    private val context: Context,
    private val onConnecting: () -> Unit,
    private val onQrEngagementReady: () -> Unit,
    private val onDeviceRetrievalHelperReady: (session: PresentationSession, deviceRetrievalHelper: DeviceRetrievalHelper) -> Unit,
    private val onNewDeviceRequest: (request: ByteArray) -> Unit,
    private val onSendResponseApdu: (responseApdu: ByteArray) -> Unit,
    private val onDisconnected: (transportSpecificTermination: Boolean) -> Unit,
    private val onCommunicationError: (error: Throwable) -> Unit,
) {

    private val session = SessionSetup(CredentialStore(context)).createSession()
    private val connectionSetup = ConnectionSetup(context)

    private val qrEngagementListener = object : QrEngagementHelper.Listener {

        override fun onDeviceEngagementReady() {
            log("QR Engagement: Device Engagement Ready")
            onQrEngagementReady()
        }

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
                context,
                deviceRetrievalHelperListener,
                context.mainExecutor(),
                session
            )
            builder.useForwardEngagement(
                transport,
                qrEngagement.deviceEngagement,
                qrEngagement.handover
            )
            deviceRetrievalHelper = builder.build()
            deviceRetrievalHelper?.setSendSessionTerminationMessage(true)
            qrEngagement.close()
            onDeviceRetrievalHelperReady(session, deviceRetrievalHelper!!)
        }

        override fun onError(error: Throwable) {
            log("QR onError: ${error.message}")
            onCommunicationError(error)
        }
    }

    private val deviceRetrievalHelperListener = object : DeviceRetrievalHelper.Listener {

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

    private lateinit var qrEngagement: QrEngagementHelper
    private var deviceRetrievalHelper: DeviceRetrievalHelper? = null

    val deviceEngagementUriEncoded: String
        get() = qrEngagement.deviceEngagementUriEncoded

    fun configure() {
        qrEngagement = QrEngagementHelper(
            context,
            session,
            connectionSetup.getConnectionMethods(),
            connectionSetup.getConnectionOptions(),
            qrEngagementListener,
            context.mainExecutor()
        )
    }

    fun close() {
        try {
            qrEngagement.close()
        } catch (exception: RuntimeException) {
            log("Error closing QR engagement", exception)
        }
    }
}