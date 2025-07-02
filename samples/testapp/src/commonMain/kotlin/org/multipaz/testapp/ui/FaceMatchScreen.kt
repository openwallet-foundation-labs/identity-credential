package org.multipaz.testapp.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import org.multipaz.facematch.LRTModelLoader
import org.multipaz.facematch.getFaceEmbeddings
import org.multipaz.util.Logger
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.TimeSource

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
    var facesData by remember { mutableStateOf<List<Pair<DetectedFace, Float>>?>(null) }
    val transformationMatrix = remember { mutableStateOf(Matrix()) }
    var processingStats by remember { mutableStateOf<FrameProcessingStats?>(null) }

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
                                    if (goodFaceDetected(faces)) {
                                        val faceImage = extractFaceBitmap(
                                            incomingVideoFrame,
                                            faces!![0]
                                        )
                                        faceInsets0 = getFaceEmbeddings(faceImage)
                                        if (faceInsets0 != null) {
                                            captureWithPreview.value = null // Close the dialog on success.
                                        }
                                    }
                                    isProcessingFrame = false
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
    matchLastCapturedSelfie.value?.let { cameraConfig ->
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
                        Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
                            Camera(
                                modifier = Modifier.fillMaxWidth(),
                                cameraSelection = cameraConfig.first,
                                captureResolution = cameraConfig.second,
                                showCameraPreview = true,
                                onFrameCaptured = { incomingVideoFrame ->
                                    if (!isProcessingFrame && matchLastCapturedSelfie.value != null) {
                                        isProcessingFrame = true
                                        val overallStartTimeMark = TimeSource.Monotonic.markNow()

                                        val faces = detectFaces(incomingVideoFrame)
                                        transformationMatrix.value = incomingVideoFrame.previewTransformation
                                        val newFacesData = mutableListOf<Pair<DetectedFace, Float>>()

                                        var totalExtractBitmapDuration = kotlin.time.Duration.ZERO
                                        var totalGetEmbeddingsDuration = kotlin.time.Duration.ZERO

                                        if (faces != null && faceInsets0 != null) {
                                            for (face in faces) {
                                                val extractStartTimeMark = TimeSource.Monotonic.markNow()
                                                val faceImage = extractFaceBitmap(incomingVideoFrame, face)
                                                totalExtractBitmapDuration += extractStartTimeMark.elapsedNow()

                                                val embeddingStartTimeMark = TimeSource.Monotonic.markNow()
                                                val faceInsets2 = getFaceEmbeddings(faceImage)
                                                totalGetEmbeddingsDuration += embeddingStartTimeMark.elapsedNow()

                                                if (faceInsets2 != null) {
                                                    val similarity = cosineDistance(faceInsets0!!, faceInsets2)
                                                    newFacesData.add(Pair(face, similarity))
                                                }
                                            }
                                            facesData = newFacesData
                                        } else {
                                            facesData = null
                                        }

                                        val totalProcessingDuration = overallStartTimeMark.elapsedNow()
                                        val totalProcessingTimeMs = totalProcessingDuration.inWholeMilliseconds
                                        val currentFps = if (totalProcessingTimeMs > 0) 1000f / totalProcessingTimeMs else 0f

                                        // Store or update your stats
                                        processingStats = FrameProcessingStats( // Use the simplified data class
                                            totalProcessingTimeMs = totalProcessingTimeMs,
                                            fps = currentFps
                                        )
                                        isProcessingFrame = false
                                    }
                                }
                            )
                            if (facesData != null) {
                                FaceMatchResults(
                                    facesData!!,
                                    transformationMatrix.value,
                                    processingStats = processingStats ?: FrameProcessingStats(0,0f)
                                )
                            }
                        }
                    }
                }
            },
            onDismissRequest = {
                matchLastCapturedSelfie.value = null
                isProcessingFrame = false
            },
            confirmButton = {},
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
                    }) { Text("Initial Step: Take and save a selfie for further use (Front Camera, Medium Res)") }
                }
                item {
                    GroupDivider()
                }
                item {
                    TextButton(onClick = {
                        matchLastCapturedSelfie.value =
                            Pair(CameraSelection.DEFAULT_FRONT_CAMERA, CameraCaptureResolution.MEDIUM)
                        isProcessingFrame = false
                    }) { Text("Take a selfie and match with previously taken one (Front Camera, Medium Res)") }
                }
                item {
                    TextButton(onClick = {
                        matchLastCapturedSelfie.value =
                            Pair(CameraSelection.DEFAULT_BACK_CAMERA, CameraCaptureResolution.MEDIUM)
                        isProcessingFrame = false
                    }) { Text("Take a selfie and match with previously taken one (Back Camera, Medium Res)") }
                }
            }
        }
    }
}

private val textStyle = TextStyle(
    fontSize = 16.sp,
    fontWeight = FontWeight.Bold,
    color = Color.Yellow
)
private val statsStyle = TextStyle(
    fontSize = 16.sp,
    fontWeight = FontWeight.Bold,
    color = Color.Yellow,
    background = Color.Black
)

data class FrameProcessingStats(
    val totalProcessingTimeMs: Long = 0L,
    val fps: Float = 0f
)

@Composable
fun FaceMatchResults(
    facesData: List<Pair<DetectedFace, Float>>,
    transformationMatrix: Matrix,
    processingStats: FrameProcessingStats
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = Modifier.fillMaxWidth()) {
        facesData.forEach { data ->
            val face = data.first
            val similarity = data.second
            var centerPoint = Offset(0f, 0f)

            with(face.boundingBox) {
                val originalTopLeft = topLeft
                val originalTopRight = Offset(right, top)
                val originalBottomLeft = Offset(left, bottom)
                val originalBottomRight = bottomRight

                // Transform all four points.
                val mappedP1 = mapFaceData(originalTopLeft, transformationMatrix)
                val mappedP2 = mapFaceData(originalTopRight, transformationMatrix)
                val mappedP3 = mapFaceData(originalBottomLeft, transformationMatrix)
                val mappedP4 = mapFaceData(originalBottomRight, transformationMatrix)

                // Find min/max X and Y from all mapped points to correctly map the Rect.
                val bbTopLeft = Offset(
                    x = minOf(mappedP1.x, mappedP2.x, mappedP3.x, mappedP4.x),
                    y = minOf(mappedP1.y, mappedP2.y, mappedP3.y, mappedP4.y)
                )
                val bbBottomRight = Offset(
                    x = maxOf(mappedP1.x, mappedP2.x, mappedP3.x, mappedP4.x),
                    y = maxOf(mappedP1.y, mappedP2.y, mappedP3.y, mappedP4.y)
                )

                centerPoint = Offset(
                    (bbTopLeft.x + bbBottomRight.x) / 2,
                    (bbTopLeft.y + bbBottomRight.y) / 2
                )

                drawRoundRect(
                    color = if (similarity < 0.4) Color.Red else Color.Green,
                    topLeft = bbTopLeft,
                    size = Size(
                        bbBottomRight.x - bbTopLeft.x,
                        bbBottomRight.y - bbTopLeft.y
                    ),
                    cornerRadius = CornerRadius(10f, 10f),
                    style = Stroke(width = 5f)
                )
            }

            // Draw similarity value in the center.
            with ("${(similarity * 100).toInt()}%") {
                val textLayoutResult = textMeasurer.measure(
                    text = this,
                    style = textStyle,
                    constraints = Constraints(maxWidth = this@Canvas.size.width.toInt())
                )
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        x = centerPoint.x - (textLayoutResult.size.width / 2),
                        y = centerPoint.y - (textLayoutResult.size.height / 2)
                    )
                )
            }

            //Draw the processing stats.
            with ("fps: ${(processingStats.fps * 100f).roundToInt() / 100f}" +
                    "|time:${processingStats.totalProcessingTimeMs}ms") {
                val textLayoutResult = textMeasurer.measure(
                    text = this,
                    style = statsStyle,
                    constraints = Constraints(maxWidth = this@Canvas.size.width.toInt())
                )
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        x = (this@Canvas.size.width.toInt() - textLayoutResult.size.width) / 2f,
                        y = 2f
                    )
                )
            }
        }
    }
}

private fun mapFaceData(point: Offset, scale: Matrix): Offset {
    return scale.map(Offset(point.x, point.y))
}

/** Postpone the face image capturing until the face is looking straight into camera. */
private fun goodFaceDetected(faces: List<DetectedFace>?): Boolean {
    if (faces != null && faces.size == 1) {
        val face = faces[0]
        val angleThreshold = 10.0f // degrees

        val isLookingStraightX = face.headEulerAngleX in -angleThreshold..angleThreshold
        val isLookingStraightY = face.headEulerAngleY in -angleThreshold..angleThreshold
        val isLookingStraightZ = face.headEulerAngleZ in -angleThreshold..angleThreshold

        return isLookingStraightX && isLookingStraightY && isLookingStraightZ
    }
    return false
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
    face: DetectedFace,
): ImageBitmap {
    val leftEye = face.landmarks.find { it.type == FaceLandmarkType.LEFT_EYE }
    val rightEye = face.landmarks.find { it.type == FaceLandmarkType.RIGHT_EYE }

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
        targetWidthPx = LRTModelLoader.FACE_NET_MODEL_IMAGE_SIZE, // Final square image size (for database saving and face matching tasks).
    )
}