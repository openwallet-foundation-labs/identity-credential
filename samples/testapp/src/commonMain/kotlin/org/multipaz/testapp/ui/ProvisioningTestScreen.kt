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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.compose.resources.painterResource
import org.multipaz.compose.PassphraseEntryField
import org.multipaz.compose.presentment.Presentment
import org.multipaz.compose.webview.RichText
import org.multipaz.credential.Credential
import org.multipaz.models.presentment.DigitalCredentialsPresentmentMechanism
import org.multipaz.models.presentment.PresentmentModel
import org.multipaz.models.provisioning.ProvisioningModel
import org.multipaz.provision.AuthorizationChallenge
import org.multipaz.provision.AuthorizationResponse
import org.multipaz.testapp.App
import org.multipaz.testapp.platformAppIcon
import org.multipaz.testapp.platformAppName
import org.multipaz.testapp.provisioning.ProvisioningSupport
import org.multipaz.util.Logger
import kotlin.time.Duration.Companion.seconds

@Composable
fun ProvisioningTestScreen(
    app: App,
    provisioningModel: ProvisioningModel,
    provisioningSupport: ProvisioningSupport
) {
    val provisioningState = provisioningModel.state.collectAsState(ProvisioningModel.Initial).value
    Column {
        if (provisioningState is ProvisioningModel.Authorizing) {
            Authorize(
                app,
                provisioningModel,
                provisioningState.authorizationChallenges,
                provisioningSupport
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
                ProvisioningModel.ProcessingAuthorization -> "Processing authorization..."
                ProvisioningModel.Authorized -> "Authorized"
                ProvisioningModel.RequestingCredentials -> "Requesting credentials..."
                ProvisioningModel.CredentialsIssued -> "Credentials issued"
                is ProvisioningModel.Error -> throw IllegalStateException()
                is ProvisioningModel.Authorizing -> throw IllegalStateException()
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
private fun Authorize(
    app: App,
    provisioningModel: ProvisioningModel,
    challenges: List<AuthorizationChallenge>,
    provisioningSupport: ProvisioningSupport
) {
    when (val challenge = challenges.first()) {
        is AuthorizationChallenge.OAuth -> {
            EvidenceRequestWebView(
                evidenceRequest = challenge,
                provisioningModel = provisioningModel,
                provisioningSupport = provisioningSupport
            )
        }
    }
}

private const val TAG = "ProvisioningTestScreen"

@Composable
fun EvidenceRequestWebView(
    evidenceRequest: AuthorizationChallenge.OAuth,
    provisioningModel: ProvisioningModel,
    provisioningSupport: ProvisioningSupport
) {
    // NB: these scopes will be cancelled when navigating outside of this screen.
    LaunchedEffect(evidenceRequest.url) {
        val invokedUrl = provisioningSupport.waitForAppLinkInvocation(evidenceRequest.state)
        provisioningModel.provideAuthorizationResponse(
            AuthorizationResponse.OAuth(evidenceRequest.id, invokedUrl)
        )
    }
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(evidenceRequest.url) {
        // Launch the browser
        // TODO: use Chrome Custom Tabs instead?
        uriHandler.openUri(evidenceRequest.url)
        // Poll as a fallback
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

