package org.multipaz.compose.qrcode

import androidx.compose.runtime.MutableState
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Simple
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodBle
import org.multipaz.mdoc.engagement.EngagementGenerator
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.mdoc.transport.MdocTransportFactory
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.mdoc.transport.advertise
import org.multipaz.mdoc.transport.waitForConnection
import org.multipaz.models.presentment.MdocPresentmentMechanism
import org.multipaz.models.presentment.PresentmentModel
import org.multipaz.util.UUID
import kotlin.coroutines.cancellation.CancellationException

fun startQrPresentment(
    presentmentModel: PresentmentModel,
    deviceEngagement: MutableState<ByteString?>
) {
    presentmentModel.reset()
    presentmentModel.setConnecting()
    presentmentModel.presentmentScope.launch {
        try {
            val connectionMethods = listOf(
                MdocConnectionMethodBle(
                    supportsPeripheralServerMode = false,
                    supportsCentralClientMode = true,
                    peripheralServerModeUuid = null,
                    centralClientModeUuid = UUID.randomUUID(),
                )
            )
            val eDeviceKey = Crypto.createEcPrivateKey(EcCurve.P256)
            val advertisedTransports = connectionMethods.advertise(
                role = MdocRole.MDOC,
                transportFactory = MdocTransportFactory.Default,
                options = MdocTransportOptions(bleUseL2CAP = true),
            )
            val engagementGenerator = EngagementGenerator(
                eSenderKey = eDeviceKey.publicKey,
                version = EngagementGenerator.ENGAGEMENT_VERSION_1_0
            )
            engagementGenerator.addConnectionMethods(advertisedTransports.map {
                it.connectionMethod
            })
            val encodedDeviceEngagement = ByteString(engagementGenerator.generate())
            deviceEngagement.value = encodedDeviceEngagement
            val transport = advertisedTransports.waitForConnection(
                eSenderKey = eDeviceKey.publicKey,
                coroutineScope = presentmentModel.presentmentScope
            )
            presentmentModel.setMechanism(
                MdocPresentmentMechanism(
                    transport = transport,
                    eDeviceKey = eDeviceKey,
                    encodedDeviceEngagement = encodedDeviceEngagement,
                    handover = Simple.NULL,
                    engagementDuration = null,
                    allowMultipleRequests = false
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            presentmentModel.setCompleted(e)
        } finally {
            // to ensure the QR code is hidden even on error or cancellation.
            deviceEngagement.value = null
        }
    }
}