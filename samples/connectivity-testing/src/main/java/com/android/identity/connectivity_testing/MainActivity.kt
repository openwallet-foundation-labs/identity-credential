/*
 * Copyright (C) 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.identity.connectivity_testing

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.ParcelUuid
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper
import com.android.identity.android.mdoc.deviceretrieval.VerificationHelper
import com.android.identity.android.mdoc.engagement.QrEngagementHelper
import com.android.identity.android.mdoc.transport.DataTransport
import com.android.identity.android.mdoc.transport.DataTransportOptions
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.Tstr
import com.android.identity.connectivity_testing.ui.theme.IdentityCredentialTheme
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPublicKey
import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import com.android.identity.mdoc.connectionmethod.ConnectionMethodBle
import com.android.identity.util.Logger
import com.android.identity.util.UUID
import com.budiyev.android.codescanner.CodeScanner
import com.budiyev.android.codescanner.CodeScannerView
import com.budiyev.android.codescanner.DecodeCallback
import com.budiyev.android.codescanner.ErrorCallback
import com.budiyev.android.codescanner.ScanMode
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

private val TAG = "MainActivity"

private enum class State {
    MAIN_SCREEN,
    CLIENT_SCAN_QR_CODE,
    SERVER_PRESENT_QR_CODE,
    CLIENT_PROCESSING,
    SERVER_PROCESSING,
    COMPLETE,
}

class MainActivity : ComponentActivity() {


    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeAdvertiser: BluetoothLeAdvertiser

    companion object {
        const val USE_L2CAP = false
        const val URI_SCHEME = "owf-ic-connectivity-testing"
    }

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Logger.d(TAG, "permissionsLauncher ${it.key} = ${it.value}")
                if (!it.value) {
                    Toast.makeText(
                        this,
                        "The ${it.key} permission is required for BLE",
                        Toast.LENGTH_LONG
                    ).show()
                    return@registerForActivityResult
                }
            }
        }

    private val appPermissions: Array<String> =
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionsNeeded = appPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(
                applicationContext,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNeeded.isNotEmpty()) {
            permissionsLauncher.launch(
                permissionsNeeded.toTypedArray()
            )
        }

        val context = this as Context
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser


        setContent {
            IdentityCredentialTheme {
                MainScreen(applicationContext)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainScreen(
        context: Context
    ) {
        val context = LocalContext.current
        var state = remember { mutableStateOf<State>(State.MAIN_SCREEN) }
        var commandServer = remember { mutableStateOf<CommandServer?>(null) }
        var commandClient = remember { mutableStateOf<CommandClient?>(null) }
        var serverNumIterationsCompleted = remember { mutableStateOf<Int>(0) }
        var serverNumIterationsSuccess = remember { mutableStateOf<Int>(0) }
        var serverQrCode = remember { mutableStateOf<ImageBitmap?>(null) }

        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),

            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    title = {
                        Text(
                            "Connectivity Testing",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Column {
                        when (state.value) {
                            State.MAIN_SCREEN -> {
                                Button(onClick = {
                                    commandServer.value = CommandServer(
                                        onClientConnected = {
                                            state.value = State.SERVER_PROCESSING
                                        },
                                        onIterationSetup = { iterationNumber ->
                                            this@MainActivity.serverCleanPreviousIteration()
                                            this@MainActivity.serverSetupIteration(context, iterationNumber)
                                        },
                                        onIterationResult = { cmdFromClient ->
                                            this@MainActivity.serverIterationResult(
                                                cmdFromClient,
                                                serverNumIterationsCompleted,
                                                serverNumIterationsSuccess
                                            )
                                        }
                                    )
                                    Logger.i(TAG, "CommandServer listening on address " +
                                            "${commandServer.value!!.localAddress} port " +
                                            "${commandServer.value!!.localAddressPort}")
                                    serverNumIterationsCompleted.value = 0
                                    serverNumIterationsSuccess.value = 0
                                    serverQrCode.value = calcQrCodeBitmap(
                                        commandServer.value!!.localAddress,
                                        commandServer.value!!.localAddressPort
                                    ).asImageBitmap()
                                    state.value = State.SERVER_PRESENT_QR_CODE
                                }) {
                                    Text(text = "Start Server")
                                }
                                Button(onClick = {
                                    state.value = State.CLIENT_SCAN_QR_CODE
                                }) {
                                    Text(text = "Start Client")
                                }
                            }

                            State.CLIENT_SCAN_QR_CODE -> {
                                Text(text = "Scan QR code from other device")
                                Spacer(modifier = Modifier.height(50.dp))
                                QrScanner(
                                    description = "Scan QR code from other device",
                                    onScannedQrCode = { qrCodeString ->
                                        val uri = Uri.parse(qrCodeString)
                                        if (uri.scheme == URI_SCHEME) {
                                            Logger.i(TAG, "Connecting to ${uri.host} port ${uri.port}")
                                            commandClient.value = CommandClient(
                                                uri.host!!,
                                                uri.port,
                                                onIterationDo = { commandFromServer ->
                                                    this@MainActivity.clientDoIteration(
                                                        context,
                                                        commandFromServer
                                                    )
                                                }
                                            )
                                            state.value = State.CLIENT_PROCESSING
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Unsupported QR code with $qrCodeString",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                )
                            }

                            State.SERVER_PRESENT_QR_CODE -> {
                                Text(text = "Scan QR code on other device")
                                Spacer(modifier = Modifier.height(50.dp))
                                Image(
                                    bitmap = serverQrCode.value!!,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth(0.75f)
                                )
                            }

                            State.CLIENT_PROCESSING -> {
                                Text(text = "TODO: client processing")
                            }

                            State.SERVER_PROCESSING -> {
                                Text(text = "Num iterations completed ${serverNumIterationsCompleted.value}")
                                Text(text = "Num iterations successful ${serverNumIterationsSuccess.value}")
                            }

                            State.COMPLETE -> {
                                Text(text = "TODO: complete")
                            }
                        }
                    }
                }
            }
        }
    }

    private var currentIterationNumber: Int = 0
    private var currentIterationContext: Context? = null
    private var currentIterationEDeviceKey: EcPrivateKey? = null
    private var currentIterationQrEngagementHelper: QrEngagementHelper? = null
    private var currentIterationDataTransport: DataTransport? = null
    private var currentIterationError: Throwable? = null
    private var currentIterationDeviceRetrievalHelper: DeviceRetrievalHelper? = null

    private var uuidBeingAdvertised: UUID? = null

    @SuppressLint("MissingPermission")
    suspend fun serverCleanPreviousIteration() {
        if (currentIterationQrEngagementHelper != null) {
            currentIterationQrEngagementHelper!!.close()
            currentIterationQrEngagementHelper = null
        }
        if (currentIterationDataTransport != null) {
            currentIterationDataTransport!!.close()
            currentIterationDataTransport = null
        }
        if (currentIterationDeviceRetrievalHelper != null) {
            currentIterationDeviceRetrievalHelper!!.disconnect()
            currentIterationDeviceRetrievalHelper = null
        }
        currentIterationError = null
    }

    private val deviceRetrievalListener = object : DeviceRetrievalHelper.Listener {
        override fun onEReaderKeyReceived(eReaderKey: EcPublicKey) {
        }

        override fun onDeviceRequest(deviceRequestBytes: ByteArray) {
            if (Cbor.decode(deviceRequestBytes).asTstr == "TestRequest") {
                currentIterationDeviceRetrievalHelper!!.sendDeviceResponse(
                    deviceResponseBytes = Cbor.encode(Tstr("TestResponse")),
                    status = null
                )
            } else {
                Logger.wCbor(TAG, "Got something else than 'TestRequest'", deviceRequestBytes)
            }
        }

        override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {
        }

        override fun onError(error: Throwable) {
            TODO("Not yet implemented")
        }
    }

    private val qrEngagementListener = object : QrEngagementHelper.Listener {
        override fun onDeviceConnecting() {
            println("it $currentIterationNumber: onDeviceConnecting")
        }
        override fun onDeviceConnected(transport: DataTransport) {
            currentIterationDataTransport = transport
            currentIterationDeviceRetrievalHelper =
                DeviceRetrievalHelper.Builder(
                    currentIterationContext!!,
                    deviceRetrievalListener,
                    ContextCompat.getMainExecutor(currentIterationContext!!),
                    currentIterationEDeviceKey!!,
                ).useForwardEngagement(
                    transport,
                    currentIterationQrEngagementHelper!!.deviceEngagement,
                    currentIterationQrEngagementHelper!!.handover
                ).build()
            println("it $currentIterationNumber: onDeviceConnected")
        }
        override fun onError(error: Throwable) {
            currentIterationError = error
            println("it $currentIterationNumber: onError")
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun serverSetupIteration(
        context: Context,
        iterationNumber: Int
    ): String {
        currentIterationNumber = iterationNumber
        currentIterationContext = context
        currentIterationEDeviceKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val optionsBuilder = DataTransportOptions.Builder()
        if (USE_L2CAP) {
            optionsBuilder
                .setBleUseL2CAP(true)
                .setExperimentalBleL2CAPPsmInEngagement(true)
        }
        val options = optionsBuilder.build()
        val connectionMethods = mutableListOf<ConnectionMethod>()
        val bleUuid = UUID.randomUUID()
        if (USE_L2CAP) {
            // Use mdoc BLE Peripheral Server mode (to convey the PSM in the QrEngagement)
            connectionMethods.add(
                ConnectionMethodBle(
                    supportsPeripheralServerMode = true,
                    supportsCentralClientMode = false,
                    peripheralServerModeUuid = bleUuid,
                    centralClientModeUuid = null,
                )
            )
        } else {
            // Use mdoc BLE Central Client mode
            connectionMethods.add(
                ConnectionMethodBle(
                    supportsPeripheralServerMode = false,
                    supportsCentralClientMode = true,
                    peripheralServerModeUuid = null,
                    centralClientModeUuid = bleUuid,
                )
            )
        }
        currentIterationQrEngagementHelper =
            QrEngagementHelper.Builder(
                context,
                currentIterationEDeviceKey!!.publicKey,
                options,
                qrEngagementListener,
                ContextCompat.getMainExecutor(context)
            ).setConnectionMethods(connectionMethods).build()
        val deviceEngagementUriEncoded = currentIterationQrEngagementHelper!!.deviceEngagementUriEncoded
        return "QrDeviceEngagement $deviceEngagementUriEncoded"
    }

    suspend fun serverIterationResult(
        cmdFromClient: String,
        serverNumIterationsCompleted: MutableState<Int>,
        serverNumIterationsSuccess: MutableState<Int>,
    ) {
        if (cmdFromClient == true.toString()) {
            serverNumIterationsSuccess.value += 1
        }
        serverNumIterationsCompleted.value += 1
    }

    private var clientVerificationHelper: VerificationHelper? = null
    private var clientVerificationHelperContinuation: Continuation<Boolean>? = null

    suspend fun clientDoIteration(
        context: Context,
        commandFromServer: String,
    ): String {
        // Clean up from previous iteration...
        if (clientVerificationHelper != null) {
            clientVerificationHelper!!.disconnect()
            clientVerificationHelper = null
        }

        var result = false
        try {
            withTimeout(10.seconds) {
                val deviceEngagementUriEncoded = commandFromServer.split(" ")[1]
                Logger.i(TAG, "deviceEngagementUriEncoded $deviceEngagementUriEncoded")

                val optionsBuilder = DataTransportOptions.Builder()
                if (USE_L2CAP) {
                    optionsBuilder
                        .setBleUseL2CAP(true)
                        .setExperimentalBleL2CAPPsmInEngagement(true)
                }
                val options = optionsBuilder.build()
                clientVerificationHelper = VerificationHelper.Builder(
                    context,
                    verificationHelperListener,
                    ContextCompat.getMainExecutor(context)
                ).setDataTransportOptions(options).build()

                result = suspendCancellableCoroutine { continuation ->
                    clientVerificationHelperContinuation = continuation
                    clientVerificationHelper!!.setDeviceEngagementFromQrCode(
                        deviceEngagementUriEncoded
                    )
                }
            }
        } catch (_: TimeoutCancellationException) {
            Logger.i(TAG, "Timed out")
        }
        clientVerificationHelper!!.disconnect()
        clientVerificationHelper = null

        Logger.i(TAG, "The verdict is in: $result")
        Logger.i(TAG, "Sleeping 7 seconds to avoid 'registration failed because app is scanning too frequently'")
        delay(7.seconds)
        return result.toString()
    }

    private var verificationHelperListener = object : VerificationHelper.Listener {
        override fun onReaderEngagementReady(readerEngagement: ByteArray) {
            TODO("Not yet implemented")
        }

        override fun onDeviceEngagementReceived(connectionMethods: List<ConnectionMethod>) {
            clientVerificationHelper!!.connect(connectionMethods[0])
        }

        override fun onMoveIntoNfcField() {
            TODO("Not yet implemented")
        }

        override fun onDeviceConnected() {
            clientVerificationHelper!!.sendRequest(Cbor.encode(Tstr("TestRequest")))
        }

        override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {
            TODO("Not yet implemented")
        }

        override fun onResponseReceived(deviceResponseBytes: ByteArray) {
            if (Cbor.decode(deviceResponseBytes).asTstr == "TestResponse") {
                clientVerificationHelperContinuation!!.resume(true)
            } else {
                Logger.wCbor(TAG, "Got something else than TestResponse tstr", deviceResponseBytes)
                clientVerificationHelperContinuation!!.resume(false)
            }
        }

        override fun onError(error: Throwable) {
            clientVerificationHelperContinuation!!.resume(false)
        }

    }

    private fun calcQrCodeBitmap(
        serverAddress: String,
        serverPort: Int
    ): Bitmap {
        val str = "${URI_SCHEME}://${serverAddress}:${serverPort}/"
        val width = 800
        val result: BitMatrix = try {
            MultiFormatWriter().encode(
                str,
                BarcodeFormat.QR_CODE, width, width, null
            )
        } catch (e: WriterException) {
            throw java.lang.IllegalArgumentException(e)
        }
        val w = result.width
        val h = result.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] = if (result[x, y]) Color.BLACK else Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, w, h)
        return bitmap

    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun QrScanner(
    description: String,
    onScannedQrCode: (String) -> Unit,
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    if (!cameraPermissionState.status.isGranted) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier.padding(20.dp),
                    text = "Camera permission is needed to scan QR code."
                )
                Button(
                    onClick = {
                        cameraPermissionState.launchPermissionRequest()
                    }
                ) {
                    Text("Request permission.")
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier.padding(8.dp),
                    text = description
                )
                Row(
                    modifier = Modifier
                        .width(300.dp)
                        .height(300.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        factory = { context ->
                            CodeScannerView(context).apply {
                                val codeScanner = CodeScanner(context, this).apply {
                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    isAutoFocusEnabled = true
                                    isAutoFocusButtonVisible = false
                                    scanMode = ScanMode.SINGLE
                                    decodeCallback = DecodeCallback { result ->
                                        releaseResources()
                                        onScannedQrCode.invoke(result.text)
                                    }
                                    errorCallback = ErrorCallback { error ->
                                        Logger.w(TAG, "Error scanning QR", error)
                                        releaseResources()
                                    }
                                    camera = CodeScanner.CAMERA_BACK
                                    isFlashEnabled = false
                                }
                                codeScanner.startPreview()
                            }
                        },
                    )
                }
            }
        }
    }
}