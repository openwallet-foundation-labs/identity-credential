package com.android.identity.testapp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.identity.appsupport.ui.consent.ConsentDocument
import com.android.identity.appsupport.ui.consent.ConsentModalBottomSheet
import com.android.identity.appsupport.ui.consent.ConsentRelyingParty
import com.android.identity.appsupport.ui.consent.MdocConsentField
import com.android.identity.appsupport.ui.permissions.rememberBluetoothPermissionState
import com.android.identity.appsupport.ui.qrcode.ShowQrCodeDialog
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.documenttype.knowntypes.DrivingLicense
import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import com.android.identity.mdoc.connectionmethod.ConnectionMethodBle
import com.android.identity.mdoc.engagement.EngagementGenerator
import com.android.identity.mdoc.request.DeviceRequestParser
import com.android.identity.mdoc.response.DeviceResponseGenerator
import com.android.identity.mdoc.sessionencryption.SessionEncryption
import com.android.identity.mdoc.transport.MdocTransport
import com.android.identity.mdoc.transport.MdocTransportClosedException
import com.android.identity.mdoc.transport.MdocTransportFactory
import com.android.identity.mdoc.transport.MdocTransportOptions
import com.android.identity.testapp.TestAppUtils
import com.android.identity.util.Constants
import com.android.identity.util.Logger
import com.android.identity.util.UUID
import com.android.identity.util.toBase64Url
import identitycredential.samples.testapp.generated.resources.Res
import identitycredential.samples.testapp.generated.resources.driving_license_card_art
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment

private const val TAG = "IsoMdocProximitySharingScreen"

private data class ConsentSheetData(
    val showConsentPrompt: Boolean,
    val continuation:  CancellableContinuation<Boolean>,
    val document: ConsentDocument,
    val consentFields: List<MdocConsentField>,
    val relyingParty: ConsentRelyingParty,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
@Composable
fun IsoMdocProximitySharingScreen(
    showToast: (message: String) -> Unit,
) {
    val blePermissionState = rememberBluetoothPermissionState()

    val coroutineScope = rememberCoroutineScope()

    var holderAutoCloseConnection = remember { mutableStateOf(true) }
    var holderJob by remember { mutableStateOf<Job?>(null) }
    var holderTransport = remember { mutableStateOf<MdocTransport?>(null) }
    var holderEncodedDeviceEngagement = remember { mutableStateOf<ByteArray?>(null) }

    val holderConsentSheetData = remember { mutableStateOf<ConsentSheetData?>(null)}

    if (holderConsentSheetData.value != null && holderConsentSheetData.value!!.showConsentPrompt) {
        // TODO: use sheetGesturesEnabled=false when available - see
        //  https://issuetracker.google.com/issues/288211587 for details
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )
        ConsentModalBottomSheet(
            sheetState = sheetState,
            consentFields = holderConsentSheetData.value!!.consentFields,
            document = holderConsentSheetData.value!!.document,
            relyingParty = holderConsentSheetData.value!!.relyingParty,
            onConfirm = {
                coroutineScope.launch {
                    sheetState.hide()
                    holderConsentSheetData.value!!.continuation.resume(true)
                    holderConsentSheetData.value = null
                }
            },
            onCancel = {
                coroutineScope.launch {
                    sheetState.hide()
                    holderConsentSheetData.value!!.continuation.resume(false)
                    holderConsentSheetData.value = null
                }
            }
        )

    }

    val holderTransportState = holderTransport.value?.state?.collectAsState()

    val holderWaitingForRemotePeer = when (holderTransportState?.value) {
        MdocTransport.State.IDLE,
        MdocTransport.State.ADVERTISING,
        MdocTransport.State.SCANNING -> {
            true
        }
        else -> {
            false
        }
    }
    Logger.i(TAG, "holderTransportState=$holderTransportState holderWaitingForRemotePeer=$holderWaitingForRemotePeer")
    if (holderTransport != null && holderWaitingForRemotePeer) {
        val deviceEngagementQrCode = "mdoc:" + holderEncodedDeviceEngagement.value!!.toBase64Url()
        ShowQrCodeDialog(
            title = { Text(text = "Scan QR code") },
            text = { Text(text = "Scan this QR code on another device") },
            additionalContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = holderAutoCloseConnection.value,
                        onCheckedChange = { holderAutoCloseConnection.value = it }
                    )
                    Text(text = "Close transport after first response")
                }
            },
            dismissButton = "Close",
            data = deviceEngagementQrCode,
            onDismiss = {
                holderJob?.cancel()
            }
        )
    }

    if (!blePermissionState.isGranted) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { blePermissionState.launchPermissionRequest() }
            ) {
                Text("Request BLE permissions")
            }
        }
    } else {
        if (holderTransport.value != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Connection State: ${holderTransportState?.value}",
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
                                    holderTransport.value!!.sendMessage(
                                        SessionEncryption.encodeStatus(Constants.SESSION_DATA_STATUS_SESSION_TERMINATION)
                                    )
                                    holderTransport.value!!.close()
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
                                    holderTransport.value!!.sendMessage(byteArrayOf())
                                    holderTransport.value!!.close()
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
                            try {
                                coroutineScope.launch {
                                    holderTransport.value!!.close()
                                }
                            } catch (error: Throwable) {
                                Logger.e(TAG, "Caught exception", error)
                                error.printStackTrace()
                                showToast("Error: ${error.message}")
                            }
                        }
                    ) {
                        Text("Close (None)")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(8.dp)
            ) {
                item {
                    TextButton(
                        onClick = {
                            holderJob = coroutineScope.launch() {
                                doHolderFlow(
                                    connectionMethod = ConnectionMethodBle(
                                        supportsPeripheralServerMode = false,
                                        supportsCentralClientMode = true,
                                        peripheralServerModeUuid = null,
                                        centralClientModeUuid = UUID.randomUUID(),
                                    ),
                                    options = MdocTransportOptions(),
                                    autoCloseConnection = holderAutoCloseConnection,
                                    showToast = showToast,
                                    holderTransport = holderTransport,
                                    encodedDeviceEngagement = holderEncodedDeviceEngagement,
                                    holderConsentSheetData = holderConsentSheetData,
                                )
                            }
                        },
                        content = { Text("Share via QR (mdoc central client mode)") }
                    )
                }
                item {
                    TextButton(
                        onClick = {
                            holderJob = coroutineScope.launch() {
                                doHolderFlow(
                                    connectionMethod = ConnectionMethodBle(
                                        supportsPeripheralServerMode = true,
                                        supportsCentralClientMode = false,
                                        peripheralServerModeUuid = UUID.randomUUID(),
                                        centralClientModeUuid = null,
                                    ),
                                    options = MdocTransportOptions(),
                                    autoCloseConnection = holderAutoCloseConnection,
                                    showToast = showToast,
                                    holderTransport = holderTransport,
                                    encodedDeviceEngagement = holderEncodedDeviceEngagement,
                                    holderConsentSheetData = holderConsentSheetData,
                                )
                            }
                        },
                        content = { Text("Share via QR (mdoc peripheral server mode)") }
                    )
                }
                item {
                    TextButton(
                        onClick = {
                            holderJob = coroutineScope.launch() {
                                doHolderFlow(
                                    connectionMethod = ConnectionMethodBle(
                                        supportsPeripheralServerMode = false,
                                        supportsCentralClientMode = true,
                                        peripheralServerModeUuid = null,
                                        centralClientModeUuid = UUID.randomUUID(),
                                    ),
                                    options = MdocTransportOptions(bleUseL2CAP = true),
                                    autoCloseConnection = holderAutoCloseConnection,
                                    showToast = showToast,
                                    holderTransport = holderTransport,
                                    encodedDeviceEngagement = holderEncodedDeviceEngagement,
                                    holderConsentSheetData = holderConsentSheetData,
                                )
                            }
                        },
                        content = { Text("Share via QR (mdoc central client mode w/ L2CAP)") }
                    )
                }
                item {
                    TextButton(
                        onClick = {
                            holderJob = coroutineScope.launch() {
                                doHolderFlow(
                                    connectionMethod = ConnectionMethodBle(
                                        supportsPeripheralServerMode = true,
                                        supportsCentralClientMode = false,
                                        peripheralServerModeUuid = UUID.randomUUID(),
                                        centralClientModeUuid = null,
                                    ),
                                    options = MdocTransportOptions(bleUseL2CAP = true),
                                    autoCloseConnection = holderAutoCloseConnection,
                                    showToast = showToast,
                                    holderTransport = holderTransport,
                                    encodedDeviceEngagement = holderEncodedDeviceEngagement,
                                    holderConsentSheetData = holderConsentSheetData,
                                )
                            }
                        },
                        content = { Text("Share via QR (mdoc peripheral server mode w/ L2CAP)") }
                    )
                }
            }
        }
    }
}

private suspend fun doHolderFlow(
    connectionMethod: ConnectionMethod,
    options: MdocTransportOptions,
    autoCloseConnection: MutableState<Boolean>,
    showToast: (message: String) -> Unit,
    holderTransport:  MutableState<MdocTransport?>,
    encodedDeviceEngagement: MutableState<ByteArray?>,
    holderConsentSheetData: MutableState<ConsentSheetData?>,
) {
    val transport = MdocTransportFactory.createTransport(
        connectionMethod,
        MdocTransport.Role.MDOC,
        options
    )
    holderTransport.value = transport
    val eDeviceKey = Crypto.createEcPrivateKey(EcCurve.P256)
    val engagementGenerator = EngagementGenerator(
        eSenderKey = eDeviceKey.publicKey,
        version = "1.0"
    )
    engagementGenerator.addConnectionMethods(listOf(transport!!.connectionMethod))
    encodedDeviceEngagement.value = engagementGenerator.generate()
    try {
        transport.open(eDeviceKey.publicKey)

        var sessionEncryption: SessionEncryption? = null
        var encodedSessionTranscript: ByteArray? = null
        while (true) {
            Logger.i(TAG, "Waiting for message from reader...")
            val sessionData = transport!!.waitForMessage()
            if (sessionData.isEmpty()) {
                showToast("Received transport-specific session termination message from reader")
                transport.close()
                break
            }

            if (sessionEncryption == null) {
                val eReaderKey = SessionEncryption.getEReaderKey(sessionData)
                encodedSessionTranscript = TestAppUtils.generateEncodedSessionTranscript(
                    encodedDeviceEngagement.value!!,
                    eReaderKey
                )
                sessionEncryption = SessionEncryption(
                    SessionEncryption.Role.MDOC,
                    eDeviceKey,
                    eReaderKey,
                    encodedSessionTranscript,
                )
            }
            val (encodedDeviceRequest, status) = sessionEncryption.decryptMessage(sessionData)

            if (status == Constants.SESSION_DATA_STATUS_SESSION_TERMINATION) {
                showToast("Received session termination message from reader")
                transport.close()
                break
            }

            val consentFields = showConsentPrompt(
                encodedDeviceRequest!!,
                encodedSessionTranscript!!,
                holderConsentSheetData
            )

            val encodedDeviceResponse = if (consentFields == null) {
                DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK).generate()
            } else {
                TestAppUtils.generateEncodedDeviceResponse(
                    consentFields,
                    encodedSessionTranscript
                )
            }
            transport.sendMessage(
                sessionEncryption.encryptMessage(
                    encodedDeviceResponse,
                    if (autoCloseConnection.value) {
                        Constants.SESSION_DATA_STATUS_SESSION_TERMINATION
                    } else {
                        null
                    }
                )
            )
            if (autoCloseConnection.value) {
                showToast("Response sent, autoclosing connection")
                transport.close()
                break
            } else {
                showToast("Response sent, keeping connection open")
            }
        }
    } catch (_: MdocTransportClosedException) {
        // Nothing to do, this is thrown when transport.close() is called from another coroutine, that
        // is, the onClick handlers for the close buttons.
        Logger.i(TAG, "Ending holderJob due to MdocTransportClosedException")
    } catch (error: Throwable) {
        Logger.e(TAG, "Caught exception", error)
        error.printStackTrace()
        showToast("Error: ${error.message}")
    } finally {
        transport.close()
        holderTransport.value = null
        encodedDeviceEngagement.value = null
    }
}

// Returns consent-fields if confirmed by the user, false otherwise.
//
// Throws if there is no mDL docrequest.
//
@OptIn(ExperimentalResourceApi::class)
private suspend fun showConsentPrompt(
    encodedDeviceRequest: ByteArray,
    encodedSessionTranscript: ByteArray,
    holderConsentSheetData: MutableState<ConsentSheetData?>,
): List<MdocConsentField>? {
    val deviceRequest = DeviceRequestParser(
        encodedDeviceRequest,
        encodedSessionTranscript,
    ).parse()
    for (docRequest in deviceRequest.docRequests) {
        Logger.i(TAG, "docRequest.readerAuthenticated=${docRequest.readerAuthenticated}")
        val trustPoint = if (docRequest.readerAuthenticated) {
            val trustResult = TestAppUtils.readerTrustManager.verify(docRequest.readerCertificateChain!!.certificates)
            Logger.i(TAG, "trustResult.isTrusted=${trustResult.isTrusted}")
            Logger.i(TAG, "trustResult.error=${trustResult.error}")
            if (trustResult.isTrusted) {
                trustResult.trustPoints[0]
            } else {
                null
            }
        } else {
            null
        }
        if (docRequest.docType == DrivingLicense.MDL_DOCTYPE) {
            val cardArt = getDrawableResourceBytes(
                getSystemResourceEnvironment(),
                Res.drawable.driving_license_card_art
            )
            val consentFields = MdocConsentField.generateConsentFields(
                docRequest,
                TestAppUtils.documentTypeRepository,
                TestAppUtils.mdocCredential
            )
            if (suspendCancellableCoroutine { continuation ->
                holderConsentSheetData.value = ConsentSheetData(
                    showConsentPrompt = true,
                    continuation = continuation,
                    document = ConsentDocument(
                        name = "Erika's Driving License",
                        cardArt = cardArt,
                        description = "Driving License",
                    ),
                    consentFields = consentFields,
                    relyingParty = ConsentRelyingParty(
                        trustPoint = trustPoint,
                        websiteOrigin = null,
                    )
                )
            }) {
                return consentFields
            } else {
                return null
            }

        }
    }
    throw Error("DeviceRequest doesn't contain a docRequest for mDL")
}
