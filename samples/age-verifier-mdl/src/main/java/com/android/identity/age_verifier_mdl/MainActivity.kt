package org.multipaz.age_verifier_mdl

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.multipaz.age_verifier_mdl.ui.theme.IdentityCredentialTheme
import com.android.identity.android.mdoc.deviceretrieval.VerificationHelper
import org.multipaz.crypto.Algorithm
import org.multipaz.mdoc.request.DeviceRequestGenerator
import org.multipaz.mdoc.response.DeviceResponseParser
import org.multipaz.util.Logger
import java.lang.IllegalStateException

class MainActivity : ComponentActivity() {
    companion object {
        private val TAG = "MainActivity"

        val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        val MDL_NAMESPACE = "org.iso.18013.5.1"
    }

    private lateinit var transferHelper: TransferHelper

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Logger.i(TAG, "permissionsLauncher ${it.key} = ${it.value}")
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
        if (android.os.Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        } else if (android.os.Build.VERSION.SDK_INT >= VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        }
    else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }

    private var transactionError: Throwable? = null
    private var resultSize: Number = 0
    private var resultPortrait: Bitmap? = null
    private var resultAgeOver21: Boolean = false
    private var durationMillisTapToEngagement: Long = 0
    private var durationMillisEngagementToRequest: Long = 0
    private var durationMillisScanning: Long = 0
    private var durationMillisRequestToResponse: Long = 0
    private var durationMillisTotal: Long = 0
    private var engagementMethod: String = ""
    private var connectionMethod: String = ""

    private fun getMetricsForTransaction(transferHelper: TransferHelper) {
        durationMillisTapToEngagement = transferHelper.getTapToEngagementDurationMillis()
        durationMillisEngagementToRequest = transferHelper.getEngagementToRequestDurationMillis()
        durationMillisScanning = transferHelper.getScanningDurationMillis()
        durationMillisRequestToResponse = transferHelper.getRequestToResponseDurationMillis()
        durationMillisTotal = durationMillisTapToEngagement +
                durationMillisEngagementToRequest +
                durationMillisRequestToResponse

        engagementMethod = when (transferHelper.getEngagementMethod()) {
            VerificationHelper.EngagementMethod.QR_CODE -> "QR Code"
            VerificationHelper.EngagementMethod.NFC_STATIC_HANDOVER -> "NFC Static Handover"
            VerificationHelper.EngagementMethod.NFC_NEGOTIATED_HANDOVER -> "NFC Negotiated Handover"
            else -> "Unknown (${transferHelper.getEngagementMethod()})"
        }

        connectionMethod = transferHelper.getConnectionMethod()
    }

    private fun parseResponse(deviceResponseBytes: ByteArray?) {
        resultSize = deviceResponseBytes!!.size
        val parsedResponse = DeviceResponseParser(
            deviceResponseBytes,
            transferHelper.getSessionTranscript()
        ).parse()
        if (parsedResponse.documents.isEmpty()) {
            Toast.makeText(applicationContext, "No documents returned", Toast.LENGTH_SHORT).show()
            transferHelper.close()
            return
        }
        val doc = parsedResponse.documents.first()
        if (!doc.docType.equals(MDL_DOCTYPE)) {
            Toast.makeText(applicationContext, "Expected mDL, got ${doc.docType}", Toast.LENGTH_SHORT).show()
            transferHelper.close()
            return
        }
        try {
            try {
                val portraitData = doc.getIssuerEntryByteString(MDL_NAMESPACE, "portrait")
                val options = BitmapFactory.Options()
                options.inMutable = true
                resultPortrait = BitmapFactory.decodeByteArray(portraitData, 0, portraitData.size, options)
            } catch (e: IllegalArgumentException) {
                resultPortrait = null
            }
            resultAgeOver21 = doc.getIssuerEntryBoolean(MDL_NAMESPACE, "age_over_21")
        } catch (e: IllegalStateException) {
            Toast.makeText(applicationContext, "Error getting data", Toast.LENGTH_SHORT).show()
            transferHelper.close()
            return
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
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

        transferHelper = TransferHelper.getInstance(applicationContext, this)
        Logger.isDebugEnabled = transferHelper.getDebugEnabled()

        setContent {
            IdentityCredentialTheme {
                val navController = rememberNavController()

                val stateDisplay = remember { mutableStateOf("Idle") }

                transferHelper.getState().observe(this as LifecycleOwner) { state ->
                    when (state) {
                        null -> {
                            // TODO: b/393388152 - Enum argument can be null in Java,
                            //  but exhaustive when contains no null branch.
                        }
                        TransferHelper.State.IDLE -> {
                            Logger.i(TAG, "idle")
                            stateDisplay.value = "Idle"
                        }
                        TransferHelper.State.ENGAGING -> {
                            Logger.i(TAG, "engaging")
                            stateDisplay.value = "Engaging"
                        }
                        TransferHelper.State.CONNECTING -> {
                            Logger.i(TAG, "connecting")
                            stateDisplay.value = "Connecting"
                        }
                        TransferHelper.State.CONNECTED -> {
                            stateDisplay.value = "Connected"
                            Logger.i(TAG, "connected")
                            val deviceRequestGenerator = DeviceRequestGenerator(
                                transferHelper.getSessionTranscript()
                            )
                            val request =
                                if (transferHelper.getIncludePortraitInRequest()) {
                                    mapOf(
                                        Pair(
                                            MDL_NAMESPACE,
                                            mapOf(Pair("portrait", false), Pair("age_over_21", false))
                                        )
                                    )
                                } else {
                                    mapOf(
                                        Pair(
                                            MDL_NAMESPACE,
                                            mapOf(Pair("age_over_21", false))
                                        )
                                    )
                                }
                            deviceRequestGenerator.addDocumentRequest(
                                MDL_DOCTYPE,
                                request,
                                null,
                                null,
                                Algorithm.UNSET,
                                null
                            )
                            transferHelper.sendRequest(deviceRequestGenerator.generate())
                        }

                        TransferHelper.State.REQUEST_SENT -> {
                            Logger.i(TAG, "request sent")
                            stateDisplay.value = "Request Sent"
                        }
                        TransferHelper.State.TRANSACTION_COMPLETE -> {
                            transactionError = null
                            if (transferHelper.error != null) {
                                transactionError = transferHelper.error
                                stateDisplay.value = "Error Occurred"
                                Logger.i(TAG, "error occurred")
                            } else {
                                stateDisplay.value = "Response Received"
                                Logger.i(TAG, "response received")
                                parseResponse(transferHelper.deviceResponseBytes)
                            }
                            getMetricsForTransaction(transferHelper)
                            navController.navigate("ResultScreen")
                        }
                    }
                }

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
                                    "mDL Age Verifier Sample",
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
                        NavHost(navController = navController, startDestination = "MainScreen")
                        {
                            composable("MainScreen") {
                                MainScreen(stateDisplay)
                            }
                            composable("ResultScreen") {
                                ResultScreen(navController)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun MainScreen(stateDisplay: MutableState<String>) {
        val includePortraitInRequest = remember { mutableStateOf(transferHelper.getIncludePortraitInRequest()) }
        val bleCentralClientDataTransferEnabled = remember { mutableStateOf(transferHelper.getBleCentralClientDataTransferEnabled()) }
        val blePeripheralServerDataTransferEnabled = remember { mutableStateOf(transferHelper.getBlePeripheralServerDataTransferEnabled()) }
        val wifiAwareDataTransferEnabled = remember { mutableStateOf(transferHelper.getWifiAwareDataTransferEnabled()) }
        val nfcDataTransferEnabled = remember { mutableStateOf(transferHelper.getNfcDataTransferEnabled()) }
        val tcpDataTransferEnabled = remember { mutableStateOf(transferHelper.getTcpDataTransferEnabled()) }
        val udpDataTransferEnabled = remember { mutableStateOf(transferHelper.getUdpDataTransferEnabled()) }
        val l2capEnabled = remember { mutableStateOf(transferHelper.getL2CapEnabled()) }
        val experimentalPsmEnabled = remember { mutableStateOf(transferHelper.getExperimentalPsmEnabled()) }
        val debugEnabled = remember { mutableStateOf(transferHelper.getDebugEnabled()) }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = "This app is used to engage with an mDL via NFC and request " +
                                    "a simple age attestation. The main purpose of this " +
                                    "application is to evaluate performance.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column {
                        Image(
                            modifier = Modifier.size(200.dp),
                            painter = painterResource(id = R.drawable.ic_nfc),
                            contentDescription = "The NFC Logo",
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column {
                        Text(
                            modifier = Modifier.padding(16.dp),
                            text = "Tap mDL to request age",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Column {
                        Text(
                            modifier = Modifier.padding(16.dp),
                            text = "State: ${stateDisplay.value}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    if (!stateDisplay.value.equals("Idle")) {
                        Column {
                            Button(
                                onClick = {
                                    transferHelper.close()
                                }) {
                                Text("Cancel")
                            }
                        }
                    }
                    HorizontalDivider()
                    SettingSectionTitle(title = "Request")
                    SettingToggle(
                        title = "Include Portrait in Request",
                        subtitleOn = "Portrait Requested",
                        subtitleOff = "Portrait not Requested",
                        isChecked = includePortraitInRequest.value,
                        onCheckedChange = { checked ->
                            transferHelper.setIncludePortraitInRequest(checked)
                            includePortraitInRequest.value = checked
                        }
                    )
                    SettingSectionTitle(title = "Data Retrieval (Negotiated Handover)")
                    SettingToggle(
                        title = "BLE mdoc central client data retrieval",
                        subtitleOn = "BLE mdoc central client data retrieval enabled",
                        subtitleOff = "BLE mdoc central client data retrieval disabled",
                        isChecked = bleCentralClientDataTransferEnabled.value,
                        onCheckedChange = { checked ->
                            transferHelper.setBleCentralClientDataTransferEnabled(checked)
                            bleCentralClientDataTransferEnabled.value = checked
                            transferHelper.reinitializeVerificationHelper()
                        }
                    )
                    SettingToggle(
                        title = "BLE mdoc peripheral server data retrieval",
                        subtitleOn = "BLE mdoc peripheral server data retrieval enabled",
                        subtitleOff = "BLE mdoc peripheral server data retrieval disabled",
                        isChecked = blePeripheralServerDataTransferEnabled.value,
                        onCheckedChange = { checked ->
                            transferHelper.setBlePeripheralServerDataTransferEnabled(checked)
                            blePeripheralServerDataTransferEnabled.value = checked
                            transferHelper.reinitializeVerificationHelper()
                        }
                    )
                    SettingToggle(
                        title = "Wifi Aware data transfer",
                        subtitleOn = "Wifi Aware data transfer enabled",
                        subtitleOff = "Wifi Aware data transfer disabled",
                        isChecked = wifiAwareDataTransferEnabled.value,
                        onCheckedChange = { checked ->
                            transferHelper.setWifiAwareDataTransferEnabled(checked)
                            wifiAwareDataTransferEnabled.value = checked
                            transferHelper.reinitializeVerificationHelper()
                        }
                    )
                    SettingToggle(
                        title = "NFC data transfer",
                        subtitleOn = "NFC data transfer enabled",
                        subtitleOff = "NFC data transfer disabled",
                        isChecked = nfcDataTransferEnabled.value,
                        onCheckedChange = { checked ->
                            transferHelper.setNfcDataTransferEnabled(checked)
                            nfcDataTransferEnabled.value = checked
                            transferHelper.reinitializeVerificationHelper()
                        }
                    )
                    SettingToggle(
                        title = "TCP data transfer (proprietary)",
                        subtitleOn = "TCP data transfer enabled",
                        subtitleOff = "TCP data transfer disabled",
                        isChecked = tcpDataTransferEnabled.value,
                        onCheckedChange = { checked ->
                            transferHelper.setTcpDataTransferEnabled(checked)
                            tcpDataTransferEnabled.value = checked
                            transferHelper.reinitializeVerificationHelper()
                        }
                    )
                    SettingToggle(
                        title = "UDP data transfer (proprietary)",
                        subtitleOn = "UDP data transfer enabled",
                        subtitleOff = "UDP data transfer disabled",
                        isChecked = udpDataTransferEnabled.value,
                        onCheckedChange = { checked ->
                            transferHelper.setUdpDataTransferEnabled(checked)
                            udpDataTransferEnabled.value = checked
                            transferHelper.reinitializeVerificationHelper()
                        }
                    )
                    SettingSectionTitle(title = "Options")
                    SettingToggle(
                        title = "Use BLE L2CAP if available",
                        subtitleOn = "BLE L2CAP enabled",
                        subtitleOff = "BLE L2CAP disabled",
                        isChecked = l2capEnabled.value,
                        onCheckedChange = { checked ->
                            transferHelper.setL2CapEnabled(checked)
                            l2capEnabled.value = checked
                            transferHelper.reinitializeVerificationHelper()
                        }
                    )
                    SettingToggle(
                        title = "Experimental conveyance of L2CAP PSM",
                        subtitleOn = "Experimental PSM Conveyance enabled",
                        subtitleOff = "Experimental PSM Conveyance disabled",
                        isChecked = experimentalPsmEnabled.value,
                        onCheckedChange = { checked ->
                            transferHelper.setExperimentalPsmEnabled(checked)
                            experimentalPsmEnabled.value = checked
                            transferHelper.reinitializeVerificationHelper()
                        }
                    )
                    SettingSectionTitle(title = "Logging")
                    SettingToggle(
                        title = "Show Debug Messages",
                        subtitleOn = "Debug messages enabled",
                        subtitleOff = "Debug messages disabled",
                        isChecked = debugEnabled.value,
                        onCheckedChange = { checked ->
                            transferHelper.setDebugEnabled(checked)
                            debugEnabled.value = checked
                            Logger.isDebugEnabled = checked
                        }
                    )

                }
            }
        }
    }

    @Composable
    fun ResultScreen(navController: NavController) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (transactionError != null) {
                        Column { Text(text = "Error: ${transferHelper.error}") }
                    } else {
                        if (resultPortrait != null) {
                            Column {
                                Image(
                                    bitmap = resultPortrait!!.asImageBitmap(),
                                    contentDescription = null,
                                )
                            }
                        }
                        Column { Text(text = "Age over 21: $resultAgeOver21") }
                        Column { Text(text = "DeviceResponse: $resultSize bytes") }
                    }

                    HorizontalDivider()
                    Column { Text(text = "Engagement: $engagementMethod") }
                    Column { Text(text = "Connection: $connectionMethod") }
                    HorizontalDivider()
                    val scanningText = if (durationMillisScanning > 0) {
                        "$durationMillisScanning ms"
                    } else {
                        "N/A"
                    }
                    Column { Text(text = "Tap to Engagement Received: $durationMillisTapToEngagement ms") }
                    Column { Text(text = "Engagement Received to Request Sent: $durationMillisEngagementToRequest ms") }
                    Column { Text(text = "Scanning: $scanningText") }
                    Column { Text(text = "Request Sent to Response Received: $durationMillisRequestToResponse ms") }
                    Column { Text(text = "Total transaction time: $durationMillisTotal ms") }
                    HorizontalDivider()
                    Column {
                        Button(onClick = {
                            transferHelper.close()
                            navController.navigate("MainScreen")
                        }) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSectionTitle(
    modifier: Modifier = Modifier,
    title: String
) {
    Column(modifier = modifier) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingToggle(
    modifier: Modifier = Modifier,
    title: String,
    subtitleOn: String,
    subtitleOff: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val subtitle = if (isChecked) subtitleOn else subtitleOff
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Switch(
            checked = isChecked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}
