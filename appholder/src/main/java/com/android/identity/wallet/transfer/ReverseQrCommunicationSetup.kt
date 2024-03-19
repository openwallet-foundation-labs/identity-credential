package com.android.identity.wallet.transfer

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper
import com.android.identity.android.mdoc.transport.DataTransport
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcPublicKey
import com.android.identity.mdoc.engagement.EngagementParser
import com.android.identity.mdoc.origininfo.OriginInfo
import com.android.identity.wallet.util.PreferencesHelper
import com.android.identity.wallet.util.log
import com.android.identity.wallet.util.mainExecutor

class ReverseQrCommunicationSetup(
    private val context: Context,
    private val onPresentationReady: (presentation: DeviceRetrievalHelper) -> Unit,
    private val onNewRequest: (request: ByteArray) -> Unit,
    private val onDisconnected: () -> Unit,
    private val onCommunicationError: (error: Throwable) -> Unit,
) {

    private val settings = PreferencesHelper.apply { initialize(context) }
    private val connectionSetup = ConnectionSetup(context)
    private val eDeviceKey = Crypto.createEcPrivateKey(settings.getEphemeralKeyCurveOption())

    private var presentation: DeviceRetrievalHelper? = null

    private val presentationListener = object : DeviceRetrievalHelper.Listener {
        override fun onEReaderKeyReceived(eReaderKey: EcPublicKey) {
            log("DeviceRetrievalHelper Listener (QR): OnEReaderKeyReceived")
        }

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
        if (engagement.connectionMethods.isEmpty()) {
            throw IllegalStateException("No connection methods in engagement")
        }

        // For now, just pick the first transport
        val connectionMethod = engagement.connectionMethods[0]
        log("Using connection method $connectionMethod")

        val transport = DataTransport.fromConnectionMethod(
            context = context,
            connectionMethod = connectionMethod,
            role = DataTransport.Role.MDOC,
            options = connectionSetup.getConnectionOptions()
        )

        presentation = DeviceRetrievalHelper
            .Builder(
                context = context,
                listener = presentationListener,
                executor = context.mainExecutor(),
                eDeviceKey = eDeviceKey
            )
            .useReverseEngagement(transport, encodedReaderEngagement, origins)
            .build()
        onPresentationReady(requireNotNull(presentation))
    }
}