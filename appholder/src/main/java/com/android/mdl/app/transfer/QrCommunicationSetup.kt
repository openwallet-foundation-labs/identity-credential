package com.android.mdl.app.transfer

import android.content.Context
import com.android.identity.DataTransport
import com.android.identity.NfcApduRouter
import com.android.identity.PresentationHelper
import com.android.identity.PresentationSession
import com.android.identity.QrEngagementHelper
import com.android.mdl.app.util.log
import com.android.mdl.app.util.mainExecutor

class QrCommunicationSetup(
    private val context: Context,
    private val onConnecting: () -> Unit,
    private val onQrEngagementReady: () -> Unit,
    private val onPresentationReady: (session: PresentationSession, presentation: PresentationHelper) -> Unit,
    private val onNewDeviceRequest: (request: ByteArray) -> Unit,
    private val onSendResponseApdu: (responseApdu: ByteArray) -> Unit,
    private val onDisconnected: (transportSpecificTermination: Boolean) -> Unit,
    private val onCommunicationError: (error: Throwable) -> Unit,
) {

    private val session = SessionSetup(CredentialStore(context)).createSession()
    private val connectionSetup = ConnectionSetup(context)
    private val nfcApduRouter: NfcApduRouter = object : NfcApduRouter() {
        override fun sendResponseApdu(responseApdu: ByteArray) {
            onSendResponseApdu(responseApdu)
        }
    }

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
            if (presentation != null) {
                log("OnDeviceConnected for QR engagement -> ignoring due to active presentation")
                return
            }
            log("OnDeviceConnected via QR: qrEngagement=$qrEngagement")
            val builder = PresentationHelper.Builder(
                context,
                presentationListener,
                context.mainExecutor(),
                session
            )
            builder.useForwardEngagement(
                transport,
                qrEngagement.deviceEngagement,
                qrEngagement.handover
            )
            presentation = builder.build()
            presentation?.setSendSessionTerminationMessage(true)
            qrEngagement.close()
            onPresentationReady(session, presentation!!)
        }

        override fun onError(error: Throwable) {
            log("QR onError: ${error.message}")
            onCommunicationError(error)
        }
    }

    private val presentationListener = object : PresentationHelper.Listener {

        override fun onDeviceRequest(deviceRequestBytes: ByteArray) {
            log("Presentation Listener (QR): OnDeviceRequest")
            onNewDeviceRequest(deviceRequestBytes)
        }

        override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {
            log("Presentation Listener (QR): onDeviceDisconnected")
            onDisconnected(transportSpecificTermination)
        }

        override fun onError(error: Throwable) {
            log("Presentation Listener (QR): onError -> ${error.message}")
            onCommunicationError(error)
        }
    }

    private lateinit var qrEngagement: QrEngagementHelper
    private var presentation: PresentationHelper? = null

    val deviceEngagementUriEncoded: String
        get() = qrEngagement.deviceEngagementUriEncoded

    fun configure() {
        qrEngagement = QrEngagementHelper(
            context,
            session,
            connectionSetup.getConnectionMethods(),
            connectionSetup.getConnectionOptions(),
            nfcApduRouter,
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