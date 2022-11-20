package com.android.mdl.app.transfer

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.android.identity.DataTransport
import com.android.identity.EngagementParser
import com.android.identity.OriginInfo
import com.android.identity.PresentationHelper
import com.android.identity.PresentationSession
import com.android.mdl.app.util.log
import com.android.mdl.app.util.mainExecutor

class ReverseQrCommunicationSetup(
    private val context: Context,
    private val onPresentationReady: (session: PresentationSession, presentation: PresentationHelper) -> Unit,
    private val onNewRequest: (request: ByteArray) -> Unit,
    private val onDisconnected: () -> Unit,
    private val onCommunicationError: (error: Throwable) -> Unit,
) {

    private val session = SessionSetup(CredentialStore(context)).createSession()
    private val connectionSetup = ConnectionSetup(context)
    private val presentationListener = object : PresentationHelper.Listener {

        override fun onDeviceRequest(deviceRequestBytes: ByteArray) {
            onNewRequest(deviceRequestBytes)
        }

        override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {
            onDisconnected()
        }

        override fun onError(error: Throwable) {
            onCommunicationError(error)
        }
    }

    private var presentation: PresentationHelper? = null

    fun configure(
        reverseEngagementUri: String,
        origins: List<OriginInfo>
    ) {
        val uri = Uri.parse(reverseEngagementUri)
        if (!uri.scheme.equals("mdoc")) {
            throw IllegalStateException("Only supports mdoc URIs")
        }
        val encodedReaderEngagement = Base64.decode(
            uri.encodedSchemeSpecificPart,
            Base64.URL_SAFE or Base64.NO_PADDING
        )
        val engagement = EngagementParser(encodedReaderEngagement).parse()
        if (engagement.connectionMethods.size == 0) {
            throw IllegalStateException("No connection methods in engagement")
        }

        // For now, just pick the first transport
        val connectionMethod = engagement.connectionMethods[0]
        log("Using connection method $connectionMethod")

        val transport = connectionMethod.createDataTransport(
            context,
            DataTransport.ROLE_MDOC,
            connectionSetup.getConnectionOptions()
        )

        val builder = PresentationHelper.Builder(
            context,
            presentationListener,
            context.mainExecutor(),
            session
        ).useReverseEngagement(transport, encodedReaderEngagement, origins)
        presentation = builder.build()
        presentation?.setSendSessionTerminationMessage(true)
        onPresentationReady(session, presentation!!)
    }
}