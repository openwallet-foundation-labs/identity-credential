package com.android.identity.testapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.multipaz.compose.camera.Camera
import org.multipaz.compose.camera.CameraSelection
import org.multipaz.compose.permissions.rememberCameraPermissionState
import org.multipaz.util.Logger

private const val TAG = "CameraScreen"

private sealed class CameraScreenState {
    data object CameraOptions : CameraScreenState()
    data class CameraPreview(val cameraSelection: CameraSelection) : CameraScreenState()
}

@Composable
fun CameraScreen(
    showToast: (message: String) -> Unit,
) {
    val cameraPermissionState = rememberCameraPermissionState()
    val coroutineScope = rememberCoroutineScope()
    var cameraScreenState: CameraScreenState by remember { mutableStateOf(CameraScreenState.CameraOptions) }

    if (!cameraPermissionState.isGranted) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
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
                Text("Request CameraEngine permission")
            }
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (cameraScreenState) {
                is CameraScreenState.CameraOptions ->
                    CameraOptionsScreen(
                        onPreviewCamera = { cameraSelection ->
                            cameraScreenState = CameraScreenState.CameraPreview(cameraSelection)
                        }
                    )

                is CameraScreenState.CameraPreview -> {
                    CameraPreviewCase(
                        cameraSelection = (cameraScreenState as CameraScreenState.CameraPreview).cameraSelection,
                        onNavigateBack = {
                            cameraScreenState = CameraScreenState.CameraOptions
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraOptionsScreen(
    onPreviewCamera: (CameraSelection) -> Unit,
) {
    val cameraEngineOptions = listOf(
        "Front CameraEngine Preview",
        "Back CameraEngine Preview",
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp)
    ) {
        cameraEngineOptions.forEach { option ->
            TextButton(
                onClick = {
                    when (option) {
                        cameraEngineOptions[0] -> onPreviewCamera(CameraSelection.DEFAULT_FRONT_CAMERA)
                        cameraEngineOptions[1] -> onPreviewCamera(CameraSelection.DEFAULT_BACK_CAMERA)
                    }
                },
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(option)
            }
        }
    }
}

@Composable
private fun CameraPreviewCase(
    cameraSelection: CameraSelection,
    onNavigateBack: () -> Unit
) {
    // Trigger recomposition when camera is fully initialized
    var cameraInitialized by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { onNavigateBack() }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                Text(
                    text = "CameraEngine preview",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )

                Camera(
                    modifier = if (cameraInitialized) Modifier.fillMaxWidth() else Modifier.fillMaxSize(),
                    cameraConfiguration = {
                        setCameraLens(cameraSelection)
                        showPreview(cameraPreview = true)
                    },
                    onCameraReady = {
                        cameraInitialized = true
                        Logger.d(TAG, "CameraEngine ready")
                    }
                )
				}
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        cameraInitialized = false
                        onNavigateBack()
                    }) {
                        Text(text = "Close")
                    }
                }
        }
    }
}
