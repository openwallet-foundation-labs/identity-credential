package com.android.identity.testapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.multipaz.compose.camera.Camera
import org.multipaz.compose.camera.CameraCaptureResolution
import org.multipaz.compose.camera.CameraFrame
import org.multipaz.compose.camera.CameraSelection
import org.multipaz.compose.permissions.rememberCameraPermissionState
import org.multipaz.util.Logger

private const val TAG = "CameraScreen"

@Composable
fun CameraScreen(
    showToast: (message: String) -> Unit
) {
    val showCameraDialog = remember { mutableStateOf<CameraSelection?>(null) }
    val cameraPermissionState = rememberCameraPermissionState()
    val coroutineScope = rememberCoroutineScope()
    val lastFrameReceived = remember { mutableStateOf<CameraFrame?>(null) }
    val showPreview = true

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
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Camera(
                            modifier = Modifier.fillMaxWidth(),
                            cameraSelection = showCameraDialog.value!!,
                            showCameraPreview = showPreview,
                            captureResolution = CameraCaptureResolution.LOW,
                            onFrameCaptured = { incomingVideoFrame ->
                                lastFrameReceived.value = incomingVideoFrame
                            }
                        )
                        Canvas(modifier = Modifier.fillMaxWidth()) {
                            if (lastFrameReceived.value != null) {
                                val tm = lastFrameReceived.value!!.transformation
                                val t = Clock.System.now().toEpochMilliseconds() % 1000
                                val frameWidth = lastFrameReceived.value!!.width.toFloat()
                                val frameHeight = lastFrameReceived.value!!.height.toFloat()
                                val dx = t * frameWidth / 2000f
                                val dy = t * frameHeight / 2000f
                                drawCircle(
                                    color = Color.Red,
                                    center = tm.map(Offset(0f, 0f)),
                                    radius = 15f
                                )
                                drawCircle(
                                    color = Color.Green,
                                    center = tm.map(Offset(frameWidth, 0f)),
                                    radius = 15f
                                )
                                drawCircle(
                                    color = Color.Blue,
                                    center = tm.map(Offset(frameWidth - dx, frameHeight - dy)),
                                    radius = 15f
                                )
                                drawCircle(
                                    color = Color.Cyan,
                                    center = tm.map(Offset(0f, frameHeight)),
                                    radius = 15f
                                )
                            }
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
