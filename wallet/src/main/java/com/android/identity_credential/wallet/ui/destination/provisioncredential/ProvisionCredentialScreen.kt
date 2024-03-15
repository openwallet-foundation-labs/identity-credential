package com.android.identity_credential.wallet.ui.destination.provisioncredential

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.identity.credential.CredentialStore
import com.android.identity.issuance.IssuingAuthorityRepository
import com.android.identity.issuance.evidence.EvidenceRequestIcaoNfcTunnel
import com.android.identity.issuance.evidence.EvidenceRequestIcaoPassiveAuthentication
import com.android.identity.issuance.evidence.EvidenceRequestMessage
import com.android.identity.issuance.evidence.EvidenceRequestQuestionMultipleChoice
import com.android.identity.issuance.evidence.EvidenceRequestQuestionString
import com.android.identity.issuance.evidence.EvidenceResponseQuestionMultipleChoice
import com.android.identity.issuance.evidence.EvidenceResponseQuestionString
import com.android.identity_credential.wallet.PermissionTracker
import com.android.identity_credential.wallet.ProvisioningViewModel
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.navigation.WalletDestination
import com.android.identity_credential.wallet.ui.ScreenWithAppBar


@Composable
fun ProvisionCredentialScreen(
    provisioningViewModel: ProvisioningViewModel,
    onNavigate: (String) -> Unit,
    permissionTracker: PermissionTracker,
    issuingAuthorityRepository: IssuingAuthorityRepository,
    credentialStore: CredentialStore
) {
    ScreenWithAppBar(title = stringResource(R.string.provisioning_title), navigationIcon = {
        if (provisioningViewModel.state.value != ProvisioningViewModel.State.PROOFING_COMPLETE) {
            IconButton(
                onClick = {
                    onNavigate(WalletDestination.PopBackStack.route)
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.accessibility_go_back_icon)
                )
            }
        }
    }
    ) {
        when (provisioningViewModel.state.value) {
            ProvisioningViewModel.State.IDLE -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        modifier = Modifier.padding(8.dp),
                        text = stringResource(R.string.provisioning_idle)
                    )
                }
            }

            ProvisioningViewModel.State.CREDENTIAL_REGISTRATION -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        modifier = Modifier.padding(8.dp),
                        text = stringResource(R.string.provisioning_creating_key)
                    )
                }
            }

            ProvisioningViewModel.State.EVIDENCE_REQUESTS_READY -> {
                // TODO: for now we just consider the first evidence request
                val evidenceRequest = provisioningViewModel.evidenceRequests!![0]
                when (evidenceRequest) {
                    is EvidenceRequestQuestionString -> {
                        EvidenceRequestQuestionStringView(
                            evidenceRequest,
                            onAccept = { inputString ->
                                provisioningViewModel.provideEvidence(
                                    evidence = EvidenceResponseQuestionString(inputString),
                                    issuingAuthorityRepository = issuingAuthorityRepository,
                                    credentialStore = credentialStore
                                )
                            }
                        )
                    }

                    is EvidenceRequestMessage -> {
                        EvidenceRequestMessageView(
                            evidenceRequest,
                            provisioningViewModel = provisioningViewModel,
                            issuingAuthorityRepository = issuingAuthorityRepository,
                            credentialStore = credentialStore
                        )
                    }

                    is EvidenceRequestQuestionMultipleChoice -> {
                        EvidenceRequestQuestionMultipleChoiceView(
                            evidenceRequest,
                            onAccept = { selectedOption ->
                                provisioningViewModel.provideEvidence(
                                    evidence = EvidenceResponseQuestionMultipleChoice(selectedOption),
                                    issuingAuthorityRepository = issuingAuthorityRepository,
                                    credentialStore = credentialStore
                                )
                            }
                        )
                    }

                    is EvidenceRequestIcaoPassiveAuthentication -> {
                        EvidenceRequestIcaoPassiveAuthenticationView(
                            evidenceRequest = evidenceRequest,
                            provisioningViewModel = provisioningViewModel,
                            issuingAuthorityRepository = issuingAuthorityRepository,
                            credentialStore = credentialStore,
                            permissionTracker = permissionTracker
                        )
                    }

                    is EvidenceRequestIcaoNfcTunnel -> {
                        EvidenceRequestIcaoNfcTunnelView(
                            evidenceRequest,
                            provisioningViewModel = provisioningViewModel,
                            permissionTracker = permissionTracker
                        )
                    }
                }
            }

            ProvisioningViewModel.State.SUBMITTING_EVIDENCE -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        text = stringResource(R.string.provisioning_submitting)
                    )
                }
            }

            ProvisioningViewModel.State.PROOFING_COMPLETE -> {
                onNavigate(
                    WalletDestination.PopBackStack
                        .getRouteWithArguments(
                            listOf(
                                Pair(
                                    WalletDestination.PopBackStack.Argument.ROUTE,
                                    WalletDestination.Main.route
                                ),
                                Pair(
                                    WalletDestination.PopBackStack.Argument.INCLUSIVE,
                                    false
                                )
                            )
                        )
                )
            }

            ProvisioningViewModel.State.FAILED -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        text = stringResource(R.string.provisioning_error,
                            provisioningViewModel.error.toString())
                    )
                }
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
                        text = stringResource(R.string.provisioning_unexpected,
                            provisioningViewModel.state.value)
                    )
                }
            }
        }
    }
}