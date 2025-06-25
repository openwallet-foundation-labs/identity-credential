package org.multipaz.testapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import multipazproject.samples.testapp.generated.resources.Res
import multipazproject.samples.testapp.generated.resources.selfie_check_dialog_title
import multipazproject.samples.testapp.generated.resources.selfie_check_subtitle
import org.jetbrains.compose.resources.stringResource
import org.multipaz.compose.camera.CameraCaptureResolution
import org.multipaz.compose.camera.CameraCaptureResolution.MEDIUM
import org.multipaz.compose.permissions.rememberCameraPermissionState
import org.multipaz.selfiecheck.SelfieCheck
import org.multipaz.selfiecheck.SelfieCheckViewModel
import org.multipaz.util.Logger

private const val TAG = "SelfieCheckScreen"

@Composable
fun SelfieCheckScreen(
    showToast: (message: String) -> Unit
) {
    val identityIssuer = "Utopia Department of Motor Vehicles"
    val showSelfieDialog = remember { mutableStateOf<CaptureConfig?>(null) }
    val cameraPermissionState = rememberCameraPermissionState()
    val coroutineScope = rememberCoroutineScope()
    val viewModel: SelfieCheckViewModel = remember { SelfieCheckViewModel(identityIssuer) }

    if (showSelfieDialog.value != null) {
        val isLandscapeFromViewModel by viewModel.isLandscape.collectAsState()
        Logger.d(TAG, "isLandscapeFromViewModel: $isLandscapeFromViewModel")

        AlertDialog(
            modifier = Modifier
                .fillMaxWidth(if (isLandscapeFromViewModel) 0.8f else 1f)
                .padding(if (isLandscapeFromViewModel) 4.dp else 4.dp),
            title = { Text(text = stringResource(Res.string.selfie_check_dialog_title)) },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SelfieCheck(
                        modifier = Modifier.fillMaxWidth(),
                        onVerificationComplete = {
                            showSelfieDialog.value = null
                            viewModel.resetForNewCheck()
                            // Use the `viewModel.capturedFaceImage` here for further processing (before VM destroyed).
                        },
                        viewModel = viewModel,
                        identityIssuer = identityIssuer
                    )
                }
            },
            onDismissRequest = { showSelfieDialog.value = null },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showSelfieDialog.value = null
                    viewModel.resetForNewCheck()
                }) {
                    Text(text = "Close")
                }
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false, // Crucial for any custom width.
                dismissOnClickOutside = true,
                dismissOnBackPress = true
            )
        )
    }

    if (!cameraPermissionState.isGranted) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        cameraPermissionState.launchPermissionRequest()
                    }
                }
            ) {
                Text("Request Camera permission")
            }
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            LazyColumn(
                modifier = Modifier.padding(8.dp)
            ) {
                item {
                    val cameraConfig = CaptureConfig(MEDIUM)

                    TextButton(onClick = {
                        showSelfieDialog.value = cameraConfig
                    }) { Text(stringResource(Res.string.selfie_check_subtitle)) }
                }
            }
        }
    }
}

/** Camera parameters variants to display in the UI. */
private data class CaptureConfig(
    val captureResolution: CameraCaptureResolution
)

