package org.multipaz.mdoc.nfc

import org.multipaz.cbor.DataItem
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.transport.MdocTransport
import org.multipaz.mdoc.transport.MdocTransportFactory
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.mdoc.transport.NfcTransportMdocReader
import org.multipaz.nfc.scanNfcTag
import org.multipaz.prompt.PromptDismissedException
import org.multipaz.util.Logger
import kotlinx.io.bytestring.ByteString
import org.multipaz.mdoc.role.MdocRole

const private val TAG = "scanNfcMdocReader"

private data class ScanNfcMdocReaderResult(
    val transport: MdocTransport,
    val encodedDeviceEngagement: ByteString,
    val handover: DataItem,
)

/**
 * Performs NFC engagement as a reader.
 *
 * This uses [scanNfcTag] to show a dialog prompting the user to tap the mdoc.
 *
 * When a connection has been established, [onHandover] is called with the created transport as well as
 * the device engagement and handover structures used.
 *
 * If the given transport is for [org.multipaz.mdoc.connectionmethod.MdocConnectionMethodNfc] the
 * [onHandover] callback is called in the same coroutine used for interacting with the tag to ensure
 * continued communications with the remote NFC tag reader and the `updateMessage` parameter to [onHandover]
 * will be non-`null` which the application can use to update the message in the NFC scanning dialog.
 * Otherwise [onHandover] is called in the calling coroutine right after the NFC interaction with
 * `updateMessage` set to `null`. In either case, any exception thrown in [onHandover] will be thrown
 * from this method.
 *
 * @param message the message to display in the NFC tag scanning dialog or `null` to not show a dialog. Not all
 *   platforms supports not showing a dialog, use [org.multipaz.nfc.nfcTagScanningSupportedWithoutDialog] to check at
 *   runtime if the platform supports this.
 * @param options the [MdocTransportOptions] used to create new [MdocTransport] instances.
 * @param transportFactory the factory used to create [MdocTransport] instances.
 * @param selectConnectionMethod used to choose a connection method if the remote mdoc is using NFC static handover.
 * @param negotiatedHandoverConnectionMethods the connection methods to offer if the remote mdoc is using NFC
 * Negotiated Handover.
 * @param onHandover: Will be called on successful handover.
 * @return `true` if [onHandover] was invoked, `false` if no handover happened.
 */
suspend fun scanNfcMdocReader(
    message: String?,
    options: MdocTransportOptions,
    transportFactory: MdocTransportFactory = MdocTransportFactory.Default,
    selectConnectionMethod: suspend (connectionMethods: List<MdocConnectionMethod>) -> MdocConnectionMethod?,
    negotiatedHandoverConnectionMethods: List<MdocConnectionMethod>,
    onHandover: suspend (
        transport: MdocTransport,
        encodedDeviceEngagement: ByteString,
        handover: DataItem,
        updateMessage: ((message: String) -> Unit)?
    ) -> Unit
): Boolean {
    // Start creating transports for Negotiated Handover and start advertising these
    // immediately. This helps with connection time because the holder's device will
    // get a chance to opportunistically read the UUIDs which helps reduce scanning
    // time.
    //
    val negotiatedHandoverTransports = negotiatedHandoverConnectionMethods.map {
        val transport = transportFactory.createTransport(
            it,
            MdocRole.MDOC_READER,
            options
        )
        transport.advertise()
        transport
    }
    // Make sure we don't leak connections...
    val transportsToClose = negotiatedHandoverTransports.toMutableList()

    try {
        val result = scanNfcTag(
            message = message,
            tagInteractionFunc = tagInteractionFunc@{ tag, updateMessage ->
                val handoverResult = mdocReaderNfcHandover(
                    tag = tag,
                    negotiatedHandoverConnectionMethods = negotiatedHandoverTransports.map { it.connectionMethod },
                )
                if (handoverResult == null) {
                    return@tagInteractionFunc null
                }
                val connectionMethod = if (handoverResult.connectionMethods.size == 1) {
                    handoverResult.connectionMethods[0]
                } else {
                    selectConnectionMethod(handoverResult.connectionMethods)
                }
                if (connectionMethod == null) {
                    return@tagInteractionFunc null
                }

                // Now that we're connected, close remaining transports and see if one of the warmed-up
                // transports was chosen (can happen for negotiated handover, never for static handover)
                //
                var transport: MdocTransport? = null
                transportsToClose.forEach {
                    if (it.connectionMethod == connectionMethod) {
                        transport = it
                    } else {
                        Logger.i(TAG, "Closing connection with CM ${it.connectionMethod}")
                        it.close()
                    }
                }
                transportsToClose.clear()
                if (transport == null) {
                    transport = transportFactory.createTransport(
                        connectionMethod,
                        MdocRole.MDOC_READER,
                        options
                    )
                }

                val result = ScanNfcMdocReaderResult(
                    transport = transport!!,
                    encodedDeviceEngagement = handoverResult.encodedDeviceEngagement,
                    handover = handoverResult.handover
                )

                // If using NFC transport, run onHandover synchronously to keep the NFC tag reading
                // dialog visible and NfcIsoTag instance alive.
                if (result.transport is NfcTransportMdocReader) {
                    result.transport.setTag(tag)
                    onHandover(
                        result.transport,
                        result.encodedDeviceEngagement,
                        result.handover,
                        updateMessage
                    )
                }
                result
            }
        )
        if (result.transport !is NfcTransportMdocReader) {
            onHandover(result.transport, result.encodedDeviceEngagement, result.handover, null)
        }
        return true
    } catch (_: PromptDismissedException) {
        return false
    } finally {
        // Close listening transports that went unused.
        transportsToClose.forEach {
            Logger.i(TAG, "Closing connection with CM ${it.connectionMethod}")
            it.close()
        }
    }
}
