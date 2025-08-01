package org.multipaz.testapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.time.Clock
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
    val captureWithPreview = remember { mutableStateOf<Pair<CameraSelection, CameraCaptureResolution>?>(null) }
    val captureWithoutPreview = remember { mutableStateOf<Pair<CameraSelection, CameraCaptureResolution>?>(null) }
    val cameraPermissionState = rememberCameraPermissionState()
    val coroutineScope = rememberCoroutineScope()

    captureWithPreview.value?.let {
        val lastFrameReceived = remember { mutableStateOf<CameraFrame?>(null) }
        AlertDialog(
            modifier = Modifier.wrapContentSize(),
            title = { Text(text = "Camera with Preview") },
            text = {
                Column(
                    Modifier.wrapContentSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Camera preview with a canvas on top with circles drawn " +
                                "in the coordinate system of the captured frame."
                    )
                    Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
                        Camera(
                            modifier = Modifier.fillMaxWidth(),
                            cameraSelection = it.first,
                            captureResolution = it.second,
                            showCameraPreview = true,
                            onFrameCaptured = { incomingVideoFrame ->
                                lastFrameReceived.value = incomingVideoFrame
                            }
                        )
                        Canvas(modifier = Modifier.fillMaxWidth()) {
                            if (lastFrameReceived.value != null) {
                                val tm = lastFrameReceived.value!!.previewTransformation
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
            onDismissRequest = { captureWithPreview.value = null },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    captureWithPreview.value = null
                }) {
                    Text(text = "Close")
                }
            }
        )
    }

    captureWithoutPreview.value?.let {
        val lastBitmapCaptured = remember { mutableStateOf<ImageBitmap?>(null) }
        AlertDialog(
            modifier = Modifier.wrapContentSize(),
            title = { Text(text = "Camera without Preview") },
            text = {
                Column(
                    Modifier.wrapContentSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Camera capture without preview. The captured image is rendered " +
                                "as an ImageBitmap without any transformations applied."
                    )
                    Camera(
                        cameraSelection = it.first,
                        captureResolution = it.second,
                        showCameraPreview = false,
                        onFrameCaptured = { incomingVideoFrame ->
                            val bitmap = incomingVideoFrame.cameraImage.toImageBitmap()
                            Logger.i(TAG, "Received bitmap of width ${bitmap.width} height ${bitmap.height}")
                            lastBitmapCaptured.value = bitmap
                        }
                    )
                    lastBitmapCaptured.value?.let {
                        Image(
                            modifier = Modifier.fillMaxWidth(),
                            bitmap = it,
                            contentDescription = "Camera frame",
                        )
                    }
                }
            },
            onDismissRequest = { captureWithoutPreview.value = null },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    captureWithoutPreview.value = null
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
                        captureWithPreview.value = Pair(CameraSelection.DEFAULT_FRONT_CAMERA, CameraCaptureResolution.LOW)
                    }) { Text("Capture with Preview (Front Camera, Low Res)") }
                }
                item {
                    TextButton(onClick = {
                        captureWithPreview.value = Pair(CameraSelection.DEFAULT_FRONT_CAMERA, CameraCaptureResolution.MEDIUM)
                    }) { Text("Capture with Preview (Front Camera, Medium Res)") }
                }
                item {
                    TextButton(onClick = {
                        captureWithPreview.value = Pair(CameraSelection.DEFAULT_FRONT_CAMERA, CameraCaptureResolution.HIGH)
                    }) { Text("Capture with Preview (Front Camera, High Res)") }
                }

                item {
                    GroupDivider()
                }

                item {
                    TextButton(onClick = {
                        captureWithPreview.value = Pair(CameraSelection.DEFAULT_BACK_CAMERA, CameraCaptureResolution.LOW)
                    }) { Text("Capture with Preview (Back Camera, Low Res)") }
                }
                item {
                    TextButton(onClick = {
                        captureWithPreview.value = Pair(CameraSelection.DEFAULT_BACK_CAMERA, CameraCaptureResolution.MEDIUM)
                    }) { Text("Capture with Preview (Back Camera, Medium Res)") }
                }
                item {
                    TextButton(onClick = {
                        captureWithPreview.value = Pair(CameraSelection.DEFAULT_BACK_CAMERA, CameraCaptureResolution.HIGH)
                    }) { Text("Capture with Preview (Back Camera, High Res)") }
                }

                item {
                    GroupDivider()
                }

                item {
                    TextButton(onClick = {
                        captureWithoutPreview.value = Pair(CameraSelection.DEFAULT_FRONT_CAMERA, CameraCaptureResolution.LOW)
                    }) { Text("Capture without Preview (Front Camera, Low Res)") }
                }
                item {
                    TextButton(onClick = {
                        captureWithoutPreview.value = Pair(CameraSelection.DEFAULT_FRONT_CAMERA, CameraCaptureResolution.MEDIUM)
                    }) { Text("Capture without Preview (Front Camera, Medium Res)") }
                }
                item {
                    TextButton(onClick = {
                        captureWithoutPreview.value = Pair(CameraSelection.DEFAULT_FRONT_CAMERA, CameraCaptureResolution.HIGH)
                    }) { Text("Capture without Preview (Front Camera, High Res)") }
                }

                item {
                    GroupDivider()
                }

                item {
                    TextButton(onClick = {
                        captureWithoutPreview.value = Pair(CameraSelection.DEFAULT_BACK_CAMERA, CameraCaptureResolution.LOW)
                    }) { Text("Capture without Preview (Back Camera, Low Res)") }
                }
                item {
                    TextButton(onClick = {
                        captureWithoutPreview.value = Pair(CameraSelection.DEFAULT_BACK_CAMERA, CameraCaptureResolution.MEDIUM)
                    }) { Text("Capture without Preview (Back Camera, Medium Res)") }
                }
                item {
                    TextButton(onClick = {
                        captureWithoutPreview.value = Pair(CameraSelection.DEFAULT_BACK_CAMERA, CameraCaptureResolution.HIGH)
                    }) { Text("Capture without Preview (Back Camera, High Res)") }
                }
            }
        }
    }
}


