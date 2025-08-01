package org.multipaz.testapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.DiagnosticOption
import org.multipaz.cbor.Simple
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.EcPublicKey
import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.documenttype.DocumentCannedRequest
import org.multipaz.documenttype.DocumentType
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodBle
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodNfc
import org.multipaz.mdoc.engagement.EngagementParser
import org.multipaz.mdoc.nfc.scanNfcMdocReader
import org.multipaz.mdoc.response.DeviceResponseParser
import org.multipaz.mdoc.sessionencryption.SessionEncryption
import org.multipaz.mdoc.transport.MdocTransport
import org.multipaz.mdoc.transport.MdocTransportClosedException
import org.multipaz.mdoc.transport.MdocTransportFactory
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.mdoc.transport.NfcTransportMdocReader
import org.multipaz.nfc.scanNfcTag
import org.multipaz.testapp.App
import org.multipaz.testapp.TestAppUtils
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.util.Constants
import org.multipaz.util.Logger
import org.multipaz.util.UUID
import org.multipaz.util.fromBase64Url
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.bytestring.ByteString
import org.multipaz.compose.cards.InfoCard
import org.multipaz.compose.cards.WarningCard
import org.multipaz.compose.decodeImage
import org.multipaz.compose.permissions.rememberBluetoothPermissionState
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.mdoc.zkp.ZkDocument
import org.multipaz.mdoc.zkp.ZkSystemRepository

private const val TAG = "IsoMdocProximityReadingScreen"

private data class ConnectionMethodPickerData(
    val showPicker: Boolean,
    val connectionMethods: List<MdocConnectionMethod>,
    val continuation:  CancellableContinuation<MdocConnectionMethod?>,
)

private suspend fun selectConnectionMethod(
    connectionMethods: List<MdocConnectionMethod>,
    connectionMethodPickerData: MutableState<ConnectionMethodPickerData?>
): MdocConnectionMethod? {
    return suspendCancellableCoroutine { continuation ->
        connectionMethodPickerData.value = ConnectionMethodPickerData(
            showPicker = true,
            connectionMethods = connectionMethods,
            continuation = continuation
        )
    }
}

private data class RequestPickerEntry(
    val displayName: String,
    val documentType: DocumentType,
    val sampleRequest: DocumentCannedRequest
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
@Composable
fun IsoMdocProximityReadingScreen(
    app: App,
    showToast: (message: String) -> Unit,
) {
    val availableRequests = mutableListOf<RequestPickerEntry>()
    for (documentType in TestAppUtils.provisionedDocumentTypes) {
        for (sampleRequest in documentType.cannedRequests) {
            availableRequests.add(RequestPickerEntry(
                displayName = "${documentType.displayName}: ${sampleRequest.displayName}",
                documentType = documentType,
                sampleRequest = sampleRequest
            ))
        }
    }
    val dropdownExpanded = remember { mutableStateOf(false) }
    val selectedRequest = remember { mutableStateOf(availableRequests[0]) }
    val blePermissionState = rememberBluetoothPermissionState()
    val coroutineScope = rememberCoroutineScope { app.promptModel }
    val readerShowQrScanner = remember { mutableStateOf(false) }
    val readerTransport = remember { mutableStateOf<MdocTransport?>(null) }
    val readerSessionEncryption = remember { mutableStateOf<SessionEncryption?>(null) }
    val readerSessionTranscript = remember { mutableStateOf<ByteArray?>(null) }
    val readerMostRecentDeviceResponse = remember { mutableStateOf<ByteArray?>(null) }
    val connectionMethodPickerData = remember { mutableStateOf<ConnectionMethodPickerData?>(null) }
    val eReaderKey = remember { mutableStateOf<EcPrivateKey?>(null) }

    var readerJob by remember { mutableStateOf<Job?>(null) }

    if (connectionMethodPickerData.value != null) {
        val radioOptions = connectionMethodPickerData.value!!.connectionMethods
        val (selectedOption, onOptionSelected) = remember { mutableStateOf(radioOptions[0]) }
        AlertDialog(
            title = @Composable { Text(text = "Select Connection Method") },
            text = @Composable {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(
                        modifier = Modifier.selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        radioOptions.forEach { connectionMethod ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = (connectionMethod == selectedOption),
                                        onClick = { onOptionSelected(connectionMethod) },
                                        role = Role.RadioButton
                                    )
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioButton(
                                    selected = (connectionMethod == selectedOption),
                                    onClick = null
                                )
                                Text(
                                    text = connectionMethod.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                }
            },
            dismissButton = @Composable {
                TextButton(
                    onClick = {
                        connectionMethodPickerData.value!!.continuation.resume(null) { _, _, _ -> null }
                        connectionMethodPickerData.value = null
                    }
                ) {
                    Text("Cancel")
                }
            },
            onDismissRequest = {
                connectionMethodPickerData.value!!.continuation.resume(null) { _, _, _ -> null }
                connectionMethodPickerData.value = null
            },
            confirmButton = @Composable {
                TextButton(
                    onClick = {
                        connectionMethodPickerData.value!!.continuation.resume(selectedOption) { _, _, _ -> null }
                        connectionMethodPickerData.value = null
                    }
                ) {
                    Text("Connect")
                }
            }
        )
    }

    if (readerShowQrScanner.value) {
        ScanQrCodeDialog(
            title = { Text(text = "Scan QR code") },
            text = { Text(text = "Scan this QR code on another device") },
            dismissButton = "Close",
            onCodeScanned = { data ->
                if (data.startsWith("mdoc:")) {
                    readerShowQrScanner.value = false
                    readerJob = coroutineScope.launch() {
                        try {
                            doReaderFlow(
                                app = app,
                                encodedDeviceEngagement = ByteString(data.substring(5).fromBase64Url()),
                                existingTransport = null,
                                handover = Simple.NULL,
                                updateNfcDialogMessage = null,
                                allowMultipleRequests = app.settingsModel.readerAllowMultipleRequests.value,
                                bleUseL2CAP = app.settingsModel.readerBleL2CapEnabled.value,
                                showToast = showToast,
                                readerTransport = readerTransport,
                                readerSessionEncryption = readerSessionEncryption,
                                readerSessionTranscript = readerSessionTranscript,
                                readerMostRecentDeviceResponse = readerMostRecentDeviceResponse,
                                eReaderKey = eReaderKey,
                                selectedRequest = selectedRequest,
                                selectConnectionMethod = { connectionMethods ->
                                    if (app.settingsModel.readerAutomaticallySelectTransport.value) {
                                        showToast("Auto-selected first from $connectionMethods")
                                        connectionMethods[0]
                                    } else {
                                        selectConnectionMethod(
                                            connectionMethods,
                                            connectionMethodPickerData
                                        )
                                    }
                                }
                            )
                        } catch (error: Throwable) {
                            Logger.e(TAG, "Caught exception", error)
                            error.printStackTrace()
                            showToast("Error: ${error.message}")
                        }
                        readerJob = null
                    }
                    true
                } else {
                    false
                }
            },
            onDismiss = { readerShowQrScanner.value = false }
        )
    }

    val readerTransportState = readerTransport.value?.state?.collectAsState()

    if (!blePermissionState.isGranted) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        blePermissionState.launchPermissionRequest()
                    }
                }
            ) {
                Text("Request BLE permissions")
            }
        }
    } else {
        if (readerJob != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.weight(1.0f),
                    verticalArrangement = Arrangement.Top,
                ) {
                    ShowReaderResults(app, readerMostRecentDeviceResponse, readerSessionTranscript, eReaderKey.value!!)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Connection State: ${readerTransportState?.value}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val encodedDeviceRequest =
                                        TestAppUtils.generateEncodedDeviceRequest(
                                            request = selectedRequest.value.sampleRequest,
                                            encodedSessionTranscript = readerSessionTranscript.value!!,
                                            readerKey = app.readerKey,
                                            readerCert = app.readerCert,
                                            readerRootCert = app.readerRootCert,
                                        )
                                    readerMostRecentDeviceResponse.value = byteArrayOf()
                                    readerTransport.value!!.sendMessage(
                                        readerSessionEncryption.value!!.encryptMessage(
                                            messagePlaintext = encodedDeviceRequest,
                                            statusCode = null
                                        )
                                    )
                                } catch (error: Throwable) {
                                    Logger.e(TAG, "Caught exception", error)
                                    error.printStackTrace()
                                    showToast("Error: ${error.message}")
                                }
                            }
                        }
                    ) {
                        Text("Send Another Request")
                    }
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    readerTransport.value!!.sendMessage(
                                        SessionEncryption.encodeStatus(Constants.SESSION_DATA_STATUS_SESSION_TERMINATION)
                                    )
                                    readerTransport.value!!.close()
                                } catch (error: Throwable) {
                                    Logger.e(TAG, "Caught exception", error)
                                    error.printStackTrace()
                                    showToast("Error: ${error.message}")
                                }
                            }
                        }
                    ) {
                        Text("Close (Message)")
                    }
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    readerTransport.value!!.sendMessage(byteArrayOf())
                                    readerTransport.value!!.close()
                                } catch (error: Throwable) {
                                    Logger.e(TAG, "Caught exception", error)
                                    error.printStackTrace()
                                    showToast("Error: ${error.message}")
                                }
                            }
                        }
                    ) {
                        Text("Close (Transport-Specific)")
                    }
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    readerTransport.value!!.close()
                                } catch (error: Throwable) {
                                    Logger.e(TAG, "Caught exception", error)
                                    error.printStackTrace()
                                    showToast("Error: ${error.message}")
                                }
                            }
                        }
                    ) {
                        Text("Close (None)")
                    }
                }
            }
        } else if (readerMostRecentDeviceResponse.value != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.weight(1.0f),
                    verticalArrangement = Arrangement.Top,
                ) {
                    ShowReaderResults(app, readerMostRecentDeviceResponse, readerSessionTranscript, eReaderKey.value!!)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        onClick = { readerMostRecentDeviceResponse.value = null }
                    ) {
                        Text("Close")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(8.dp)
            ) {
                item {
                    RequestPicker(
                        availableRequests,
                        selectedRequest,
                        dropdownExpanded,
                        onRequestSelected = {}
                    )
                }
                item {
                    TextButton(
                        onClick = {
                            readerShowQrScanner.value = true
                            readerMostRecentDeviceResponse.value = null
                        },
                        content = { Text("Request mdoc via QR Code") }
                    )
                }
                item {
                    TextButton(
                        onClick = {
                            readerMostRecentDeviceResponse.value = null

                            coroutineScope.launch {
                                try {
                                    val negotiatedHandoverConnectionMethods = mutableListOf<MdocConnectionMethod>()
                                    val bleUuid = UUID.randomUUID()
                                    if (app.settingsModel.readerBleCentralClientModeEnabled.value) {
                                        negotiatedHandoverConnectionMethods.add(
                                            MdocConnectionMethodBle(
                                                supportsPeripheralServerMode = false,
                                                supportsCentralClientMode = true,
                                                peripheralServerModeUuid = null,
                                                centralClientModeUuid = bleUuid,
                                            )
                                        )
                                    }
                                    if (app.settingsModel.readerBlePeripheralServerModeEnabled.value) {
                                        negotiatedHandoverConnectionMethods.add(
                                            MdocConnectionMethodBle(
                                                supportsPeripheralServerMode = true,
                                                supportsCentralClientMode = false,
                                                peripheralServerModeUuid = bleUuid,
                                                centralClientModeUuid = null,
                                            )
                                        )
                                    }
                                    if (app.settingsModel.readerNfcDataTransferEnabled.value) {
                                        negotiatedHandoverConnectionMethods.add(
                                            MdocConnectionMethodNfc(
                                                commandDataFieldMaxLength = 0xffff,
                                                responseDataFieldMaxLength = 0x10000
                                            )
                                        )
                                    }
                                    scanNfcMdocReader(
                                        message = "Hold near credential holder's phone.",
                                        options = MdocTransportOptions(
                                            bleUseL2CAP = app.settingsModel.readerBleL2CapEnabled.value
                                        ),
                                        selectConnectionMethod = { connectionMethods ->
                                            if (app.settingsModel.readerAutomaticallySelectTransport.value) {
                                                showToast("Auto-selected first from $connectionMethods")
                                                connectionMethods[0]
                                            } else {
                                                selectConnectionMethod(
                                                    connectionMethods,
                                                    connectionMethodPickerData
                                                )
                                            }
                                        },
                                        negotiatedHandoverConnectionMethods = negotiatedHandoverConnectionMethods,
                                        onHandover = { transport, encodedDeviceEngagement, handover, updateMessage ->
                                            doReaderFlow(
                                                app = app,
                                                encodedDeviceEngagement = encodedDeviceEngagement,
                                                existingTransport = transport,
                                                handover = handover,
                                                updateNfcDialogMessage = updateMessage,
                                                allowMultipleRequests = app.settingsModel.readerAllowMultipleRequests.value,
                                                bleUseL2CAP = app.settingsModel.readerBleL2CapEnabled.value,
                                                showToast = showToast,
                                                readerTransport = readerTransport,
                                                readerSessionEncryption = readerSessionEncryption,
                                                readerSessionTranscript = readerSessionTranscript,
                                                readerMostRecentDeviceResponse = readerMostRecentDeviceResponse,
                                                eReaderKey = eReaderKey,
                                                selectedRequest = selectedRequest,
                                                selectConnectionMethod = { connectionMethods ->
                                                    if (app.settingsModel.readerAutomaticallySelectTransport.value) {
                                                        showToast("Auto-selected first from $connectionMethods")
                                                        connectionMethods[0]
                                                    } else {
                                                        selectConnectionMethod(
                                                            connectionMethods,
                                                            connectionMethodPickerData
                                                        )
                                                    }
                                                }
                                            )
                                            readerJob = null
                                        }
                                    )
                                } catch (e: Throwable) {
                                    Logger.e(TAG, "NFC engagement failed", e)
                                    e.printStackTrace()
                                    showToast("NFC engagement failed with $e")
                                }
                            }
                        },
                        content = { Text("Request mdoc via NFC") }
                    )
                }
            }
        }
    }
}

private suspend fun doReaderFlow(
    app: App,
    encodedDeviceEngagement: ByteString,
    existingTransport: MdocTransport?,
    handover: DataItem,
    updateNfcDialogMessage: ((message: String) -> Unit)?,
    allowMultipleRequests: Boolean,
    bleUseL2CAP: Boolean,
    showToast: (message: String) -> Unit,
    readerTransport: MutableState<MdocTransport?>,
    readerSessionEncryption: MutableState<SessionEncryption?>,
    readerSessionTranscript: MutableState<ByteArray?>,
    readerMostRecentDeviceResponse: MutableState<ByteArray?>,
    eReaderKey: MutableState<EcPrivateKey?>,
    selectedRequest: MutableState<RequestPickerEntry>,
    selectConnectionMethod: suspend (connectionMethods: List<MdocConnectionMethod>) -> MdocConnectionMethod?
) {
    val deviceEngagement = EngagementParser(encodedDeviceEngagement.toByteArray()).parse()
    val eDeviceKey = deviceEngagement.eSenderKey
    Logger.i(TAG, "Using curve ${eDeviceKey.curve.name} for session encryption")
    eReaderKey.value = Crypto.createEcPrivateKey(eDeviceKey.curve)

    val transport = if (existingTransport != null) {
        existingTransport
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
                        app = app,
                        transport = transport,
                        encodedDeviceEngagement = encodedDeviceEngagement,
                        handover = handover,
                        updateNfcDialogMessage = updateNfcDialogMessage,
                        allowMultipleRequests = allowMultipleRequests,
                        bleUseL2CAP = bleUseL2CAP,
                        showToast = showToast,
                        readerTransport = readerTransport,
                        readerSessionEncryption = readerSessionEncryption,
                        readerSessionTranscript = readerSessionTranscript,
                        readerMostRecentDeviceResponse = readerMostRecentDeviceResponse,
                        selectedRequest = selectedRequest,
                        eDeviceKey = eDeviceKey,
                        eReaderKey = eReaderKey.value!!,
                    )
                }
            )
            return
        }
        transport
    }
    doReaderFlowWithTransport(
        app = app,
        transport = transport,
        encodedDeviceEngagement = encodedDeviceEngagement,
        handover = handover,
        updateNfcDialogMessage = updateNfcDialogMessage,
        allowMultipleRequests = allowMultipleRequests,
        bleUseL2CAP = bleUseL2CAP,
        showToast = showToast,
        readerTransport = readerTransport,
        readerSessionEncryption = readerSessionEncryption,
        readerSessionTranscript = readerSessionTranscript,
        readerMostRecentDeviceResponse = readerMostRecentDeviceResponse,
        selectedRequest = selectedRequest,
        eDeviceKey = eDeviceKey,
        eReaderKey = eReaderKey.value!!,
    )
}

private suspend fun doReaderFlowWithTransport(
    app: App,
    transport: MdocTransport,
    encodedDeviceEngagement: ByteString,
    handover: DataItem,
    updateNfcDialogMessage: ((message: String) -> Unit)?,
    allowMultipleRequests: Boolean,
    bleUseL2CAP: Boolean, // TODO: unused param. WIP or Remove?
    showToast: (message: String) -> Unit,
    readerTransport: MutableState<MdocTransport?>,
    readerSessionEncryption: MutableState<SessionEncryption?>,
    readerSessionTranscript: MutableState<ByteArray?>,
    readerMostRecentDeviceResponse: MutableState<ByteArray?>,
    selectedRequest: MutableState<RequestPickerEntry>,
    eDeviceKey: EcPublicKey,
    eReaderKey: EcPrivateKey,
) {
    if (updateNfcDialogMessage != null) {
        updateNfcDialogMessage("Transferring data, don't move your phone")
    }
    readerTransport.value = transport
    val encodedSessionTranscript = TestAppUtils.generateEncodedSessionTranscript(
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
    readerSessionEncryption.value = sessionEncryption
    readerSessionTranscript.value = encodedSessionTranscript
    val encodedDeviceRequest = TestAppUtils.generateEncodedDeviceRequest(
        request = selectedRequest.value.sampleRequest,
        encodedSessionTranscript = readerSessionTranscript.value!!,
        readerKey = app.readerKey,
        readerCert = app.readerCert,
        readerRootCert = app.readerRootCert,
        zkSystemRepository = app.zkSystemRepository,
    )
    try {
        transport.open(eDeviceKey)
        transport.sendMessage(
            sessionEncryption.encryptMessage(
                messagePlaintext = encodedDeviceRequest,
                statusCode = null
            )
        )
        while (true) {
            val sessionData = transport.waitForMessage()
            if (sessionData.isEmpty()) {
                showToast("Received transport-specific session termination message from holder")
                transport.close()
                break
            }

            val (message, status) = sessionEncryption.decryptMessage(sessionData)
            Logger.i(TAG, "Holder sent ${message?.size} bytes status $status")
            if (message != null) {
                readerMostRecentDeviceResponse.value = message
            }
            if (status == Constants.SESSION_DATA_STATUS_SESSION_TERMINATION) {
                showToast("Received session termination message from holder")
                Logger.i(TAG, "Holder indicated they closed the connection. " +
                        "Closing and ending reader loop")
                transport.close()
                break
            }
            if (!allowMultipleRequests) {
                showToast("Response received, closing connection")
                Logger.i(TAG, "Holder did not indicate they are closing the connection. " +
                        "Auto-close is enabled, so sending termination message, closing, and " +
                        "ending reader loop")
                transport.sendMessage(SessionEncryption.encodeStatus(Constants.SESSION_DATA_STATUS_SESSION_TERMINATION))
                transport.close()
                break
            }
            showToast("Response received, keeping connection open")
            Logger.i(TAG, "Holder did not indicate they are closing the connection. " +
                    "Auto-close is not enabled so waiting for message from holder")
            // "Send additional request" and close buttons will act further on `transport`
        }
    } catch (_: MdocTransportClosedException) {
        // Nothing to do, this is thrown when transport.close() is called from another coroutine, that
        // is, the onClick handlers for the close buttons.
        Logger.i(TAG, "Ending reader flow due to MdocTransportClosedException")
    } finally {
        if (updateNfcDialogMessage != null) {
            updateNfcDialogMessage("Transfer complete")
        }
        transport.close()
        readerTransport.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestPicker(
    availableRequests: List<RequestPickerEntry>,
    comboBoxSelected: MutableState<RequestPickerEntry>,
    comboBoxExpanded: MutableState<Boolean>,
    onRequestSelected: (request: RequestPickerEntry) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                modifier = Modifier.padding(end = 16.dp),
                text = "mDL Data Elements to Request"
            )

            ExposedDropdownMenuBox(
                expanded = comboBoxExpanded.value,
                onExpandedChange = {
                    comboBoxExpanded.value = !comboBoxExpanded.value
                }
            ) {
                TextField(
                    value = comboBoxSelected.value.displayName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = comboBoxExpanded.value) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )

                ExposedDropdownMenu(
                    expanded = comboBoxExpanded.value,
                    onDismissRequest = { comboBoxExpanded.value = false }
                ) {
                    availableRequests.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(text = item.displayName) },
                            onClick = {
                                comboBoxSelected.value = item
                                comboBoxExpanded.value = false
                                onRequestSelected(comboBoxSelected.value)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowReaderResults(
    app: App,
    readerMostRecentDeviceResponse: MutableState<ByteArray?>,
    readerSessionTranscript: MutableState<ByteArray?>,
    eReaderKey: EcPrivateKey
) {
    val coroutineScope = rememberCoroutineScope()

    val deviceResponse1 = readerMostRecentDeviceResponse.value
    if (deviceResponse1 == null || deviceResponse1.isEmpty()) {
        Text(
            text = "Waiting for data",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
    } else {
        val documentData = remember { mutableStateOf<DocumentData?>(null) }

        documentData.value?.let {
            ShowDocumentData(it)
        }

        LaunchedEffect(Unit) {
            coroutineScope.launch {
                val parser = DeviceResponseParser(
                    encodedDeviceResponse = deviceResponse1,
                    encodedSessionTranscript = readerSessionTranscript.value!!,
                )
                parser.setEphemeralReaderKey(eReaderKey)
                val deviceResponse2 = parser.parse()
                // TODO: support showing multiple documents
                if (deviceResponse2.documents.isNotEmpty()) {
                    documentData.value = DocumentData.fromMdocDeviceResponseDocument(
                        document = deviceResponse2.documents[0],
                        documentTypeRepository = app.documentTypeRepository,
                        issuerTrustManager = app.issuerTrustManager
                    )
                } else if (deviceResponse2.zkDocuments.isNotEmpty()) {
                    documentData.value = DocumentData.fromZkMdocDeviceResponseDocument(
                        zkDocument = deviceResponse2.zkDocuments[0],
                        encodedSessionTranscript = readerSessionTranscript.value!!,
                        zkSystemRepository = app.zkSystemRepository,
                        issuerTrustManager = app.issuerTrustManager
                    )
                } else {
                    documentData.value = DocumentData(
                        infoTexts = listOf("No documents in response"),
                        warningTexts = emptyList(),
                        kvPairs = emptyList()
                    )
                }
            }
        }
    }
}

@Composable
private fun ShowKeyValuePair(kvPair: DocumentKeyValuePair) {
    Column(
        Modifier
            .padding(8.dp)
            .fillMaxWidth()) {
        Text(
            text = kvPair.key,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = kvPair.textValue,
            style = MaterialTheme.typography.bodyMedium
        )
        if (kvPair.bitmap != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    bitmap = kvPair.bitmap,
                    modifier = Modifier.size(200.dp),
                    contentDescription = null
                )
            }

        }
    }
}

@Composable
private fun ShowDocumentData(documentData: DocumentData) {
    Column(
        Modifier
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
    ) {

        for (text in documentData.infoTexts) {
            InfoCard {
                Text(text)
            }
        }
        for (text in documentData.warningTexts) {
            WarningCard {
                Text(text)
            }
        }

        for (kvPair in documentData.kvPairs) {
            ShowKeyValuePair(kvPair)
        }
    }
}

// TODO:
//  - add infos/warnings according to TrustManager (need to port TrustManager to KMP), that is
//    add a warning if the issuer isn't well-known.
//  - move to identity-models
//  - add fromSdJwtVcResponse()
private data class DocumentData(
    val infoTexts: List<String>,
    val warningTexts: List<String>,
    val kvPairs: List<DocumentKeyValuePair>
) {
    companion object {

        suspend fun fromZkMdocDeviceResponseDocument(
            zkDocument: ZkDocument,
            encodedSessionTranscript: ByteArray,
            zkSystemRepository: ZkSystemRepository,
            issuerTrustManager: TrustManager
        ): DocumentData {
            val infos = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            val kvPairs = mutableListOf<DocumentKeyValuePair>()

            if (zkDocument.zkDocumentData.msoX5chain == null) {
                warnings.add("No msoX5chain in ZkDocumentData")
            } else {
                val trustResult = issuerTrustManager.verify(zkDocument.zkDocumentData.msoX5chain!!.certificates)
                if (trustResult.isTrusted) {
                    if (trustResult.trustPoints[0].metadata.displayName != null) {
                        infos.add("Issuer '${trustResult.trustPoints[0].metadata.displayName}' is in a trust list")
                    } else {
                        infos.add(
                            "Issuer with name '${trustResult.trustPoints[0].certificate.subject.name}' " +
                                    "is in a trust list"
                        )
                    }
                } else {
                    warnings.add("Issuer is not in trust list")
                }
            }

            try {
                val zkSystemSpec = zkSystemRepository.getAllZkSystemSpecs().find {
                    it.id == zkDocument.zkDocumentData.zkSystemSpecId
                }
                if (zkSystemSpec == null) {
                    throw IllegalArgumentException("ZK System Spec ID ${zkDocument.zkDocumentData.zkSystemSpecId} was not found.")
                }

                zkSystemRepository.lookup(zkSystemSpec.system)
                    ?.verifyProof(zkDocument, zkSystemSpec, ByteString(encodedSessionTranscript))
                    ?: throw IllegalStateException("Zk System '${zkSystemSpec.system}' was not found.")
                infos.add("ZK verification succeeded.")
            } catch (e: Throwable) {
                warnings.add("ZK verification failed with error ${e.message}.")
            }

            zkDocument.zkDocumentData.issuerSigned.forEach { (nameSpaceName, dataElements) ->
                kvPairs.add(DocumentKeyValuePair("Namespace", nameSpaceName))
                for ((dataElementName, dataElementValue) in dataElements) {
                    val prettyPrintedValue = Cbor.toDiagnostics(
                        dataElementValue, setOf(
                            DiagnosticOption.PRETTY_PRINT,
                            DiagnosticOption.EMBEDDED_CBOR,
                            DiagnosticOption.BSTR_PRINT_LENGTH,
                        )
                    )
                    kvPairs.add(DocumentKeyValuePair(dataElementName, prettyPrintedValue))
                }
            }
            // TODO: also iterate over DeviceSigned items

            return DocumentData(infos, warnings, kvPairs)
        }

        suspend fun fromMdocDeviceResponseDocument(
            document: DeviceResponseParser.Document,
            documentTypeRepository: DocumentTypeRepository,
            issuerTrustManager: TrustManager
        ): DocumentData {
            val infos = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            val kvPairs = mutableListOf<DocumentKeyValuePair>()

            if (document.issuerSignedAuthenticated) {
                val trustResult = issuerTrustManager.verify(document.issuerCertificateChain.certificates)
                if (trustResult.isTrusted) {
                    if (trustResult.trustPoints[0].metadata.displayName != null) {
                        infos.add("Issuer '${trustResult.trustPoints[0].metadata.displayName}' is in a trust list")
                    } else {
                        infos.add("Issuer with name '${trustResult.trustPoints[0].certificate.subject.name}' " +
                                "is in a trust list")
                    }
                } else {
                    warnings.add("Issuer is not in trust list")
                }
            }
            if (!document.deviceSignedAuthenticated) {
                warnings.add("Device Authentication failed")
            }
            if (!document.issuerSignedAuthenticated) {
                warnings.add("Issuer Authentication failed")
            }
            if (document.numIssuerEntryDigestMatchFailures > 0) {
                warnings.add("One or more issuer provided data elements failed to authenticate")
            }
            val now = Clock.System.now()
            if (now < document.validityInfoValidFrom || now > document.validityInfoValidUntil) {
                warnings.add("Document information is not valid at this point in time.")
            }

            kvPairs.add(DocumentKeyValuePair("Type", "ISO mdoc (ISO/IEC 18013-5:2021)"))
            kvPairs.add(DocumentKeyValuePair("DocType", document.docType))
            kvPairs.add(DocumentKeyValuePair("Valid From", formatTime(document.validityInfoValidFrom)))
            kvPairs.add(DocumentKeyValuePair("Valid Until", formatTime(document.validityInfoValidUntil)))
            kvPairs.add(DocumentKeyValuePair("Signed At", formatTime(document.validityInfoSigned)))
            kvPairs.add(DocumentKeyValuePair(
                "Expected Update",
                document.validityInfoExpectedUpdate?.let { formatTime(it) } ?: "Not Set"
            ))

            val mdocType = documentTypeRepository.getDocumentTypeForMdoc(document.docType)?.mdocDocumentType

            // TODO: Handle DeviceSigned data
            for (namespaceName in document.issuerNamespaces) {
                val mdocNamespace = if (mdocType !=null) {
                    mdocType.namespaces.get(namespaceName)
                } else {
                    // Some DocTypes not known by [documentTypeRepository] - could be they are
                    // private or was just never added - may use namespaces from existing
                    // DocTypes... support that as well.
                    //
                    documentTypeRepository.getDocumentTypeForMdocNamespace(namespaceName)
                        ?.mdocDocumentType?.namespaces?.get(namespaceName)
                }

                kvPairs.add(DocumentKeyValuePair("Namespace", namespaceName))
                for (dataElementName in document.getIssuerEntryNames(namespaceName)) {
                    val mdocDataElement = mdocNamespace?.dataElements?.get(dataElementName)
                    val encodedDataElementValue = document.getIssuerEntryData(namespaceName, dataElementName)
                    val dataElement = Cbor.decode(encodedDataElementValue)
                    var bitmap: ImageBitmap? = null
                    val (key, value) = if (mdocDataElement != null) {
                        if (dataElement is Bstr && mdocDataElement.attribute.type == DocumentAttributeType.Picture) {
                            try {
                                bitmap = decodeImage(dataElement.value)
                            } catch (e: Throwable) {
                                Logger.w(TAG, "Error decoding image for data element $dataElement in " +
                                        "namespace $namespaceName", e)
                            }
                        }
                        Pair(
                            mdocDataElement.attribute.displayName,
                            mdocDataElement.renderValue(dataElement)
                        )
                    } else {
                        Pair(
                            dataElementName,
                            Cbor.toDiagnostics(
                                dataElement, setOf(
                                    DiagnosticOption.PRETTY_PRINT,
                                    DiagnosticOption.EMBEDDED_CBOR,
                                    DiagnosticOption.BSTR_PRINT_LENGTH,
                                )
                            )
                        )
                    }
                    kvPairs.add(DocumentKeyValuePair(key, value, bitmap = bitmap))
                }
            }
            return DocumentData(infos, warnings, kvPairs)
        }
    }
}

private data class DocumentKeyValuePair(
    val key: String,
    val textValue: String,
    val bitmap: ImageBitmap? = null
)

private fun formatTime(instant: Instant): String {
    val tz = TimeZone.currentSystemDefault()
    val isoStr = instant.toLocalDateTime(tz).format(LocalDateTime.Formats.ISO)
    // Get rid of the middle 'T'
    return isoStr.substring(0, 10) + " " + isoStr.substring(11)
}


