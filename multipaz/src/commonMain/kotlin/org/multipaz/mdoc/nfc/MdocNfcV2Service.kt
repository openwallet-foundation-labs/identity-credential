package org.multipaz.mdoc.nfc

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.buildCborArray
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.toDataItem
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodNfcV2
import org.multipaz.mdoc.engagement.Capability
import org.multipaz.mdoc.request.DeviceRequest
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.mdoc.sessionencryption.SessionEncryption
import org.multipaz.mdoc.transport.MdocTransport
import org.multipaz.mdoc.transport.MdocTransportFactory
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.mdoc.transport.NfcHybridTransportMdoc
import org.multipaz.nfc.CommandApdu
import org.multipaz.nfc.Nfc
import org.multipaz.nfc.ResponseApdu
import org.multipaz.presentment.EngagementParams
import org.multipaz.presentment.Iso18013Presentment
import org.multipaz.presentment.PresentmentCanceledException
import org.multipaz.presentment.PresentmentModel
import org.multipaz.presentment.PresentmentSource
import org.multipaz.prompt.PromptModel
import org.multipaz.util.Constants
import org.multipaz.util.Logger
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Base class for implementing NFCv2 engagement according to ISO/IEC 18013 Second Edition.
 *
 * Applications should subclass this and include the appropriate stanzas in its manifest
 * for binding to the appropriate AID (A0000002480401).
 *
 * @property sendResponse a function to send a response APDU via [CombinedNfcService].
 */
abstract class MdocNfcV2Service(
    sendResponse: (ByteArray) -> Unit
) : NfcApduService(sendResponse) {
    companion object {
        private const val TAG = "MdocNfcV2Service"

        // Active multi-tap state stored in companion so that subsequent taps
        // work regardless of whether Android reuses or recreates the HostApduService instance.
        @Volatile
        internal var hybridTransport: NfcHybridTransportMdoc? = null

        @Volatile
        internal var activeEngagementParamsFlow: MutableStateFlow<EngagementParams>? = null

        @Volatile
        internal var activeDeviceRequest: DeviceRequest? = null

        @Volatile
        internal var activeEngagement: MdocNfcV2EngagementHelper? = null

        @Volatile
        internal var onHybridTransportCreated: ((NfcHybridTransportMdoc) -> Unit)? = null
    }

    private fun vibrateError() {
        vibrateMdocNfcError()
    }

    private fun vibrateSuccess() {
        vibrateMdocNfcSuccess()
    }

    // A job started when onCreate is called and used for dispatching APDUs and onDeactivated events
    // to a suspend-world. Runs until onDestroy is called.
    private var dispatchJob: Job? = null

    // A job started when the reader has selected us and used for listening for
    // a signal from the UI that it wants to cancel during NFC engagement.
    private var listenForCancellationFromUiJob: Job? = null

    @Volatile
    private var engagement: MdocNfcV2EngagementHelper? = null

    // Channel used for bouncing data from processCommandApdu() and onDeactivated() to dispatchJob coroutine.
    private val dispatchChannel = Channel<Data>(Channel.UNLIMITED)

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private sealed class Data

    private data class CommandApduData(
        val commandApdu: CommandApdu
    ): Data()

    private data class DeactivatedData(
        val reason: Int
    ): Data()

    /**
     * Settings provided by the application for how to configure NFC engagement.
     *
     * @property source the [PresentmentSource] to use as the source of truth for what to present.
     * @property promptModel the [PromptModel] to use.
     * @property presentmentModel the [PresentmentModel] to update during presentment, or `null`.
     * @property launchUi a callback invoked when handover completes to launch the presentment UI, or `null`.
     * @property sessionEncryptionCurve the Elliptic Curve Cryptography curve to use for session encryption.
     * @property useNegotiatedHandover if `true` NFC negotiated handover will be used, otherwise NFC static handover.
     * @property negotiatedHandoverPreferredOrder a list of the preferred order for which kind of
     *   [org.multipaz.mdoc.transport.MdocTransport] to create when using NFC negotiated handover.
     * @property transportOptions the [MdocTransportOptions] to use for newly created connections.
     * @property transportFactory the [MdocTransportFactory] to use for creating transports.
     * @property capabilities the capabilities to convey to the mdoc reader.
     */
    data class Settings(
        val source: PresentmentSource,
        val promptModel: PromptModel,
        val presentmentModel: PresentmentModel?,
        val launchUi: (() -> Unit)? = null,
        val sessionEncryptionCurve: EcCurve,
        val useNegotiatedHandover: Boolean,
        val negotiatedHandoverPreferredOrder: List<String>,
        val transportOptions: MdocTransportOptions,
        val transportFactory: MdocTransportFactory = MdocTransportFactory.Default,
        val capabilities: Map<Capability, DataItem> = mapOf(
            Capability.READER_AUTH_ALL_SUPPORT to true.toDataItem(),
            Capability.EXTENDED_REQUEST_SUPPORT to true.toDataItem()
        )
    )

    /**
     * Must be implemented by the application to specify its preferences/settings for NFC engagement.
     *
     * Note that this is called after the NFC tap has been detected but before any messages are sent. As such
     * it's of paramount importance that this completes quickly because the NFC tag reader only stays in the
     * field for so long. Every millisecond literally counts, and it's very likely the application is cold-starting
     * so be mindful of doing expensive initializations here.
     *
     * @return a [Settings] object.
     */
    abstract suspend fun getSettings(): Settings

    override fun onCreate() {
        Logger.i(TAG, "onCreate")
        super.onCreate()

        // Start a coroutine on a background thread for handling incoming APDUs and deactivation events
        // from the OS in processCommandApdu() and onDeactivated() overrides. This is so we can
        // use suspend functions.
        //
        dispatchJob = serviceScope.launch(Dispatchers.Default) {
            for (data in dispatchChannel) {
                try {
                    when (data) {
                        is CommandApduData -> {
                            processCommandApdu(data.commandApdu)?.let { responseApdu ->
                                try {
                                    sendResponseApdu(responseApdu.encode())
                                } catch (e: Exception) {
                                    Logger.w(TAG, "Error sending response APDU", e)
                                }
                            }
                        }
                        is DeactivatedData -> {
                            processDeactivated(data.reason)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        if (!isActive) {
                            // The whole service/job is being shut down, let the exception propagate so the loop dies.
                            throw e
                        }
                        // Only the current sub-task (APDU processing) was aborted, keep the loop alive for the next tap.
                        Logger.i(TAG, "dispatchJob: APDU processing cancelled (likely due to deactivation)")
                    } else {
                        Logger.e(TAG, "Error processing data from channel", e)
                    }
                }
            }
        }
    }

    @Volatile
    private var pendingEngagementDeviceKey: EcPrivateKey? = null

    override fun onDestroy() {
        Logger.i(TAG, "onDestroy")
        super.onDestroy()
        val currentEngagement = engagement
        if (activeEngagement === currentEngagement) {
            activeEngagement = null
        }
        engagement = null
        pendingEngagementDeviceKey?.close()
        pendingEngagementDeviceKey = null
        serviceScope.cancel()
        dispatchJob = null
        listenForCancellationFromUiJob?.cancel()
        listenForCancellationFromUiJob = null
        dispatchChannel.close()
    }

    @Volatile
    private var engagementStarted = false
    @Volatile
    private var engagementComplete = false

    private suspend fun startEngagement() {
        Logger.i(TAG, "startEngagement")

        // Note: Every millisecond literally counts here because we're handling a
        // NFC tap and users tend to remove their phone from the reader really fast. So
        // log how much time the application takes to give us settings.
        //
        val t0 = Clock.System.now()
        val settings = getSettings()
        val t1 = Clock.System.now()
        Logger.i(TAG, "Settings provided by application in ${(t1 - t0).inWholeMilliseconds} ms")

        pendingEngagementDeviceKey?.close()
        val eDeviceKey = Crypto.createEcPrivateKey(settings.sessionEncryptionCurve)
        pendingEngagementDeviceKey = eDeviceKey
        val timeStarted = Clock.System.now()

        val currentJob = transactionJob
        if (currentJob != null && !currentJob.isActive && !currentJob.isCompleted) {
            currentJob.join()
        }
        if (transactionJob?.isActive != true) {
            settings.presentmentModel?.setConnecting()
        }

        listenForCancellationFromUiJob?.cancel()
        listenForCancellationFromUiJob = serviceScope.launch(Dispatchers.Default) {
            settings.presentmentModel?.state?.collect { state ->
                if (state == PresentmentModel.State.CanceledByUser) {
                    engagementComplete = true
                    transactionJob?.cancel()
                }
            }
        }

        // TODO: Listen on methods _before_ starting the engagement helper so we can send the PSM
        //   for mdoc Peripheral Server mode when using NFC Static Handover.
        //
        lateinit var currentEngagement: MdocNfcV2EngagementHelper
        currentEngagement = MdocNfcV2EngagementHelper(
            eDeviceKey = eDeviceKey.publicKey,
            onHandoverComplete = { connectionMethod, encodedDeviceEngagement, handover ->
                // OK, we're done with engagement, and we're communicating with a bona fide ISO/IEC 18013-5 Second
                // Edition reader capable of NFCv2. Let the user know and launch a new job to start the transaction.
                //
                vibrateSuccess()

                if (pendingEngagementDeviceKey === eDeviceKey) {
                    pendingEngagementDeviceKey = null
                }
                activeEngagement = currentEngagement

                val currentParams = EngagementParams(
                    eDeviceKey = eDeviceKey,
                    deviceEngagement = Cbor.decode(encodedDeviceEngagement.toByteArray()),
                    handover = handover
                )

                val isNfcOnly = connectionMethod is MdocConnectionMethodNfcV2

                if (isNfcOnly &&
                    hybridTransport?.isNfcOnly == true &&
                    transactionJob?.isActive == true &&
                    activeEngagementParamsFlow != null
                ) {
                    Logger.i(TAG, "Re-tap detected during active presentment. Updating engagement parameters.")
                    val previousParams = activeEngagementParamsFlow?.value
                    activeEngagementParamsFlow?.value = currentParams
                    if (previousParams != null && previousParams.eDeviceKey !== currentParams.eDeviceKey) {
                        previousParams.eDeviceKey.close()
                    }
                    return@MdocNfcV2EngagementHelper
                }

                val previousTransactionJob = transactionJob
                previousTransactionJob?.cancel(TransactionSupersededException())

                val paramsFlow = MutableStateFlow(currentParams)
                activeEngagementParamsFlow = paramsFlow
                activeDeviceRequest = null

                lateinit var newTransport: NfcHybridTransportMdoc
                newTransport = NfcHybridTransportMdoc(
                    sendMessageViaNfc = { message ->
                        if (hybridTransport === newTransport) {
                            activeEngagement?.let {
                                it.sendMessage(message)
                                true
                            } ?: false
                        } else {
                            false
                        }
                    }
                )
                if (!isNfcOnly) {
                    newTransport.setExpectTransport(true)
                }
                hybridTransport = newTransport
                onHybridTransportCreated?.invoke(newTransport)

                // We launch transactionJob in a new detached scope so it survives both
                // NFC deactivation (the reader moving away) and the Service's onDestroy
                // (as the transaction may continue over BLE and wait for UI consent).
                transactionJob = CoroutineScope(Dispatchers.Default + settings.promptModel).launch {
                    startTransaction(
                        previousTransactionJob = previousTransactionJob,
                        timeStarted = timeStarted,
                        transport = newTransport,
                        settings = settings,
                        connectionMethod = connectionMethod,
                        engagementParamsFlow = paramsFlow,
                    )
                }
            },
            onMessageReceived = { message ->
                if (hybridTransport?.isNfcOnly == true) {
                    try {
                        val readerKey = SessionEncryption.getEReaderKey(message.toByteArray())
                        activeEngagementParamsFlow?.value?.let { current ->
                            val updatedParams = current.copy(eReaderKey = readerKey.publicKey)
                            activeEngagementParamsFlow?.value = updatedParams

                            val currentActiveRequest = activeDeviceRequest
                            if (currentActiveRequest != null) {
                                val sessionTranscript = buildCborArray {
                                    add(Tagged(Tagged.ENCODED_CBOR, Bstr(Cbor.encode(updatedParams.deviceEngagement))))
                                    add(Tagged(Tagged.ENCODED_CBOR, Bstr(Cbor.encode(readerKey.publicKey.toCoseKey().toDataItem()))))
                                    add(updatedParams.handover)
                                }
                                val (encodedDeviceRequest, _) = SessionEncryption(
                                    role = MdocRole.MDOC,
                                    eSelfKey = updatedParams.eDeviceKey.duplicate(),
                                    remotePublicKey = readerKey.publicKey,
                                    encodedSessionTranscript = Cbor.encode(sessionTranscript)
                                ).use { sessionEncryption ->
                                    sessionEncryption.decryptMessage(message.toByteArray())
                                }
                                if (encodedDeviceRequest != null) {
                                    val newDeviceRequest = DeviceRequest.fromDataItem(Cbor.decode(encodedDeviceRequest))
                                    if (!newDeviceRequest.isStructurallyEquivalent(currentActiveRequest)) {
                                        Logger.iCbor(TAG, "Current active request", currentActiveRequest.toDataItem())
                                        Logger.iCbor(TAG, "New request", newDeviceRequest.toDataItem())
                                        Logger.w(TAG, "Incoming DeviceRequest is NOT structurally equivalent to active request. Killing old session.")
                                        transactionJob?.cancel()
                                        activeDeviceRequest = null
                                        hybridTransport = null
                                        activeEngagement?.sendMessage(
                                            ByteString(
                                                SessionEncryption.encodeStatus(
                                                    Constants.SESSION_DATA_STATUS_SESSION_TERMINATION
                                                )
                                            )
                                        )
                                        return@MdocNfcV2EngagementHelper
                                    } else {
                                        Logger.i(TAG, "Incoming DeviceRequest IS structurally equivalent to active request.")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Not a SessionEstablishment message or decryption failed, ignore
                    }
                }
                hybridTransport?.onMessageReceivedViaNfc(message)
            },
            onError = { error ->
                // Engagement failed. This can happen if a NDEF tag reader - for example another unlocked
                // Android device - is reading this device. So we really don't want any user-visible side effects
                // here such as showing an error or vibrating the phone.
                //
                engagementComplete = true
                if (pendingEngagementDeviceKey === eDeviceKey) {
                    pendingEngagementDeviceKey?.close()
                    pendingEngagementDeviceKey = null
                }
                if (transactionJob?.isActive != true) {
                    settings.presentmentModel?.setCompleted(error)
                }
                Logger.w(TAG, "Engagement failed. Maybe this wasn't an ISO mdoc reader.", error)
            },
            negotiatedHandoverPicker = { connectionMethods ->
                if (settings.useNegotiatedHandover) {
                    for (prefix in settings.negotiatedHandoverPreferredOrder) {
                        for (connectionMethod in connectionMethods) {
                            if (connectionMethod.toString().startsWith(prefix)) {
                                return@MdocNfcV2EngagementHelper connectionMethod
                            }
                        }
                    }
                    return@MdocNfcV2EngagementHelper connectionMethods.first()
                } else {
                    connectionMethods.find { it is MdocConnectionMethodNfcV2 }!!
                }
            },
            capabilities = settings.capabilities
        )
        engagement = currentEngagement
    }

    private suspend fun startTransaction(
        previousTransactionJob: Job?,
        timeStarted: Instant,
        transport: NfcHybridTransportMdoc,
        settings: Settings,
        connectionMethod: MdocConnectionMethod,
        engagementParamsFlow: StateFlow<EngagementParams>,
    ) {
        val transactionJobContext = currentCoroutineContext()
        val isNfcOnly = connectionMethod is MdocConnectionMethodNfcV2
        var enteredPresentment = false
        var waitForTransportJob: Job? = null
        var uiCancelJob: Job? = null
        var nfcConnectedJob: Job? = null

        try {
            previousTransactionJob?.let { prevJob ->
                Logger.i(TAG, "Waiting for previous active transactionJob to finish cleanup")
                prevJob.join()
            }

            settings.presentmentModel?.setNfcOnly(isNfcOnly)

            settings.launchUi?.invoke()

            val engagementDuration = Clock.System.now() - timeStarted
            val initialParams = engagementParamsFlow.value
            val eDeviceKey = initialParams.eDeviceKey

            uiCancelJob = CoroutineScope(transactionJobContext).launch {
                settings.presentmentModel?.state?.collect { state ->
                    if (state == PresentmentModel.State.CanceledByUser) {
                        transactionJobContext.cancel(CancellationException("Presentment was cancelled by user"))
                    }
                }
            }

            nfcConnectedJob = if (isNfcOnly) {
                CoroutineScope(transactionJobContext).launch {
                    transport.isNfcConnected.collect { connected ->
                        settings.presentmentModel?.setNfcConnected(connected)
                    }
                }
            } else {
                null
            }

            val preselectedDocuments = settings.presentmentModel?.documentsSelected?.value ?: emptyList()
            settings.presentmentModel?.setConnecting()

            transport.open(eDeviceKey.publicKey)

            if (isNfcOnly) {
                transport.setExpectTransport(false)
            } else {
                transport.setExpectTransport(true)
                // Wait for the non-NFC transport in a coroutine so we are not blocking
                // initiating presentment....
                waitForTransportJob = CoroutineScope(transactionJobContext).launch(Dispatchers.Default) {
                    var negotiatedTransport: MdocTransport? = null
                    var handedOffToHybrid = false
                    try {
                        val createdTransport = settings.transportFactory.createTransport(
                            connectionMethod = connectionMethod,
                            role = MdocRole.MDOC,
                            options = settings.transportOptions
                        )
                        negotiatedTransport = createdTransport
                        createdTransport.open(eSenderKey = eDeviceKey.publicKey)
                        transport.setTransport(createdTransport)
                        handedOffToHybrid = true

                        // Monitor the secondary transport
                        createdTransport.state.collect { state ->
                            if (state == MdocTransport.State.FAILED || state == MdocTransport.State.CLOSED) {
                                transactionJobContext.cancel(CancellationException("Secondary transport $state"))
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Logger.w(TAG, "Error opening non-NFC transport", e)
                    } finally {
                        if (!handedOffToHybrid) {
                            withContext(NonCancellable) {
                                try {
                                    negotiatedTransport?.close()
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Logger.w(TAG, "Error closing unattached negotiated transport", e)
                                }
                            }
                        }
                    }
                }
            }

            enteredPresentment = true
            Iso18013Presentment(
                transport = transport,
                engagementParams = engagementParamsFlow,
                source = settings.source,
                keyAgreementPossible = listOf(eDeviceKey.curve),
                preselectedDocuments = preselectedDocuments,
                insertSequenceNumbers = true,
                onWaitingForRequest = { settings.presentmentModel?.setWaitingForReader() },
                onWaitingForUserInput = { settings.presentmentModel?.setWaitingForUserInput() },
                onDocumentsInFocus = { documents ->
                    settings.presentmentModel?.setDocumentsSelected(selectedDocuments = documents)
                },
                onSendingResponse = { settings.presentmentModel?.setSending() },
                onDeviceRequest = { deviceRequest ->
                    activeDeviceRequest = deviceRequest
                }
            )
            settings.presentmentModel?.setCompleted(null)
        } catch (e: Exception) {
            if (e is TransactionSupersededException) {
                Logger.i(TAG, "Transaction was superseded by a new NFC tap")
            } else {
                Logger.w(TAG, "Caught error while performing 18013-5 transaction", e)
                if (e is CancellationException) {
                    settings.presentmentModel?.setCompleted(PresentmentCanceledException("Presentment was cancelled"))
                } else {
                    settings.presentmentModel?.setCompleted(e)
                }
            }
        } finally {
            withContext(NonCancellable) {
                uiCancelJob?.cancel()
                nfcConnectedJob?.cancel()
                listenForCancellationFromUiJob?.cancel()
                listenForCancellationFromUiJob = null
                waitForTransportJob?.let {
                    it.cancel()
                    it.join()
                }
                if (!enteredPresentment) {
                    try {
                        engagementParamsFlow.value.eDeviceKey.close()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Logger.w(TAG, "Error closing eDeviceKey", e)
                    }
                    try {
                        transport.close()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Logger.w(TAG, "Error closing hybrid transport", e)
                    }
                }
                if (activeEngagementParamsFlow === engagementParamsFlow) {
                    activeEngagementParamsFlow = null
                    activeDeviceRequest = null
                }
                if (hybridTransport === transport) {
                    hybridTransport = null
                }
            }
        }
    }

    private fun cancelEngagementJobs() {
        listenForCancellationFromUiJob?.cancel()
        listenForCancellationFromUiJob = null
    }

    private val CommandApdu.isApplicationSelect: Boolean
        get() = ins == Nfc.INS_SELECT && p1 == Nfc.INS_SELECT_P1_APPLICATION

    private val firstCommandResponse = ResponseApdu(
        status = Nfc.RESPONSE_STATUS_SUCCESS,
        payload = ByteString(Cbor.encode(buildCborMap {
            put(0, 65536L /* apduCommandMaxSize */)
        }))
    )
    private var applicationSelectProcessed = false

    // Called by coroutine running in background thread, see onCreate() for details
    private suspend fun processCommandApdu(commandApdu: CommandApdu): ResponseApdu? {
        if (commandApdu.isApplicationSelect && engagementStarted) {
            Logger.i(TAG, "Received SELECT APPLICATION while engagement already in progress; resetting for new engagement")
            engagement?.processDeactivated(0)
            if (activeEngagement === engagement) {
                activeEngagement = null
            }
            engagement = null
            pendingEngagementDeviceKey?.close()
            pendingEngagementDeviceKey = null
            engagementStarted = false
            engagementComplete = false
            cancelEngagementJobs()
        }

        // TODO: maybe need replay and super-fast response as in mdocReaderNfcHandover.kt function processCommandApdu()
        if (!engagementStarted) {
            engagementStarted = true
            startEngagement()
        }
        try {
            engagement?.let {
                val responseApdu = it.processApdu(commandApdu)
                if (commandApdu.isApplicationSelect && applicationSelectProcessed) {
                    Logger.i(TAG, "Skipping response to APPLICATION SELECT since it's already been processed")
                } else {
                    return responseApdu
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.e(TAG, "Error processing APDU in MdocNfcV2EngagementHelper", e)
        }
        return null
    }

    // Called by coroutine running in background thread, see onCreate() for details
    private suspend fun processDeactivated(reason: Int) {
        try {
            val currentEngagement = engagement
            currentEngagement?.processDeactivated(reason)
            hybridTransport?.onNfcDeactivated(reason)

            // Android might reuse this service for the next tap. That is, we can't rely on onDestroy()
            // firing right after this, then onCreate(). So reset everything so next time the OS calls
            // processCommandApdu() we'll start processing a new engagement.
            //
            if (activeEngagement === currentEngagement) {
                activeEngagement = null
            }
            engagement = null
            pendingEngagementDeviceKey?.close()
            pendingEngagementDeviceKey = null
            engagementStarted = false
            engagementComplete = false
            applicationSelectProcessed = false

            cancelEngagementJobs()
            if (transactionJob?.isActive != true) {
                hybridTransport = null
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.e(TAG, "Error processing deactivation event in MdocNfcV2EngagementHelper", e)
        }
    }

    // Called by OS when an APDU arrives
    override fun processCommandApdu(encodedCommandApdu: ByteArray): ByteArray? {
        // Bounce the APDU to processCommandApdu() above via the coroutine in background thread set up in onCreate().
        //
        // With Extended APDUs it's possible we get a partial APDU so gracefully handle if decoding fails. Simply
        // log and discard, we'll likely get hit with an onDeactivated call soon anyway.
        //
        val commandApdu = try {
            CommandApdu.decode(encodedCommandApdu)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "Error decoding APDU", e)
            return null
        }
        if (commandApdu.isApplicationSelect) {
            engagementComplete = false
        }
        if (!engagementComplete) {
            if (commandApdu.isApplicationSelect) {
                applicationSelectProcessed = true
                Logger.i(TAG, "Processing APPLICATION SELECT synchronously")
                val unused = dispatchChannel.trySend(CommandApduData(commandApdu))
                return firstCommandResponse.encode()
            } else {
                val unused = dispatchChannel.trySend(CommandApduData(commandApdu))
            }
        } else {
            Logger.w(TAG, "Engagement complete but received APDU $commandApdu")
        }
        return null
    }

    // Called by OS when NFC tag reader deactivates
    override fun onDeactivated(reason: Int) {
        Logger.i(TAG, "onDeactivated: reason=$reason")
        // Important: we must call these here to unblock dispatchJob if it is suspended in
        // engagement.processApdu() waiting for a response to send back to the reader, and
        // to ensure hybridTransport is marked disconnected even if onDestroy() runs before
        // dispatchJob processes DeactivatedData.
        val currentEngagement = engagement
        currentEngagement?.processDeactivated(reason)
        if (activeEngagement === currentEngagement) {
            activeEngagement = null
        }
        hybridTransport?.onNfcDeactivated(reason)
        // Bounce the event to processDeactivated() above via the coroutine in background thread set up in onCreate()
        val unused = dispatchChannel.trySend(DeactivatedData(reason))
    }
}
