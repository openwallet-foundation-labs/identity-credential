package org.multipaz.testapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.multipaz.provisioning.evidence.EvidenceRequestCreatePassphrase
import org.multipaz.provisioning.evidence.EvidenceRequestMessage
import org.multipaz.provisioning.evidence.EvidenceRequestOpenid4Vp
import org.multipaz.provisioning.evidence.EvidenceRequestQuestionMultipleChoice
import org.multipaz.provisioning.evidence.EvidenceRequestQuestionString
import org.multipaz.provisioning.evidence.EvidenceRequestWeb
import org.multipaz.provisioning.evidence.EvidenceResponseCreatePassphrase
import org.multipaz.provisioning.evidence.EvidenceResponseMessage
import org.multipaz.provisioning.evidence.EvidenceResponseQuestionMultipleChoice
import org.multipaz.provisioning.evidence.EvidenceResponseQuestionString
import org.multipaz.provisioning.evidence.EvidenceResponseWeb
import org.multipaz.testapp.provisioning.model.ProvisioningModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.painterResource
import org.multipaz.compose.PassphraseEntryField
import org.multipaz.compose.presentment.Presentment
import org.multipaz.compose.webview.RichText
import org.multipaz.credential.Credential
import org.multipaz.models.presentment.DigitalCredentialsPresentmentMechanism
import org.multipaz.models.presentment.PresentmentModel
import org.multipaz.provisioning.ApplicationSupport
import org.multipaz.provisioning.LandingUrlUnknownException
import org.multipaz.provisioning.evidence.EvidenceRequestNotificationPermission
import org.multipaz.provisioning.evidence.EvidenceResponseNotificationPermission
import org.multipaz.provisioning.evidence.EvidenceResponseOpenid4Vp
import org.multipaz.testapp.App
import org.multipaz.testapp.platformAppIcon
import org.multipaz.testapp.platformAppName
import org.multipaz.util.Logger
import kotlin.time.Duration.Companion.seconds

@Composable
fun ProvisioningTestScreen(
    app: App,
    provisioningModel: ProvisioningModel,
    presentmentModel: PresentmentModel
) {
    LaunchedEffect(provisioningModel) {
        provisioningModel.run()
    }
    val provisioningState = provisioningModel.state.collectAsState(ProvisioningModel.Initial).value
    Column {
        if (provisioningState is ProvisioningModel.EvidenceRequested) {
            RequestEvidence(
                app,
                provisioningModel,
                presentmentModel,
                provisioningState
            )
        } else if (provisioningState is ProvisioningModel.Error) {
            Text(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(8.dp),
                style = MaterialTheme.typography.titleLarge,
                text = "Error: ${provisioningState.err.message}"
            )
            Text(
                modifier = Modifier.padding(4.dp),
                style = MaterialTheme.typography.bodyMedium,
                text = "For details: adb logcat -s ProvisioningModel"
            )
        } else {
            val text = when (provisioningState) {
                ProvisioningModel.Initial -> "Starting provisioning..."
                ProvisioningModel.Connected -> "Connected to the back-end"
                ProvisioningModel.Registration -> "Initializing session..."
                ProvisioningModel.SendingEvidence -> "Sending evidence..."
                ProvisioningModel.ProcessingEvidence -> "Processing evidence..."
                ProvisioningModel.ProofingComplete -> "Evidence collected"
                ProvisioningModel.RequestingCredentials -> "Requesting credentials..."
                ProvisioningModel.CredentialsIssued -> "Credentials issued"
                is ProvisioningModel.Error -> throw IllegalStateException()
                is ProvisioningModel.EvidenceRequested -> throw IllegalStateException()
            }
            Text(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(8.dp),
                style = MaterialTheme.typography.titleLarge,
                text = text
            )
        }
    }
}

@Composable
private fun RequestEvidence(
    app: App,
    provisioningModel: ProvisioningModel,
    presentmentModel: PresentmentModel,
    request: ProvisioningModel.EvidenceRequested
) {
    val index = remember { mutableIntStateOf(0) }
    val evidenceRequest = request.evidenceRequests[index.value]
    val coroutineScope = rememberCoroutineScope { app.promptModel }
    when (evidenceRequest) {
        is EvidenceRequestQuestionString -> {
            EvidenceRequestQuestionStringView(
                evidenceRequest,
                onAccept = { inputString ->
                    coroutineScope.launch {
                        provisioningModel.provideEvidence(
                            EvidenceResponseQuestionString(inputString)
                        )
                    }
                }
            )
        }

        is EvidenceRequestCreatePassphrase -> {
            EvidenceRequestCreatePassphraseView(
                evidenceRequest,
                onAccept = { inputString ->
                    coroutineScope.launch {
                        provisioningModel.provideEvidence(
                            EvidenceResponseCreatePassphrase(inputString)
                        )
                    }
                }
            )
        }

        is EvidenceRequestNotificationPermission -> {
            EvidenceRequestNotificationPermissionView(
                evidenceRequest = evidenceRequest,
                provisioningModel = provisioningModel
            )
        }

        is EvidenceRequestMessage -> {
            EvidenceRequestMessageView(
                evidenceRequest = evidenceRequest,
                provisioningModel = provisioningModel
            )
        }

        is EvidenceRequestQuestionMultipleChoice -> {
            EvidenceRequestQuestionMultipleChoiceView(
                evidenceRequest,
                onAccept = { selectedOption ->
                    coroutineScope.launch {
                        provisioningModel.provideEvidence(
                            evidence = EvidenceResponseQuestionMultipleChoice(selectedOption)
                        )
                    }
                }
            )
        }

        is EvidenceRequestOpenid4Vp -> {
            EvidenceRequestOpenid4VpView(
                app = app,
                provisioningModel = provisioningModel,
                evidenceRequest = evidenceRequest,
                presentmentModel = presentmentModel,
                viableCredentials = request.credentials,
                onNextEvidenceRequest = {
                    index.value++
                }
            )
        }

        is EvidenceRequestWeb -> {
            EvidenceRequestWebView(
                evidenceRequest = evidenceRequest,
                provisioningModel = provisioningModel
            )
        }

        else -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    text = "Unsupported evidence request: $evidenceRequest"
                )
            }
        }
    }
}

private const val TAG = "ProvisioningTestScreen"

@Composable
fun EvidenceRequestMessageView(
    evidenceRequest: EvidenceRequestMessage,
    provisioningModel: ProvisioningModel
) {
    val coroutineScope = rememberCoroutineScope()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        RichText(
            modifier = Modifier.padding(8.dp),
            content = evidenceRequest.message,
            assets = evidenceRequest.assets
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        val rejectButtonText = evidenceRequest.rejectButtonText
        if (rejectButtonText != null) {
            Button(
                modifier = Modifier.padding(8.dp),
                onClick = {
                    coroutineScope.launch {
                        provisioningModel.provideEvidence(
                            evidence = EvidenceResponseMessage(false)
                        )
                    }
                }) {
                Text(rejectButtonText)
            }
        }
        Button(
            modifier = Modifier.padding(8.dp),
            onClick = {
                coroutineScope.launch {
                    provisioningModel.provideEvidence(
                        evidence = EvidenceResponseMessage(true)
                    )
                }
            }) {
            Text(evidenceRequest.acceptButtonText)
        }
    }

}

@Composable
fun EvidenceRequestQuestionStringView(
    evidenceRequest: EvidenceRequestQuestionString,
    onAccept: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        RichText(
            modifier = Modifier.padding(8.dp),
            content = evidenceRequest.message,
            assets = evidenceRequest.assets
        )
    }

    var inputText by remember { mutableStateOf(evidenceRequest.defaultValue) }
    TextField(
        value = inputText,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        onValueChange = { inputText = it },
        label = { Text("Answer") }
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Button(onClick = {
            onAccept(inputText)
        }) {
            Text(evidenceRequest.acceptButtonText)
        }
    }
}

@Composable
fun EvidenceRequestCreatePassphraseView(
    evidenceRequest: EvidenceRequestCreatePassphrase,
    onAccept: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var chosenPassphrase by remember { mutableStateOf("") }
    var verifiedPassphrase by remember { mutableStateOf("") }
    var showMatchErrorText by remember { mutableStateOf(false) }

    val constraints = evidenceRequest.passphraseConstraints

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        if (chosenPassphrase.isEmpty()) {
            RichText(
                modifier = Modifier.padding(8.dp),
                content = evidenceRequest.message,
                assets = evidenceRequest.assets
            )
        } else {
            RichText(
                modifier = Modifier.padding(8.dp),
                content = evidenceRequest.verifyMessage,
                assets = evidenceRequest.assets
            )
        }
    }

    if (chosenPassphrase.isEmpty()) {
        // Choose PIN
        var currentPassphraseMeetsRequirements by remember { mutableStateOf(false) }
        var currentPassphrase by remember { mutableStateOf("") }
        PassphraseEntryField(
            constraints = constraints,
            checkWeakPassphrase = true,
            onChanged = { passphrase, passphraseMeetsRequirements, donePressed ->
                currentPassphrase = passphrase
                currentPassphraseMeetsRequirements = passphraseMeetsRequirements
                if (constraints.isFixedLength() && currentPassphraseMeetsRequirements) {
                    chosenPassphrase = passphrase
                } else if (donePressed) {
                    chosenPassphrase = passphrase
                }
            }
        )

        if (constraints.minLength != constraints.maxLength) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    enabled = currentPassphraseMeetsRequirements,
                    onClick = { chosenPassphrase = currentPassphrase }
                ) {
                    Text("Next")
                }
            }
        }
    } else if (verifiedPassphrase.isEmpty()) {
        // Verify PIN
        var currentPassphrase by remember { mutableStateOf("") }
        PassphraseEntryField(
            constraints = constraints,
            onChanged = { passphrase, passphraseMeetsRequirements, donePressed ->
                currentPassphrase = passphrase
                if (constraints.isFixedLength() && currentPassphrase.length == constraints.minLength) {
                    verifiedPassphrase = passphrase
                } else if (donePressed) {
                    verifiedPassphrase = passphrase
                }
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Spacer(modifier = Modifier.weight(1.0f))
            if (constraints.minLength != constraints.maxLength) {
                Button(
                    onClick = { verifiedPassphrase = currentPassphrase },
                ) {
                    Text("Confirm")
                }
            }
        }
    } else {
        if (chosenPassphrase == verifiedPassphrase) {
            onAccept(chosenPassphrase)
            return
        } else {
            showMatchErrorText = true
            PassphraseEntryField(
                constraints = constraints,
                onChanged = { passphrase, passphraseMeetsRequirements, donePressed ->
                }
            )
            SideEffect {
                scope.launch {
                    delay(2000)
                    verifiedPassphrase = ""
                    showMatchErrorText = false
                }
            }
        }
    }

    val matchErrorText = if (showMatchErrorText) {
        if (constraints.requireNumerical) {
            "PIN did not match, try again"
        } else {
            "Passphrase did not mach, try again"
        }
    } else ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = matchErrorText,
            color = Color.Red
        )
    }
}

@Composable
fun EvidenceRequestQuestionMultipleChoiceView(
    evidenceRequest: EvidenceRequestQuestionMultipleChoice,
    onAccept: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        RichText(
            modifier = Modifier.padding(8.dp),
            content = evidenceRequest.message,
            assets = evidenceRequest.assets
        )
    }

    val radioOptions = evidenceRequest.possibleValues
    val radioOptionsKeys = radioOptions.keys.toList()
    val (selectedOption, onOptionSelected) = remember { mutableStateOf(0) }
    Column {
        radioOptions.forEach { entry ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = (entry.key == radioOptionsKeys[selectedOption]),
                        onClick = {
                            onOptionSelected(radioOptionsKeys.indexOf(entry.key))
                        }
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (entry.key == radioOptionsKeys[selectedOption]),
                    onClick = { onOptionSelected(radioOptionsKeys.indexOf(entry.key)) }
                )
                Text(
                    text = entry.value,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Button(onClick = {
            onAccept(radioOptionsKeys[selectedOption])
            // Reset to first choice, for the next time this is used
            onOptionSelected(0)
        }) {
            Text(evidenceRequest.acceptButtonText)
        }
    }

}

@Composable
fun EvidenceRequestWebView(
    evidenceRequest: EvidenceRequestWeb,
    provisioningModel: ProvisioningModel,
) {
    val url = evidenceRequest.url
    val redirectUri = evidenceRequest.redirectUri
    val appSupport = provisioningModel.applicationSupport
    // NB: these scopes will be cancelled when navigating outside of this screen.
    LaunchedEffect(url, redirectUri) {
        // Wait for notifications
        withContext(provisioningModel.coroutineContext) {
            appSupport.collect { notification ->
                if (notification.baseUrl == redirectUri) {
                    handleLanding(appSupport, redirectUri, provisioningModel)
                }
            }
        }
    }
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(url, redirectUri) {
        // Launch the browser
        // TODO: use Chrome Custom Tabs instead?
        uriHandler.openUri(url)
        // Poll as a fallback
        do {
            delay(15.seconds)
        } while(handleLanding(appSupport, redirectUri, provisioningModel))
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Launching browser, continue there",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

private suspend fun handleLanding(
    appSupport: ApplicationSupport,
    redirectUri: String,
    provisioningViewModel: ProvisioningModel
): Boolean {
    val resp = try {
        withContext(provisioningViewModel.coroutineContext) {
            appSupport.getLandingUrlStatus(redirectUri)
        }
    } catch (err: LandingUrlUnknownException) {
        Logger.e(TAG, "landing: $redirectUri unknown: $err")
        provisioningViewModel.provideEvidence(
            evidence = EvidenceResponseWeb("")
        )
        return false
    }
    if (resp == null) {
        // Landing url not navigated to yet.
        return true
    }
    provisioningViewModel.provideEvidence(
        evidence = EvidenceResponseWeb(resp)
    )
    return false
}

@Composable
fun EvidenceRequestOpenid4VpView(
    app: App,
    provisioningModel: ProvisioningModel,
    evidenceRequest: EvidenceRequestOpenid4Vp,
    presentmentModel: PresentmentModel,
    viableCredentials: List<Credential>,
    onNextEvidenceRequest: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val presenting = remember { mutableStateOf(false) }
    if (presenting.value) {
        Presentment(
            appName = platformAppName,
            appIconPainter = painterResource(platformAppIcon),
            presentmentModel = presentmentModel,
            presentmentSource = app.getPresentmentSource(),
            documentTypeRepository = app.documentTypeRepository,
            onPresentmentComplete = { presenting.value = false},
        )
    } else {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Presentation during issuance",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    modifier = Modifier.padding(8.dp),
                    onClick = {
                        presentmentModel.reset()
                        val mechanism = object : DigitalCredentialsPresentmentMechanism(
                            appId = "",
                            webOrigin = evidenceRequest.originUri,
                            protocol = "openid4vp",
                            data = buildJsonObject {
                                put("request", evidenceRequest.request)
                            },
                            document = viableCredentials.first().document
                        ) {
                            override fun sendResponse(protocol: String, data: JsonObject) {
                                coroutineScope.launch {
                                    provisioningModel.provideEvidence(
                                        EvidenceResponseOpenid4Vp(
                                            Json.encodeToString(data)
                                        )
                                    )
                                }
                            }

                            override fun close() {
                                presenting.value = false
                            }
                        }
                        presentmentModel.setConnecting()
                        presentmentModel.setMechanism(mechanism)
                        presenting.value = true
                    }
                ) {
                    Text(text = "Present Credential")
                }
                Button(
                    modifier = Modifier.padding(8.dp),
                    onClick = onNextEvidenceRequest
                ) {
                    Text(text = evidenceRequest.cancelText ?: "Use browser")
                }
            }
        }
    }
}

@Composable
fun EvidenceRequestNotificationPermissionView(
    evidenceRequest: EvidenceRequestNotificationPermission,
    provisioningModel: ProvisioningModel
) {
    // TODO: This is a hack, until this feature is implemented
    LaunchedEffect(true) {
        provisioningModel.provideEvidence(
            evidence = EvidenceResponseNotificationPermission(true)
        )
    }
}
