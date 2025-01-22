package com.android.identity.testapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.identity.appsupport.ui.permissions.rememberBluetoothPermissionState
import com.android.identity.appsupport.ui.qrcode.ShowQrCodeDialog
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.Simple
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import com.android.identity.mdoc.connectionmethod.ConnectionMethodBle
import com.android.identity.mdoc.engagement.EngagementGenerator
import com.android.identity.testapp.presentation.MdocPresentationMechanism
import com.android.identity.mdoc.transport.MdocTransport
import com.android.identity.mdoc.transport.MdocTransportFactory
import com.android.identity.mdoc.transport.MdocTransportOptions
import com.android.identity.testapp.presentation.PresentationModel
import com.android.identity.util.Logger
import com.android.identity.util.UUID
import com.android.identity.util.toBase64Url
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString

private const val TAG = "IsoMdocProximitySharingScreen"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
@Composable
fun IsoMdocProximitySharingScreen(
    presentationModel: PresentationModel,
    onNavigateToPresentationScreen: (allowMultipleRequests: Boolean) -> Unit,
    showToast: (message: String) -> Unit,
) {
    val blePermissionState = rememberBluetoothPermissionState()

    var allowMultipleRequests = remember { mutableStateOf(false) }

    LaunchedEffect(presentationModel) { presentationModel.reset() }

    if (presentationModel.state.collectAsState().value == PresentationModel.State.RUNNING) {
        var mechanism = presentationModel.mechanism as MdocPresentationMechanism
        val deviceEngagementQrCode = "mdoc:" + mechanism.encodedDeviceEngagement.toByteArray().toBase64Url()
        ShowQrCodeDialog(
            title = { Text(text = "Scan QR code") },
            text = { Text(text = "Scan this QR code on another device") },
            additionalContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = allowMultipleRequests.value,
                        onCheckedChange = { allowMultipleRequests.value = it }
                    )
                    Text(text = "Keep connection open after first response")
                }
            },
            dismissButton = "Close",
            data = deviceEngagementQrCode,
            onDismiss = {
                presentationModel.reset()
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
        LazyColumn(
            modifier = Modifier.padding(8.dp)
        ) {
            item {
                TextButton(
                    onClick = {
                        presentationModel.reset()
                        presentationModel.setWaiting()
                        presentationModel.presentationScope.launch() {
                            doHolderFlow(
                                connectionMethod = ConnectionMethodBle(
                                    supportsPeripheralServerMode = false,
                                    supportsCentralClientMode = true,
                                    peripheralServerModeUuid = null,
                                    centralClientModeUuid = UUID.randomUUID(),
                                ),
                                handover = Simple.NULL,
                                options = MdocTransportOptions(),
                                allowMultipleRequests = allowMultipleRequests,
                                showToast = showToast,
                                presentationModel = presentationModel,
                                onNavigateToPresentationScreen = onNavigateToPresentationScreen,
                            )
                        }
                    },
                    content = { Text("Share via QR (mdoc central client mode)") }
                )
            }
            item {
                TextButton(
                    onClick = {
                        presentationModel.reset()
                        presentationModel.setWaiting()
                        presentationModel.presentationScope.launch() {
                            doHolderFlow(
                                connectionMethod = ConnectionMethodBle(
                                    supportsPeripheralServerMode = true,
                                    supportsCentralClientMode = false,
                                    peripheralServerModeUuid = UUID.randomUUID(),
                                    centralClientModeUuid = null,
                                ),
                                handover = Simple.NULL,
                                options = MdocTransportOptions(),
                                allowMultipleRequests = allowMultipleRequests,
                                showToast = showToast,
                                presentationModel = presentationModel,
                                onNavigateToPresentationScreen = onNavigateToPresentationScreen,
                            )
                        }
                    },
                    content = { Text("Share via QR (mdoc peripheral server mode)") }
                )
            }
            item {
                TextButton(
                    onClick = {
                        presentationModel.reset()
                        presentationModel.setWaiting()
                        presentationModel.presentationScope.launch() {
                            doHolderFlow(
                                connectionMethod = ConnectionMethodBle(
                                    supportsPeripheralServerMode = false,
                                    supportsCentralClientMode = true,
                                    peripheralServerModeUuid = null,
                                    centralClientModeUuid = UUID.randomUUID(),
                                ),
                                handover = Simple.NULL,
                                options = MdocTransportOptions(bleUseL2CAP = true),
                                allowMultipleRequests = allowMultipleRequests,
                                showToast = showToast,
                                presentationModel = presentationModel,
                                onNavigateToPresentationScreen = onNavigateToPresentationScreen,
                            )
                        }
                    },
                    content = { Text("Share via QR (mdoc central client mode w/ L2CAP)") }
                )
            }
            item {
                TextButton(
                    onClick = {
                        presentationModel.reset()
                        presentationModel.setWaiting()
                        presentationModel.presentationScope.launch() {
                            doHolderFlow(
                                connectionMethod = ConnectionMethodBle(
                                    supportsPeripheralServerMode = true,
                                    supportsCentralClientMode = false,
                                    peripheralServerModeUuid = UUID.randomUUID(),
                                    centralClientModeUuid = null,
                                ),
                                handover = Simple.NULL,
                                options = MdocTransportOptions(bleUseL2CAP = true),
                                allowMultipleRequests = allowMultipleRequests,
                                showToast = showToast,
                                presentationModel = presentationModel,
                                onNavigateToPresentationScreen = onNavigateToPresentationScreen,
                            )
                        }
                    },
                    content = { Text("Share via QR (mdoc peripheral server mode w/ L2CAP)") }
                )
            }
        }
    }
}

private suspend fun doHolderFlow(
    connectionMethod: ConnectionMethod,
    handover: DataItem,
    options: MdocTransportOptions,
    allowMultipleRequests: MutableState<Boolean>,
    showToast: (message: String) -> Unit,
    presentationModel: PresentationModel,
    onNavigateToPresentationScreen: (allowMultipleRequests: Boolean) -> Unit,
) {
    val transport = MdocTransportFactory.Default.createTransport(
        connectionMethod,
        MdocTransport.Role.MDOC,
        options
    )
    val eDeviceKey = Crypto.createEcPrivateKey(EcCurve.P256)
    val engagementGenerator = EngagementGenerator(
        eSenderKey = eDeviceKey.publicKey,
        version = "1.0"
    )
    engagementGenerator.addConnectionMethods(listOf(transport!!.connectionMethod))
    val encodedDeviceEngagement = ByteString(engagementGenerator.generate())

    presentationModel.setRunning(
        MdocPresentationMechanism(
            transport = transport,
            eDeviceKey = eDeviceKey,
            encodedDeviceEngagement = encodedDeviceEngagement,
            handover = handover,
            engagementDuration = null,
        )
    )

    // MdocTransport.open() doesn't return until state is CONNECTED which is much later than
    // when we're seeing a connection attempt (when state is CONNECTING)
    //
    // And we want to switch to PresentationScreen upon seeing CONNECTING .. so call open() in a subroutine
    // and just watch the state variable change.
    //
    presentationModel.presentationScope.launch {
        try {
            transport.open(eDeviceKey.publicKey)
        } catch (error: Throwable) {
            Logger.e(TAG, "Caught exception", error)
            error.printStackTrace()
            showToast("Error: ${error.message}")
            presentationModel.setCompleted(error)
        }
    }
    // Wait until state changes to CONNECTED, CONNECTING, FAILED, or CLOSED
    transport.state.first {
        it == MdocTransport.State.CONNECTED ||
        it == MdocTransport.State.CONNECTING ||
        it == MdocTransport.State.FAILED ||
        it == MdocTransport.State.CLOSED
    }
    if (transport.state.value == MdocTransport.State.CONNECTING ||
        transport.state.value == MdocTransport.State.CONNECTED) {
        onNavigateToPresentationScreen(allowMultipleRequests.value)
    }
}

