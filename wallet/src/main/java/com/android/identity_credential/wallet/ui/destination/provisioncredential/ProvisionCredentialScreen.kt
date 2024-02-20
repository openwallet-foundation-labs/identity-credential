package com.android.identity_credential.wallet.ui.destination.provisioncredential

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import com.android.identity.issuance.evidence.EvidenceType
import com.android.identity_credential.wallet.PermissionTracker
import com.android.identity_credential.wallet.ProvisioningViewModel
import com.android.identity_credential.wallet.ScreenWithAppBar
import com.android.identity_credential.wallet.navigation.WalletDestination


@Composable
fun ProvisionCredentialScreen(
    provisioningViewModel: ProvisioningViewModel,
    onNavigate: (String) -> Unit,
    permissionTracker: PermissionTracker,
    issuingAuthorityRepository: IssuingAuthorityRepository,
    credentialStore: CredentialStore
) {
    ScreenWithAppBar(title = "Provisioning", navigationIcon = {
        if (provisioningViewModel.state.value != ProvisioningViewModel.State.PROOFING_COMPLETE) {
            IconButton(
                onClick = {
                    onNavigate(WalletDestination.PopBackStack.route)
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back Arrow"
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
                        text = "TODO: Idle"
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
                        text = "TODO: Creating CredentialKey"
                    )
                }
            }

            ProvisioningViewModel.State.EVIDENCE_REQUESTS_READY -> {
                // TODO: for now we just consider the first evidence request
                val evidenceRequest = provisioningViewModel.evidenceRequests!![0]
                when (evidenceRequest.evidenceType) {
                    EvidenceType.QUESTION_STRING -> {
                        EvidenceRequestQuestionStringView(
                            evidenceRequest as EvidenceRequestQuestionString,
                            onAccept = { inputString ->
                                provisioningViewModel.provideEvidence(
                                    evidence = EvidenceResponseQuestionString(inputString),
                                    issuingAuthorityRepository = issuingAuthorityRepository,
                                    credentialStore = credentialStore
                                )
                            }
                        )
                    }

                    EvidenceType.MESSAGE -> {
                        EvidenceRequestMessageView(
                            evidenceRequest as EvidenceRequestMessage,
                            provisioningViewModel = provisioningViewModel,
                            issuingAuthorityRepository = issuingAuthorityRepository,
                            credentialStore = credentialStore
                        )
                    }

                    EvidenceType.QUESTION_MULTIPLE_CHOICE -> {
                        EvidenceRequestQuestionMultipleChoiceView(
                            evidenceRequest as EvidenceRequestQuestionMultipleChoice,
                            onAccept = { selectedOption ->
                                provisioningViewModel.provideEvidence(
                                    evidence = EvidenceResponseQuestionMultipleChoice(selectedOption),
                                    issuingAuthorityRepository = issuingAuthorityRepository,
                                    credentialStore = credentialStore
                                )
                            }
                        )
                    }

                    EvidenceType.ICAO_9303_PASSIVE_AUTHENTICATION -> {
                        EvidenceRequestIcaoPassiveAuthenticationView(
                            evidenceRequest = evidenceRequest as EvidenceRequestIcaoPassiveAuthentication,
                            provisioningViewModel = provisioningViewModel,
                            issuingAuthorityRepository = issuingAuthorityRepository,
                            credentialStore = credentialStore,
                            permissionTracker = permissionTracker
                        )
                    }

                    EvidenceType.ICAO_9303_NFC_TUNNEL -> {
                        EvidenceRequestIcaoNfcTunnelView(
                            evidenceRequest as EvidenceRequestIcaoNfcTunnel,
                            provisioningViewModel = provisioningViewModel,
                            permissionTracker = permissionTracker
                        )
                    }

                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                modifier = Modifier.padding(8.dp),
                                text = "Unknown evidence type ${evidenceRequest.evidenceType}"
                            )
                        }
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
                        text = "TODO: Submitting evidence"
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
                        text = "Something went wrong: ${provisioningViewModel.error}"
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
                        text = "Unexpected state: ${provisioningViewModel.state.value}"
                    )
                }
            }
        }
    }
}