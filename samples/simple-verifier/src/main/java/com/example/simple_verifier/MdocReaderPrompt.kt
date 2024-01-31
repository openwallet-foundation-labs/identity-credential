/*
 * Copyright 2024 The Android Open Source Project
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

package com.example.simple_verifier

import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.nfc.NfcAdapter
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.identity.android.mdoc.deviceretrieval.VerificationHelper
import com.android.identity.android.mdoc.transport.DataTransportOptions
import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import com.android.identity.mdoc.request.DeviceRequestGenerator
import com.android.identity.mdoc.response.DeviceResponseParser
import com.android.identity.util.Logger
import com.budiyev.android.codescanner.CodeScanner
import com.budiyev.android.codescanner.CodeScannerView
import com.budiyev.android.codescanner.DecodeCallback
import com.budiyev.android.codescanner.ErrorCallback
import com.budiyev.android.codescanner.ScanMode
import com.example.simple_verifier.ui.theme.IdentityCredentialTheme
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class MdocReaderPrompt(
    private val mdocReaderSettings: MdocReaderSettings
) : BottomSheetDialogFragment() {

    private lateinit var context: Context
    private lateinit var readerModeListener: NfcAdapter.ReaderCallback

    private var hasStarted = false
    private lateinit var ageRequested: AgeVerificationType
    private lateinit var vibrator: Vibrator
    var readerEngagement: ByteArray? = null
    var responseBytes: ByteArray? = null
    private var mdocConnectionMethod: ConnectionMethod? = null

    private lateinit var responseListener: VerificationHelper.Listener
    private lateinit var verification: VerificationHelper
    private lateinit var navController: NavHostController

    private fun disconnect() {
        try {
            verification.disconnect()
        } catch (e: RuntimeException) {
            Logger.d("ReaderUtil", "Error ignored.", e)
        }
        hasStarted = false
    }

    private fun sendRequest() {
        val requestedElemsIntent: MutableMap<String, Boolean> = mutableMapOf()
        requestedElemsIntent["portrait"] = false
        when (ageRequested) {
            AgeVerificationType.Over18 -> requestedElemsIntent["age_over_18"] = false
            AgeVerificationType.Over21 -> requestedElemsIntent["age_over_21"] = false
        }

        val generator = DeviceRequestGenerator()
            .setSessionTranscript(verification.sessionTranscript)
            .addDocumentRequest(
                "org.iso.18013.5.1.mDL",
                mapOf("org.iso.18013.5.1" to requestedElemsIntent),
                null,
                null,
                null
            )

        verification.sendRequest(generator.generate())
    }

    private fun initializeWithContext() {
        Logger.d("init", "new init")
        context = requireActivity().applicationContext
        vibrator = context.getSystemService(Vibrator::class.java)
        responseListener = object : VerificationHelper.Listener {
            override fun onReaderEngagementReady(readerEngagement: ByteArray) {
                this@MdocReaderPrompt.readerEngagement = readerEngagement
            }

            override fun onDeviceEngagementReceived(connectionMethods: MutableList<ConnectionMethod>) {
                // Need to disambiguate the connection methods here to get e.g. two ConnectionMethods
                // if both BLE modes are available at the same time.
                Logger.d("Listener", "device engagement received")
                navController.navigate("ReaderReady/Connecting")
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                val availableMdocConnectionMethods = ConnectionMethod.disambiguate(connectionMethods)
                if (availableMdocConnectionMethods.isNotEmpty()) {
                    this@MdocReaderPrompt.mdocConnectionMethod = availableMdocConnectionMethods.first()
                }
                if (hasStarted)
                    throw IllegalStateException("Connection has already started. It is necessary to stop verification before starting a new one.")

                if (mdocConnectionMethod == null)
                    throw IllegalStateException("No mdoc connection method selected.")

                // Start connection
                mdocConnectionMethod?.let { verification.connect(it) }
                hasStarted = true
            }

            override fun onMoveIntoNfcField() {
                Logger.d("Listener", "onMoveIntoNfcField")
                navController.navigate("Status/Move into NFC Field")
            }

            override fun onDeviceConnected() {
                Logger.d("Listener", "onDeviceConnected")
                sendRequest()
            }

            override fun onResponseReceived(deviceResponseBytes: ByteArray) {
                Logger.d("Listener", "onResponseReceived")
                responseBytes = deviceResponseBytes
                navController.navigate("Results")
            }

            override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {
                Logger.d("Listener", "onDeviceDisconnected")
                disconnect()
                if (responseBytes == null) {
                    navController.navigate("ReaderReady")
                    initializeWithContext()
                }
            }

            override fun onError(error: Throwable) {
                Logger.d("Listener", "onError")
                disconnect()
                if (responseBytes == null) {
                    navController.navigate("ReaderReady")
                    initializeWithContext()
                }
            }
        }

        verification = VerificationHelper.Builder(context, responseListener, context.mainExecutor)
            .setDataTransportOptions(DataTransportOptions.Builder().build())
            .build()
        readerModeListener = NfcAdapter.ReaderCallback { tag ->
            verification.nfcProcessOnTagDiscovered(tag)
        }
    }

    private fun getDeviceResponse(): DeviceResponseParser.DeviceResponse {
        responseBytes?.let { rb ->
            val parser =
                DeviceResponseParser()
            parser.setSessionTranscript(verification.sessionTranscript)
            parser.setEphemeralReaderKey(verification.ephemeralReaderKey)
            parser.setDeviceResponse(rb)
            return parser.parse()
        } ?: throw IllegalStateException("Response not received")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Fill the whole screen by default
        val bottomSheetDialog = dialog as BottomSheetDialog
        bottomSheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        bottomSheetDialog.behavior.skipCollapsed = true

        initializeWithContext()
        ageRequested = mdocReaderSettings.getAgeRequested()
        val adapter = NfcAdapter.getDefaultAdapter(context)
        adapter.enableReaderMode(
            activity, readerModeListener,
            NfcAdapter.FLAG_READER_NFC_A + NfcAdapter.FLAG_READER_NFC_B
                    + NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK + NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null)

        return ComposeView(requireContext()).apply {
            setContent {
                IdentityCredentialTheme {
                    navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "ReaderReady",
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(route = "ReaderReady") {
                            ReaderScreen(
                                onClose = { dismiss() },
                                onQrClicked = { navController.navigate("QrScanner") },
                                mdocReaderSettings = mdocReaderSettings,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.background)
                                    .fillMaxSize()
                            )
                        }
                        composable(route = "ReaderReady/Connecting") {
                            ReaderScreen(
                                onClose = { dismiss() },
                                onQrClicked = { navController.navigate("QrScanner") },
                                mdocReaderSettings = mdocReaderSettings,
                                connecting = true,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.background)
                                    .fillMaxSize()
                            )
                        }
                        composable(route = "QrScanner") {
                            QrScanner(
                                onClose = { dismiss() },
                                qrCodeReturn = { qrText -> verification.setDeviceEngagementFromQrCode(qrText) },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.background)
                                    .fillMaxSize()
                            )
                        }
                        composable(route = "Results") {
                            ResultsScreen(
                                onClose = { dismiss() },
                                deviceResponse = getDeviceResponse(),
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.background)
                                    .fillMaxSize()
                            )
                        }
                        composable(route = "Status/{description}") {
                            StatusScreen(
                                onClose = { dismiss() },
                                description = it.arguments?.getString("description")!!,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.background)
                                    .fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        verification.disconnect()
        super.onDismiss(dialog)
    }
}

@Composable
private fun CloseBtn(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = { onClick() },
        colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier
            .minimumInteractiveComponentSize(),
    ) {
        Text(
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            text = "Close",
            modifier = Modifier
                .padding(5.dp)
        )
    }
}

@Composable
private fun StatusScreen(
    onClose: () -> Unit,
    description: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
    ) {
        Text(
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            text = description,
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp)
                .align(Alignment.TopCenter)
        )
        CloseBtn(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(15.dp)
        )
    }
}


@Composable
private fun QrScanner(
    qrCodeReturn: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
    ) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            AndroidView(
                modifier = Modifier,
                factory = {
                    CodeScannerView(it).apply {
                        val codeScanner = CodeScanner(it, this).apply {
                            isAutoFocusEnabled = true
                            isAutoFocusButtonVisible = false
                            scanMode = ScanMode.SINGLE
                            decodeCallback = DecodeCallback { result ->
                                qrCodeReturn.invoke(result.text)
                                releaseResources()
                            }
                            errorCallback = ErrorCallback {
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
        CloseBtn(onClick = onClose)
    }
}


@Composable
private fun MovingSquares(nfcConnecting: Boolean) {
    // Same color with different variants for different squares
    val primaryColor = MaterialTheme.colorScheme.tertiaryContainer
    val frontCircle = primaryColor.copy(0.75f)
    val midCircle = primaryColor.copy(0.50f)
    val backCircle = primaryColor.copy(0.25f)

    if (nfcConnecting) {
        DrawSquareOnCanvas(
            scale = scaleInfiniteTransition(targetValue = 2f, durationMillis = 600),
            color = backCircle,
        )

        DrawSquareOnCanvas(
            scale = scaleInfiniteTransition(targetValue = 2.5f, durationMillis = 800),
            color = midCircle,
        )

        DrawSquareOnCanvas(
            scale = scaleInfiniteTransition(targetValue = 3f, durationMillis = 1000),
            color = frontCircle,
        )
    } else {
        DrawSquareOnCanvas(
            scale = 2f,
            color = backCircle,
        )
    }
}


@Composable
private fun DrawSquareOnCanvas(
    scale: Float,
    color: Color,
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) { drawRoundRect(color = color) }
}


@Composable
private fun scaleInfiniteTransition(
    initialValue: Float = 0f,
    targetValue: Float,
    durationMillis: Int,
): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "nfc connect")
    val scale: Float by infiniteTransition.animateFloat(
        initialValue = initialValue,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "nfc connect"
    )
    return scale
}



@Composable
private fun ReaderScreen(
    onClose: () -> Unit,
    onQrClicked: () -> Unit,
    mdocReaderSettings: MdocReaderSettings,
    modifier: Modifier = Modifier,
    connecting: Boolean = false,
) {
    // top request info
    Box(
        modifier = modifier,
    ) {
        Column (
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(vertical = 60.dp)
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp)
                )
                .clip(RoundedCornerShape(12.dp))
                .align(Alignment.TopCenter)
        ) {
            Text(
                modifier = Modifier
                    .padding(vertical = 15.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center,
                text = "Requesting",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                textAlign = TextAlign.Center,
                text = "\t\u2022\t\t" + "Portrait",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 25.dp, vertical = 5.dp)
            )
            Text(
                textAlign = TextAlign.Center,
                text = "\t\u2022\t\t" + mdocReaderSettings.getAgeDisplayString(),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 25.dp, vertical = 5.dp)
            )
        }

        // center dialog
        Column (
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .requiredSize(100.dp)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                MovingSquares(connecting)
                if (connecting) {
                    Text(
                        textAlign = TextAlign.Center,
                        text = "Connected, waiting on user",
                        overflow = TextOverflow.Visible,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                } else {
                    IconButton(
                        onClick = { },
                        modifier = Modifier
                            .requiredSize(80.dp)
                            .align(Alignment.Center)
                    ) {
                        Icon(
                            modifier = Modifier.requiredSize(70.dp),
                            imageVector = Icons.Filled.Nfc,
                            contentDescription = stringResource(R.string.nfc),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
            if (!connecting) {
                Text(
                    textAlign = TextAlign.Center,
                    text = "Tap here to\n Present",
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        // bottom btns
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(vertical = 10.dp)
        ) {
            Button(
                onClick = { onQrClicked() },
                colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 5.dp),
            ) {
                Text(
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    text = "Use QR Code",
                    modifier = Modifier
                        .padding(5.dp)
                )
            }
            CloseBtn(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 5.dp)
            )
        }
    }
}


@Composable
private fun ResultsScreen(
    onClose: () -> Unit,
    deviceResponse: DeviceResponseParser.DeviceResponse?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
    ) {
        Column (
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(vertical = 40.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .align(Alignment.TopCenter)
        ) {
            val documents: MutableList<DeviceResponseParser.Document>? = deviceResponse?.documents
            val mdoc: DeviceResponseParser.Document? = documents?.get(0)
            if (mdoc != null && "org.iso.18013.5.1" in mdoc.issuerNamespaces) {
                val portraitBytes = mdoc.getIssuerEntryByteString("org.iso.18013.5.1", "portrait") // could break - need to fail more gracefully
                val portraitColor: Color
                val namespaces: List<String> = mdoc.getIssuerEntryNames("org.iso.18013.5.1")
                var ageError = true
                var resultDescription = "Person did not confirm age"

                if (namespaces.contains("age_over_21")) {
                    if (mdoc.getIssuerEntryBoolean("org.iso.18013.5.1", "age_over_21")) {
                        ageError = false
                        resultDescription = "Person is over 21"
                    } else {
                        resultDescription = "Person is NOT over 21"
                    }
                } else if (namespaces.contains("age_over_18")) {
                    if (mdoc.getIssuerEntryBoolean("org.iso.18013.5.1", "age_over_18")) {
                        ageError = false
                        resultDescription = "Person is over 18"
                    } else {
                        resultDescription = "Person is NOT over 18"
                    }
                }

                if (ageError) {
                    portraitColor = Color.Red
                }
                else if (!mdoc.issuerSignedAuthenticated or !mdoc.deviceSignedAuthenticated) {
                    portraitColor = Color.Red
                    resultDescription = "Person could not prove age"
                }
                else {
                    portraitColor = Color.Green
                }

                Image(modifier = Modifier
                    .size(200.dp)
                    .padding(horizontal = 20.dp)
                    .align(Alignment.CenterHorizontally)
                    .background(portraitColor),
                    bitmap = BitmapFactory.decodeByteArray(portraitBytes, 0, portraitBytes.size).asImageBitmap(),
                    contentScale = ContentScale.None,
                    contentDescription = "Portrait image + $resultDescription"
                )

                Text(
                    textAlign = TextAlign.Center,
                    text = resultDescription,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp)
                )
            } else {
                Text(
                    textAlign = TextAlign.Center,
                    text = "Error - mDL not as expected",
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp)
                )
            }
        }
        CloseBtn(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(vertical = 15.dp)
        )
    }
}


@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReaderScreenPreview() {
    IdentityCredentialTheme {
        ReaderScreen(
            modifier = Modifier.fillMaxSize(),
            mdocReaderSettings = MdocReaderSettings.Builder()
                .setAgeVerificationType(AgeVerificationType.Over21)
                .build(),
            onClose = {},
            onQrClicked = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun ResultsScreenPreview() {
    IdentityCredentialTheme {
        ResultsScreen(
            deviceResponse = null,
            onClose = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun QrPreview() {
    IdentityCredentialTheme {
        QrScanner(
            qrCodeReturn = {},
            onClose = {})
    }
}

