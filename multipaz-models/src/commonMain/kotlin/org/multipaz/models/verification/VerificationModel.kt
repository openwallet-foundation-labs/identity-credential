package org.multipaz.models.verification

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.buildCborArray
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.X509CertChain
import org.multipaz.documenttype.DocumentCannedRequest
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.engagement.EngagementParser
import org.multipaz.mdoc.request.DeviceRequestGenerator
import org.multipaz.mdoc.response.DeviceResponseParser
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.mdoc.sessionencryption.SessionEncryption
import org.multipaz.mdoc.transport.MdocTransport
import org.multipaz.mdoc.transport.MdocTransportClosedException
import org.multipaz.mdoc.transport.MdocTransportFactory
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.mdoc.transport.NfcTransportMdocReader
import org.multipaz.nfc.scanNfcTag
import org.multipaz.util.Constants

class VerificationModel {
    private var mutableState = MutableSharedFlow<State>()

    val state: SharedFlow<State> get() = mutableState
    var mdocDeviceResponse: DeviceResponseParser.DeviceResponse? = null

    sealed class State

    class StartMdocTransport(val transport: MdocTransport) : State()
    object SendingMdocMessage : State()
    class ClosedMdocTransport(val transport: MdocTransport, val orderly: Boolean) : State()
    object UserClosed : State()
    class Error(val exception: Throwable) : State()

    class ReaderIdentity(
        val privateKey: EcPrivateKey,
        val certChain: X509CertChain,
    )

    fun start(
        state: String,
        request: DocumentCannedRequest
    ) {
    }

    private suspend fun starProximityVerification(
        readerIdentity: ReaderIdentity,
        request: DocumentCannedRequest,
        mdocVerificationMechanism: MdocVerificationMechanism
    ) {
        val deviceEngagement = EngagementParser(
            encodedEngagement = mdocVerificationMechanism.encodedDeviceEngagement.toByteArray()
        ).parse()
        val eDeviceKey = deviceEngagement.eSenderKey
        val eReaderKey = Crypto.createEcPrivateKey(eDeviceKey.curve)

        val transport = if (mdocVerificationMechanism.existingTransport != null) {
            mdocVerificationMechanism.existingTransport
        } else {
            val connectionMethods = MdocConnectionMethod.disambiguate(
                deviceEngagement.connectionMethods,
                MdocRole.MDOC_READER
            )
            val connectionMethod = if (connectionMethods.size == 1) {
                connectionMethods[0]
            } else {
                selectConnectionMethod(connectionMethods)
            }
            if (connectionMethod == null) {
                // If user canceled
                return
            }
            val transport = MdocTransportFactory.Default.createTransport(
                connectionMethod,
                MdocRole.MDOC_READER,
                MdocTransportOptions(bleUseL2CAP = bleUseL2CAP)
            )
            if (transport is NfcTransportMdocReader) {
                scanNfcTag(
                    message = "QR engagement with NFC Data Transfer. Move into NFC field of the mdoc",
                    tagInteractionFunc = { tag, _ ->
                        transport.setTag(tag)
                        doReaderFlowWithTransport(
                            readerIdentity = readerIdentity,
                            request = request,
                            transport = transport,
                            encodedDeviceEngagement = encodedDeviceEngagement,
                            handover = handover,
                            allowMultipleRequests = allowMultipleRequests,
                            readerSessionTranscript = readerSessionTranscript,
                            eDeviceKey = eDeviceKey,
                            eReaderKey = eReaderKey,
                        )
                    }
                )
                return
            }
            transport
        }
        doReaderFlowWithTransport(
            readerIdentity = readerIdentity,
            request = request,
            transport = transport,
            encodedDeviceEngagement = encodedDeviceEngagement,
            handover = handover,
            allowMultipleRequests = allowMultipleRequests,
            readerSessionTranscript = readerSessionTranscript,
            eDeviceKey = eDeviceKey,
            eReaderKey = eReaderKey,
        )
    }

    private suspend fun doReaderFlowWithTransport(
        readerIdentity: ReaderIdentity,
        request: DocumentCannedRequest,
        transport: MdocTransport,
        encodedDeviceEngagement: ByteString,
        handover: DataItem,
        allowMultipleRequests: Boolean,
        readerSessionTranscript: ByteArray,
        eDeviceKey: EcPublicKey,
        eReaderKey: EcPrivateKey,
    ) {
        mutableState.emit(StartMdocTransport(transport))

        val encodedSessionTranscript = generateEncodedSessionTranscript(
            encodedDeviceEngagement.toByteArray(),
            handover,
            eReaderKey.publicKey
        )
        val sessionEncryption = SessionEncryption(
            MdocRole.MDOC_READER,
            eReaderKey,
            eDeviceKey,
            encodedSessionTranscript,
        )
        val encodedDeviceRequest = generateEncodedDeviceRequest(
            request = request,
            encodedSessionTranscript = readerSessionTranscript,
            readerIdentity = readerIdentity
        )
        try {
            transport.open(eDeviceKey)
            mutableState.emit(SendingMdocMessage)
            transport.sendMessage(
                sessionEncryption.encryptMessage(
                    messagePlaintext = encodedDeviceRequest,
                    statusCode = null
                )
            )
            while (true) {
                val sessionData = transport.waitForMessage()
                if (sessionData.isEmpty()) {
                    mutableState.emit(ClosedMdocTransport(transport, false))
                    transport.close()
                    break
                }

                val (message, status) = sessionEncryption.decryptMessage(sessionData)
                if (message == null || message.isEmpty()) {
                    // TODO: is this a problem or not?
                    mutableState.emit(ClosedMdocTransport(transport, false))
                    transport.close()
                    break
                }
                val parser = DeviceResponseParser(
                    encodedDeviceResponse = message,
                    encodedSessionTranscript = readerSessionTranscript,
                )
                parser.setEphemeralReaderKey(eReaderKey)
                mdocDeviceResponse = parser.parse()
                if (status == Constants.SESSION_DATA_STATUS_SESSION_TERMINATION) {
                    mutableState.emit(ClosedMdocTransport(transport, true))
                    transport.close()
                    break
                }
                if (!allowMultipleRequests) {
                    mutableState.emit(ClosedMdocTransport(transport, true))
                    transport.sendMessage(SessionEncryption.encodeStatus(Constants.SESSION_DATA_STATUS_SESSION_TERMINATION))
                    transport.close()
                    break
                }
            }
        } catch (_: MdocTransportClosedException) {
            mutableState.emit(UserClosed)
        } finally {
            mutableState.emit(ClosedMdocTransport(transport, true))
            transport.close()
        }
    }

    private fun generateEncodedDeviceRequest(
        request: DocumentCannedRequest,
        encodedSessionTranscript: ByteArray,
        readerIdentity: ReaderIdentity
    ): ByteArray {
        val mdocRequest = request.mdocRequest!!
        val itemsToRequest = mutableMapOf<String, MutableMap<String, Boolean>>()
        for (ns in mdocRequest.namespacesToRequest) {
            for ((de, intentToRetain) in ns.dataElementsToRequest) {
                val itemMap = itemsToRequest.getOrPut(ns.namespace) { mutableMapOf() }
                itemMap[de.attribute.identifier] = intentToRetain
            }
        }

        val deviceRequestGenerator = DeviceRequestGenerator(encodedSessionTranscript)
        deviceRequestGenerator.addDocumentRequest(
            docType = mdocRequest.docType,
            itemsToRequest = itemsToRequest,
            requestInfo = null,
            readerKey = readerIdentity.privateKey,
            signatureAlgorithm = readerIdentity.privateKey.curve.defaultSigningAlgorithm,
            readerKeyCertificateChain = readerIdentity.certChain,
        )
        return deviceRequestGenerator.generate()
    }

    private fun generateEncodedSessionTranscript(
        encodedDeviceEngagement: ByteArray,
        handover: DataItem,
        eReaderKey: EcPublicKey
    ): ByteArray {
        val encodedEReaderKey = Cbor.encode(eReaderKey.toCoseKey().toDataItem())
        return Cbor.encode(
            buildCborArray {
                add(Tagged(24, Bstr(encodedDeviceEngagement)))
                add(Tagged(24, Bstr(encodedEReaderKey)))
                add(handover)
            }
        )
    }
}
