package org.multipaz.testapp.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.multipaz.compose.camera.Camera
import org.multipaz.compose.camera.CameraCaptureResolution
import org.multipaz.compose.camera.CameraFrame
import org.multipaz.compose.camera.CameraSelection
import org.multipaz.compose.cropRotateScaleImage
import org.multipaz.compose.permissions.rememberCameraPermissionState
import org.multipaz.facedetection.DetectedFace
import org.multipaz.facedetection.FaceLandmarkType
import org.multipaz.facedetection.detectFaces
import org.multipaz.facematch.getFaceEmbeddings
import org.multipaz.util.Logger
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

private const val TAG = "FaceMatchScreen"

@Composable
fun FaceMatchScreen(
    showToast: (message: String) -> Unit
) {
    val captureWithPreview = remember { mutableStateOf<Pair<CameraSelection, CameraCaptureResolution>?>(null) }
    val matchLastCapturedSelfie = remember { mutableStateOf<Pair<CameraSelection, CameraCaptureResolution>?>(null) }
    val cameraPermissionState = rememberCameraPermissionState()
    val coroutineScope = rememberCoroutineScope()
    var isProcessingFrame by remember { mutableStateOf(false) }
    var faceInsets0 by remember { mutableStateOf<FloatArray?>(null) }

    // Step 1. Capture initial selfie.
    captureWithPreview.value?.let {
        ///val lastFrameReceived = remember { mutableStateOf<CameraFrame?>(null) }
        LaunchedEffect(Unit) {
            isProcessingFrame = false
        }
        AlertDialog(
            modifier = Modifier.wrapContentSize(),
            title = { Text(text = "Automatic Selfie") },
            text = {
                Column(
                    Modifier.wrapContentSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Center your face in the preview frame. The selfie will be taken automatically."
                    )
                    Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
                        Camera(
                            modifier = Modifier.fillMaxWidth(),
                            cameraSelection = it.first,
                            captureResolution = it.second,
                            showCameraPreview = true,
                            onFrameCaptured = { incomingVideoFrame ->
                                if (!isProcessingFrame && captureWithPreview.value != null) {
                                    isProcessingFrame = true
                                    val faces = detectFaces(incomingVideoFrame)
                                    if (faces != null && faces.size == 1) {
                                        val faceImage = extractFaceBitmap(incomingVideoFrame, faces)
                                        faceInsets0 = getFaceEmbeddings(faceImage)
                                        captureWithPreview.value = null // Close the dialog on success.
                                    }
                                    else {
                                        isProcessingFrame = false
                                    }
                                }
                            }
                        )
                    }
                }
            },
            onDismissRequest = {
                captureWithPreview.value = null
                isProcessingFrame = false
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    captureWithPreview.value = null
                    isProcessingFrame = false
                }) {
                    Text(text = "Close")
                }
            }
        )
    }

    // Step 2. Match the last captured selfie.
    matchLastCapturedSelfie.value?.let {
        val initialMessge = "Detecting the face..."
        val dialogMessage = remember { mutableStateOf(initialMessge) }
        AlertDialog(
            modifier = Modifier.wrapContentSize(),
            title = { Text(text = "Match the face") },
            text = {
                if (faceInsets0 == null) {
                    Text(
                        text = "The initial face to match against have to be set first. Close and use Step 1 to set it."
                    )
                } else {
                    Column(
                        Modifier.wrapContentSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = dialogMessage.value,
                            fontSize = MaterialTheme.typography.headlineSmall.fontSize
                        )
                        Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
                            Camera(
                                modifier = Modifier.fillMaxWidth(),
                                cameraSelection = it.first,
                                captureResolution = it.second,
                                showCameraPreview = true,
                                onFrameCaptured = { incomingVideoFrame ->
                                    if (!isProcessingFrame && matchLastCapturedSelfie.value != null) {
                                        isProcessingFrame = true
                                        val faces = detectFaces(incomingVideoFrame)
                                        if (faces != null && faces.size == 1 && faceInsets0 != null) {
                                            val faceImage = extractFaceBitmap(incomingVideoFrame, faces)
                                            val faceInsets2 = getFaceEmbeddings(faceImage)
                                            val similarity = cosineDistance(faceInsets0!!, faceInsets2)
                                            dialogMessage.value = "Similarity: ${(similarity*100).toInt()}%"
                                        }
                                        else {
                                            dialogMessage.value = initialMessge
                                            isProcessingFrame = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            },
            onDismissRequest = {
                matchLastCapturedSelfie.value = null
                isProcessingFrame = false },
            confirmButton = {
                TextButton(onClick = {
                    dialogMessage.value = initialMessge
                    isProcessingFrame = false
                }) {
                    Text(text = "Try again")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    matchLastCapturedSelfie.value = null
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
                        captureWithPreview.value =
                            Pair(CameraSelection.DEFAULT_FRONT_CAMERA, CameraCaptureResolution.MEDIUM)
                        isProcessingFrame = false
                    }) { Text("Step 1: Take and save a selfie for further use (Front Camera, Medium Res)") }
                }
                item {
                    TextButton(onClick = {
                        matchLastCapturedSelfie.value =
                            Pair(CameraSelection.DEFAULT_FRONT_CAMERA, CameraCaptureResolution.MEDIUM)
                        isProcessingFrame = false
                    }) { Text("Step 2: Take a selfie and match with previously taken one (Front Camera, Medium Res)") }
                }
            }
        }
    }
}

/** Calculate cosine similarity between two vectors. */
private fun cosineDistance(x1: FloatArray, x2: FloatArray): Float {
    var mag1 = 0.0f
    var mag2 = 0.0f
    var product = 0.0f
    for (i in x1.indices) {
        mag1 += x1[i].pow(2)
        mag2 += x2[i].pow(2)
        product += x1[i] * x2[i]
    }
    mag1 = sqrt(mag1)
    mag2 = sqrt(mag2)
    return product / (mag1 * mag2)
}

/** Cut out the face square, rotate it to level eyes line, scale to the smaller size for face matching tasks. */
private fun extractFaceBitmap(
    frameData: CameraFrame,
    faces: List<DetectedFace>?,
): ImageBitmap {
    if (faces.isNullOrEmpty()) {
        Logger.w(TAG, "No face data for bitmap extraction.")
        return frameData.cameraImage.toImageBitmap()
    }

    val leftEye = faces[0].landmarks.find { it.type == FaceLandmarkType.LEFT_EYE }
    val rightEye = faces[0].landmarks.find { it.type == FaceLandmarkType.RIGHT_EYE }

    if (leftEye == null || rightEye == null) {
        Logger.w(TAG, "No face features for bitmap extraction.")
        return frameData.cameraImage.toImageBitmap()
    }

    // Heuristic multiplier to fit the face normalized to the eyes pupilar distance.
    val faceCropFactor = 3.5f

    // Heuristic multiplier to offset vertically so the face is better centered within the rectangular crop.
    val faceVerticalOffsetFactor = 0.5f

    var faceCenterX = (leftEye.position.x + rightEye.position.x) / 2
    var faceCenterY = (leftEye.position.y + rightEye.position.y) / 2
    val eyeOffsetX = leftEye.position.x - rightEye.position.x
    val eyeOffsetY = leftEye.position.y - rightEye.position.y
    val eyeDistance = sqrt(eyeOffsetX * eyeOffsetX + eyeOffsetY * eyeOffsetY)
    val faceWidth = eyeDistance * faceCropFactor
    val faceVerticalOffset = eyeDistance * faceVerticalOffsetFactor
    if (frameData.isLandscape) {
        faceCenterY += faceVerticalOffset
    } else {
        faceCenterX -= faceVerticalOffset
    }
    val eyesAngleRad = atan2(eyeOffsetY, eyeOffsetX)
    val eyesAngleDeg = eyesAngleRad * 180.0 / PI // Convert radians to degrees
    val totalRotationDegrees = 180 - eyesAngleDeg

    // Call platform dependent bitmap transformation.
    return cropRotateScaleImage(
        frameData = frameData, // Platform-specific image data.
        cx = faceCenterX.toDouble(), // Point between eyes
        cy = faceCenterY.toDouble(), // Point between eyes
        angleDegrees = totalRotationDegrees, //includes the camera rotation and eyes rotation.
        outputWidthPx = faceWidth.toInt(), // Expected face width for cropping *before* final scaling.
        outputHeightPx = faceWidth.toInt(),// Expected face height for cropping *before* final scaling.
        targetWidthPx = 256, // Final square image size (for database saving and face matching tasks).
    )
}