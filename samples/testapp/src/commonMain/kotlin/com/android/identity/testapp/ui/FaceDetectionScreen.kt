package com.android.identity.testapp.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.multipaz.compose.camera.Camera
import org.multipaz.compose.camera.CameraCaptureResolution
import org.multipaz.compose.camera.CameraFrame
import org.multipaz.compose.camera.CameraSelection
import org.multipaz.compose.permissions.rememberCameraPermissionState
import org.multipaz.face_detector.detectFaces

private const val TAG = "FaceDetectionScreen"

@Composable
fun FaceDetectionScreen(
    showToast: (message: String) -> Unit
) {
    val showCameraDialog = remember { mutableStateOf<CameraSelection?>(null) }
    val cameraPermissionState = rememberCameraPermissionState()
    val coroutineScope = rememberCoroutineScope()
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
                    Camera(
                        modifier = Modifier.fillMaxWidth(),
                        cameraSelection = showCameraDialog.value!!,
                        showCameraPreview = showPreview,
                        captureResolution = CameraCaptureResolution.LOW,
                        onFrameCaptured = { incomingVideoFrame ->
                            // Note: this is a suspend-func called on an I/O thread managed by Camera() composable
                            detectAndRenderFaces(incomingVideoFrame)
                        }
                    )
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
                        Text("Face Detection (Front Camera)")
                    }
                }

                item {
                    TextButton(onClick = {
                        showCameraDialog.value = CameraSelection.DEFAULT_BACK_CAMERA
                    }) {
                        Text("Face Detection (Back Camera)")
                    }
                }
            }
        }
    }
}

object Painter {
    const val FACE_POS_RADIUS = 4f
    val facePosPaint = Paint().apply {
        color = Color.White
    }
}

fun detectAndRenderFaces(incomingVideoFrame: CameraFrame): ImageBitmap? {
    val faces = detectFaces(incomingVideoFrame)

    if (faces == null || faces.faces.isEmpty()) return null

    val outImage = ImageBitmap(
        width = incomingVideoFrame.width,
        height = incomingVideoFrame.height,
        config = ImageBitmapConfig.Argb8888,
        hasAlpha = true
    )
    val w = incomingVideoFrame.width.toFloat()
    val h = incomingVideoFrame.height.toFloat()
    val a = incomingVideoFrame.rotation
    val canvas = Canvas(outImage)

    faces.faces.forEach { face ->
        // Draws all face contours.
        for (contour in face.contours) {
            for (point in contour.points) {
                canvas.drawCircle(
                    getOffset(point.x, point.y, w, h, a),
                    Painter.FACE_POS_RADIUS,
                    Painter.facePosPaint
                )
            }
        }
        for (landmark in face.landmarks) {
            canvas.drawCircle(
                getOffset(landmark.position.x, landmark.position.y, w, h, a),
                Painter.FACE_POS_RADIUS,
                Painter.facePosPaint
            )
        }
    }

    return outImage
}

/**
 * Adjust the MLKit data to the bitmap orientation data.
 *
 * Note that they are not the same as MLKit detects only upright looking faces. This conversion also takes into the
 * account that this canvas bitmap is later also rotated to the angle [angle] in the Camera composable when overlapped
 * with the original frame.
 * The two options for the angle 90 and 270 correcponds to portrat front and back cameras. Later the Landscape angles
 * should be added here as well. **/
private fun getOffset(x: Float, y: Float, width: Float, height: Float, angle: Int): Offset {
    if (angle == 90) return Offset(y, height - x) // Back camera, Portrait screen.
    if (angle == 270) return Offset(width - y, x) // Front camera, Portrait screen.

    return Offset(x, y) //default
}

