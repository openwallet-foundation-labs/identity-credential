package org.multipaz.testapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.multipaz.compose.PassphraseEntryField
import org.multipaz.models.provisioning.ProvisioningModel
import org.multipaz.provisioning.AuthorizationChallenge
import org.multipaz.provisioning.AuthorizationException
import org.multipaz.provisioning.AuthorizationResponse
import org.multipaz.securearea.PassphraseConstraints
import org.multipaz.testapp.provisioning.ProvisioningSupport

@Composable
fun ProvisioningTestScreen(
    provisioningModel: ProvisioningModel,
    provisioningSupport: ProvisioningSupport,
) {
    val provisioningState = provisioningModel.state.collectAsState(ProvisioningModel.Idle).value
    Column {
        when (provisioningState) {
            is ProvisioningModel.Authorizing -> {
                Authorize(
                    provisioningModel,
                    provisioningState.authorizationChallenges,
                    provisioningSupport,
                )
            }

            is ProvisioningModel.Error -> if (provisioningState.err is AuthorizationException) {
                Text(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    text = "Authorization failed"
                )
                val err = provisioningState.err as AuthorizationException
                Text(
                    modifier = Modifier.padding(4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    text = "Error code: ${err.code}"
                )
                if (err.description != null) {
                    Text(
                        modifier = Modifier.padding(4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        text = err.description!!
                    )
                }
            } else {
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
            }

            else -> {
                val text = when (provisioningState) {
                    ProvisioningModel.Idle -> "Initializing..."
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
}

@Composable
private fun Authorize(
    provisioningModel: ProvisioningModel,
    challenges: List<AuthorizationChallenge>,
    provisioningSupport: ProvisioningSupport
) {
    when (val challenge = challenges.first()) {
        is AuthorizationChallenge.OAuth ->
            EvidenceRequestWebView(
                evidenceRequest = challenge,
                provisioningModel = provisioningModel,
                provisioningSupport = provisioningSupport
            )
        is AuthorizationChallenge.SecretText ->
            EvidenceRequestSecretText(
                challenge = challenge,
                provisioningModel = provisioningModel
            )
    }
}

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

@Composable
fun EvidenceRequestSecretText(
    challenge: AuthorizationChallenge.SecretText,
    provisioningModel: ProvisioningModel
) {
    val coroutineScope = rememberCoroutineScope()
    val passphraseRequest = challenge.request
    val constraints = PassphraseConstraints(
        minLength = passphraseRequest.length ?: 1,
        maxLength = passphraseRequest.length ?: 10,
        passphraseRequest.isNumeric
    )
    Column {
        Text(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(8.dp),
            style = MaterialTheme.typography.titleLarge,
            text = passphraseRequest.description
        )
        if (challenge.retry) {
            Text(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(8.dp),
                style = MaterialTheme.typography.titleLarge,
                text = "Please retry"
            )
        }
        PassphraseEntryField(
            constraints = constraints,
            checkWeakPassphrase = false
        ) { passphrase, meetsRequirements, donePressed ->
            if (meetsRequirements && donePressed) {
                coroutineScope.launch {
                    provisioningModel.provideAuthorizationResponse(
                        AuthorizationResponse.SecretText(
                            id = challenge.id,
                            secret = passphrase
                        )
                    )
                }
            }
        }
    }
}
