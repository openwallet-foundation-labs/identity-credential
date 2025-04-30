package com.android.identity.testapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.multipaz.compose.camera.Camera
import org.multipaz.compose.camera.CameraCaptureResolution
import org.multipaz.compose.camera.CameraSelection
import org.multipaz.compose.permissions.rememberCameraPermissionState
import org.multipaz.face_detector.detectFace

private const val TAG = "CameraScreen"

@Composable
fun CameraScreen(
    showToast: (message: String) -> Unit
) {
    val showCameraDialog = remember { mutableStateOf<CameraSelection?>(null) }
    val cameraPermissionState = rememberCameraPermissionState()
    val coroutineScope = rememberCoroutineScope()
    var processedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val showPreview = false

    if (showCameraDialog.value != null) {
        AlertDialog(
            modifier = Modifier.wrapContentSize(),
            title = { Text(text = "Camera dialog") },
            text = {
                Column(
                    Modifier.wrapContentSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.wrapContentSize()) {
                        Camera(
                            modifier =
                                if (showPreview) {
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(3f / 4f)
                                }
                                else {
                                    Modifier
                                },
                            cameraSelection = showCameraDialog.value!!,
                            showCameraPreview = showPreview,
                            captureResolution = CameraCaptureResolution.LOW,
                            onFrameCaptured = { frame ->
                                // Generate another bitmap from the frame (e.g., processed image)
                                //   and use it as the overlay bitmap.
                                processedBitmap = detectFace(frame.data)
                                null // Optional return value for the Camera code consumption.
                            }
                        )

                        // Display the PROCESSED bitmap (with recognized facial features).
                        processedBitmap?.let { processedBitmap ->
                            androidx.compose.foundation.Image(
                                bitmap = processedBitmap,
                                contentDescription = "Processed Overlay",
                                modifier = Modifier.wrapContentSize().fillMaxWidth()
                            )
                        }
                    }
                }
            },
            onDismissRequest = { showCameraDialog.value = null },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showCameraDialog.value = null
                }) {
                    Text(text = "Close")
                }
            }
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
                    TextButton(onClick = {
                        showCameraDialog.value = CameraSelection.DEFAULT_FRONT_CAMERA
                    }) {
                        Text("Start capturing video (Front Camera)")
                    }
                }

                item {
                    TextButton(onClick = {
                        showCameraDialog.value = CameraSelection.DEFAULT_BACK_CAMERA
                    }) {
                        Text("Start capturing video (Back Camera)")
                    }
                }
            }
        }
    }
}
