package com.android.identity_credential.wallet.ui.destination.provisioncredential

import android.Manifest
import android.app.Activity
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.identity.credential.CredentialStore
import com.android.identity.issuance.IssuingAuthorityRepository
import com.android.identity.issuance.evidence.EvidenceRequestIcaoNfcTunnel
import com.android.identity.issuance.evidence.EvidenceRequestIcaoPassiveAuthentication
import com.android.identity.issuance.evidence.EvidenceRequestMessage
import com.android.identity.issuance.evidence.EvidenceRequestQuestionMultipleChoice
import com.android.identity.issuance.evidence.EvidenceRequestQuestionString
import com.android.identity.issuance.evidence.EvidenceResponseIcaoPassiveAuthentication
import com.android.identity.issuance.evidence.EvidenceResponseMessage
import com.android.identity.util.Logger
import com.android.identity_credential.mrtd.MrtdMrzScanner
import com.android.identity_credential.mrtd.MrtdNfc
import com.android.identity_credential.mrtd.MrtdNfcDataReader
import com.android.identity_credential.mrtd.MrtdNfcReader
import com.android.identity_credential.mrtd.MrtdNfcScanner
import com.android.identity_credential.wallet.NfcTunnelScanner
import com.android.identity_credential.wallet.PermissionTracker
import com.android.identity_credential.wallet.ProvisioningViewModel
import com.android.identity_credential.wallet.util.getActivity
import kotlinx.coroutines.launch


const val TAG_ER = "EvidenceRequest"

@Composable
fun EvidenceRequestMessageView(
    evidenceRequest: EvidenceRequestMessage,
    provisioningViewModel: ProvisioningViewModel,
    issuingAuthorityRepository: IssuingAuthorityRepository,
    credentialStore: CredentialStore
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            modifier = Modifier.padding(8.dp),
            text = evidenceRequest.message
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        if (evidenceRequest.rejectButtonText != null) {
            Button(onClick = {
                provisioningViewModel.provideEvidence(
                    evidence = EvidenceResponseMessage(false),
                    issuingAuthorityRepository = issuingAuthorityRepository,
                    credentialStore = credentialStore
                )
            }) {
                Text(evidenceRequest.rejectButtonText)
            }
        }
        Button(onClick = {
            provisioningViewModel.provideEvidence(
                evidence = EvidenceResponseMessage(true),
                issuingAuthorityRepository = issuingAuthorityRepository,
                credentialStore = credentialStore
            )
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
        Text(
            modifier = Modifier.padding(8.dp),
            text = evidenceRequest.message
        )
    }

    var inputText by remember { mutableStateOf(evidenceRequest.defaultValue) }
    TextField(
        value = inputText,
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
fun EvidenceRequestQuestionMultipleChoiceView(
    evidenceRequest: EvidenceRequestQuestionMultipleChoice,
    onAccept: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            modifier = Modifier.padding(8.dp),
            text = evidenceRequest.message
        )
    }

    val radioOptions = evidenceRequest.possibleValues
    val (selectedOption, onOptionSelected) = remember {
        mutableStateOf(radioOptions.keys.iterator().next())
    }
    Column {
        radioOptions.forEach { entry ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = (entry.key == selectedOption),
                        onClick = {
                            onOptionSelected(entry.key)
                        }
                    )
                    .padding(horizontal = 16.dp)
            ) {
                RadioButton(
                    selected = (entry.key == selectedOption),
                    onClick = { onOptionSelected(entry.key) }
                )
                Text(
                    text = entry.value,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Button(onClick = {
            onAccept(selectedOption)
        }) {
            Text(evidenceRequest.acceptButtonText)
        }
    }

}

@Composable
fun EvidenceRequestIcaoPassiveAuthenticationView(
    evidenceRequest: EvidenceRequestIcaoPassiveAuthentication,
    provisioningViewModel: ProvisioningViewModel,
    issuingAuthorityRepository: IssuingAuthorityRepository,
    credentialStore: CredentialStore,
    permissionTracker: PermissionTracker
) {
    EvidenceRequestIcaoView(
        MrtdNfcDataReader(evidenceRequest.requestedDataGroups),
        permissionTracker
    ) { nfcData ->
        provisioningViewModel.provideEvidence(
            evidence = EvidenceResponseIcaoPassiveAuthentication(nfcData.dataGroups, nfcData.sod),
            issuingAuthorityRepository = issuingAuthorityRepository,
            credentialStore = credentialStore
        )
    }
}

@Composable
fun EvidenceRequestIcaoNfcTunnelView(
    evidenceRequest: EvidenceRequestIcaoNfcTunnel,
    provisioningViewModel: ProvisioningViewModel,
    permissionTracker: PermissionTracker
) {
    EvidenceRequestIcaoView(NfcTunnelScanner(provisioningViewModel), permissionTracker) {
        provisioningViewModel.finishTunnel()
    }
}

@Composable
fun <ResultT> EvidenceRequestIcaoView(
    reader: MrtdNfcReader<ResultT>,
    permissionTracker: PermissionTracker,
    onResult: (ResultT) -> Unit
) {
    val requiredPermissions = listOf(Manifest.permission.CAMERA, Manifest.permission.NFC)
    permissionTracker.PermissionCheck(requiredPermissions) {
        var visualScan by remember { mutableStateOf(true) }  // start with visual scan
        var status by remember { mutableStateOf<MrtdNfc.Status>(MrtdNfc.Initial) }
        val scope = rememberCoroutineScope()
        if (visualScan) {
            AndroidView(
                factory = { context ->
                    PreviewView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_START
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        post {
                            scope.launch {
                                val passportCapture =
                                    context.getActivity()?.let { MrtdMrzScanner(it) }
                                val passportNfcScanner =
                                    MrtdNfcScanner(context.getActivity() as Activity)

                                try {
                                    val mrzInfo = passportCapture!!.readFromCamera(surfaceProvider)
                                    status = MrtdNfc.Initial
                                    visualScan = false
                                    val result =
                                        passportNfcScanner.scanCard(mrzInfo, reader) { st ->
                                            status = st
                                        }
                                    onResult(result)
                                } catch (err: Exception) {
                                    Logger.e(TAG_ER, "Error scanning MRTD: $err")
                                }
                            }
                        }
                    }
                }
            )
        } else {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        when (status) {
                            is MrtdNfc.Initial -> "Waiting to scan"
                            else -> "Reading..."
                        }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        when (status) {
                            is MrtdNfc.Initial -> "Initial"
                            is MrtdNfc.Connected -> "Connected"
                            is MrtdNfc.AttemptingPACE -> "Attempting PACE"
                            is MrtdNfc.PACESucceeded -> "PACE Succeeded"
                            is MrtdNfc.PACENotSupported -> "PACE Not Supported"
                            is MrtdNfc.PACEFailed -> "PACE Not Supported"
                            is MrtdNfc.AttemptingBAC -> "Attempting BAC"
                            is MrtdNfc.BACSucceeded -> "BAC Succeeded"
                            is MrtdNfc.ReadingData -> {
                                val s = status as MrtdNfc.ReadingData
                                "Reading: ${s.progressPercent}%"
                            }

                            is MrtdNfc.TunnelAuthenticating -> {
                                val s = status as MrtdNfc.TunnelAuthenticating
                                "Establishing secure tunnel: ${s.progressPercent}%"
                            }

                            is MrtdNfc.TunnelReading -> {
                                val s = status as MrtdNfc.TunnelReading
                                "Reading data through tunnel: ${s.progressPercent}%"
                            }

                            is MrtdNfc.Finished -> "Finished"
                        }
                    )
                }
            }
        }
    }
}