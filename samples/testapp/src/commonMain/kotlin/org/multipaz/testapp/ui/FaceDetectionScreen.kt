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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.multipaz.testapp.ui.Painter.getColor
import kotlinx.coroutines.launch
import org.multipaz.compose.camera.Camera
import org.multipaz.compose.camera.CameraCaptureResolution
import org.multipaz.compose.camera.CameraCaptureResolution.HIGH
import org.multipaz.compose.camera.CameraCaptureResolution.LOW
import org.multipaz.compose.camera.CameraCaptureResolution.MEDIUM
import org.multipaz.compose.camera.CameraFrame
import org.multipaz.compose.camera.CameraSelection
import org.multipaz.compose.camera.CameraSelection.DEFAULT_BACK_CAMERA
import org.multipaz.compose.camera.CameraSelection.DEFAULT_FRONT_CAMERA
import org.multipaz.compose.cropRotateScaleImage
import org.multipaz.compose.permissions.rememberCameraPermissionState
import org.multipaz.facedetection.DetectedFace
import org.multipaz.facedetection.FaceContourType
import org.multipaz.facedetection.FaceLandmarkType
import org.multipaz.facedetection.detectFaces
import org.multipaz.util.Logger
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

private const val TAG = "FaceDetectionScreen"

@Composable
fun FaceDetectionScreen(
    showToast: (message: String) -> Unit
) {
    val showCameraDialog = remember { mutableStateOf<CameraConfig?>(null) }
    val cameraPermissionState = rememberCameraPermissionState()
    val coroutineScope = rememberCoroutineScope()
    val lastSeenFaces = remember { mutableStateOf<List<DetectedFace>?>(null) }
    val lastBitmapCaptured = remember { mutableStateOf<ImageBitmap?>(null) }
    val transformationMatrix = remember { mutableStateOf(Matrix()) }
    val isLandscape = remember { mutableStateOf(false) }

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
                    Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
                        Camera(
                            modifier = Modifier,
                            cameraSelection = showCameraDialog.value?.cameraSelection ?: DEFAULT_FRONT_CAMERA,
                            showCameraPreview = showCameraDialog.value?.showPreview ?: true,
                            captureResolution = showCameraDialog.value?.captureResolution ?: LOW,
                            onFrameCaptured = { cameraFrame ->
                                // Note: this is a suspend-func called on an I/O thread managed by Camera() composable
                                lastSeenFaces.value = detectFaces(cameraFrame)
                                transformationMatrix.value = cameraFrame.previewTransformation
                                isLandscape.value = cameraFrame.isLandscape
                                // Show normalized bitmap
                                if (showCameraDialog.value?.showPreview == false
                                    && lastSeenFaces.value?.isNotEmpty() == true) {
                                    lastBitmapCaptured.value = extractFaceBitmap(cameraFrame, lastSeenFaces.value)
                                }
                            }
                        )
                        if (showCameraDialog.value?.showPreview == false) {
                            lastBitmapCaptured.value?.let {
                                Image(
                                    modifier = Modifier.fillMaxWidth(),
                                    bitmap = it,
                                    contentScale =
                                        if (isLandscape.value) ContentScale.FillHeight else ContentScale.FillWidth,
                                    contentDescription = "Camera frame",
                                )
                            }
                        } else {
                            CameraPreviewOverlay(lastSeenFaces.value, transformationMatrix.value)
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
                    val cameraConfig = CameraConfig(true, DEFAULT_FRONT_CAMERA, LOW)
                    TextButton(onClick = { showCameraDialog.value = cameraConfig }) { Text(itemText(cameraConfig)) }
                }
                item {
                    val cameraConfig = CameraConfig(true, DEFAULT_FRONT_CAMERA, MEDIUM)
                    TextButton(onClick = { showCameraDialog.value = cameraConfig }) { Text(itemText(cameraConfig)) }
                }
                item {
                    val cameraConfig = CameraConfig(true, DEFAULT_FRONT_CAMERA, HIGH)
                    TextButton(onClick = { showCameraDialog.value = cameraConfig }) { Text(itemText(cameraConfig)) }
                }

                item {
                    GroupDivider()
                }

                item {
                    val cameraConfig = CameraConfig(true, DEFAULT_BACK_CAMERA, LOW)
                    TextButton(onClick = { showCameraDialog.value = cameraConfig }) { Text(itemText(cameraConfig)) }
                }
                item {
                    val cameraConfig = CameraConfig(true, DEFAULT_BACK_CAMERA, MEDIUM)
                    TextButton(onClick = { showCameraDialog.value = cameraConfig }) { Text(itemText(cameraConfig)) }
                }
                item {
                    val cameraConfig = CameraConfig(true, DEFAULT_BACK_CAMERA, HIGH)
                    TextButton(onClick = { showCameraDialog.value = cameraConfig }) { Text(itemText(cameraConfig)) }
                }

                item {
                    GroupDivider()
                }

                item {
                    val cameraConfig = CameraConfig(false, DEFAULT_FRONT_CAMERA, LOW)
                    TextButton(onClick = { showCameraDialog.value = cameraConfig }) { Text(itemText(cameraConfig)) }
                }
                item {
                    val cameraConfig = CameraConfig(false, DEFAULT_FRONT_CAMERA, MEDIUM)
                    TextButton(onClick = { showCameraDialog.value = cameraConfig }) { Text(itemText(cameraConfig)) }
                }
                item {
                    val cameraConfig = CameraConfig(false, DEFAULT_FRONT_CAMERA, HIGH)
                    TextButton(onClick = { showCameraDialog.value = cameraConfig }) { Text(itemText(cameraConfig)) }
                }

                item {
                    GroupDivider()
                }

                item {
                    val cameraConfig = CameraConfig(false, DEFAULT_BACK_CAMERA, LOW)
                    TextButton(onClick = { showCameraDialog.value = cameraConfig }) { Text(itemText(cameraConfig)) }
                }
                item {
                    val cameraConfig = CameraConfig(false, DEFAULT_BACK_CAMERA, MEDIUM)
                    TextButton(onClick = { showCameraDialog.value = cameraConfig }) { Text(itemText(cameraConfig)) }
                }
                item {
                    val cameraConfig = CameraConfig(false, DEFAULT_BACK_CAMERA, HIGH)
                    TextButton(onClick = { showCameraDialog.value = cameraConfig }) { Text(itemText(cameraConfig)) }
                }
            }
        }
    }
}

/** Generate UI text line for the given camera configuration. */
private fun itemText(cameraConfig: CameraConfig): String {
    with(cameraConfig) {
        val preview = if (showPreview) "Show" else "No"
        val camera =
            mapOf(DEFAULT_FRONT_CAMERA to "Front", DEFAULT_BACK_CAMERA to "Back")[cameraSelection] ?: "Undefined"
        val resolution = mapOf(LOW to "Low", MEDIUM to "Medium", HIGH to "High")[captureResolution] ?: "Unknown"
        return "$preview camera preview, $camera camera, $resolution resolution."
    }
}

/** Camera parameters variants to display in the UI. */
private data class CameraConfig(
    val showPreview: Boolean,
    val cameraSelection: CameraSelection,
    val captureResolution: CameraCaptureResolution
)

/** Painter object for storing colors and drawing parameters for the face detection results overlay. */
private object Painter {
    val textStyle = TextStyle(
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        background = Color.Yellow,
        color = Color.Black
    )
    const val FACE_LANDMARK_RADIUS = 15f
    const val FACE_CONTOUR_RADIUS = 5f
    const val FACE_FRAME_RADIUS = 25f
    const val FACE_FRAME_STROKE = 5f

    private val colors = listOf(
        Color.White, Color.Red, Color.Green, Color.Cyan, Color.Yellow, Color.Magenta, Color.Blue
    )

    fun getColor(type: FaceLandmarkType): Color {
        return colors[type.ordinal % colors.size]
    }

    fun getColor(type: FaceContourType): Color {
        return colors[type.ordinal % colors.size]
    }
}

fun asPercent(v: Float?) = "${v?.let { (it * 100).toInt() }}%"
fun asAngle(a: Float?) = "${a?.let { (it * 10).toInt() / 10f }}°"

@Composable
private fun CameraPreviewOverlay(faces: List<DetectedFace>?, transformationMatrix: Matrix) {
    if (faces.isNullOrEmpty()) return

    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = Modifier.fillMaxWidth()) {
        var line = 0
        faces.forEach { face ->
            val contourPaths = mutableMapOf<FaceContourType, Path>()
            val faceDataText = "Face ${line+1}:" +
                    " rightEye=${asPercent(face.rightEyeOpenProbability)}" +
                    " leftEye=${asPercent(face.leftEyeOpenProbability)}" +
                    " smiling=${asPercent(face.smilingProbability)}" +
                    " headX=${asAngle(face.headEulerAngleX)}" +
                    " headY=${asAngle(face.headEulerAngleY)}" +
                    " headZ=${asAngle(face.headEulerAngleZ)}"
            val textLayoutResult = textMeasurer.measure(
                text = faceDataText,
                style = Painter.textStyle,
                constraints = Constraints(maxWidth = this.size.width.toInt())
            )
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(0f, textLayoutResult.size.height.toFloat() * line++ )
            )

            // Draw face contours (MLKit provides contours for one face only).
            for (contour in face.contours) {
                contour.points.forEach { point ->
                    val vertice = mapFaceData(point, transformationMatrix)
                    contourPaths[contour.type].let { path ->
                        if (path == null) {
                            contourPaths[contour.type] = Path().apply { moveTo(vertice.x, vertice.y) }
                        } else {
                            path.lineTo(vertice.x, vertice.y)
                        }
                        drawCircle(
                            center = vertice,
                            radius = Painter.FACE_CONTOUR_RADIUS,
                            color = getColor(contour.type),
                            alpha = 0.8f
                        )
                    }
                }
            }
            for (id in contourPaths.keys) {
                contourPaths[id]?.let { path ->
                    path.close()
                    drawPath(
                        path = path,
                        color = getColor(id),
                        style = Stroke(width = 3f)
                    )
                }
            }

            // Draw face landmarks.
            for (landmark in face.landmarks) {
                val vertice = mapFaceData(landmark.position, transformationMatrix)
                drawCircle(
                    center = vertice,
                    radius = Painter.FACE_LANDMARK_RADIUS,
                    color = getColor(landmark.type),
                    style = Stroke(width = 2f),
                    alpha = 0.6f
                )
            }
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

                // Draw face frame.
                drawRoundRect(
                    color = Color.Blue,
                    topLeft = bbTopLeft,
                    size = Size(
                        bbBottomRight.x - bbTopLeft.x,
                        bbBottomRight.y - bbTopLeft.y
                    ),
                    cornerRadius = CornerRadius(Painter.FACE_FRAME_RADIUS, Painter.FACE_FRAME_RADIUS),
                    style = Stroke(width = Painter.FACE_FRAME_STROKE)
                )
            }
        }
    }
}

private fun mapFaceData(point: Offset, scale: Matrix): Offset {
    return scale.map(Offset(point.x, point.y))
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

    val mouthPosition = faces[0].landmarks.find { it.type == FaceLandmarkType.MOUTH_BOTTOM }
    val leftEye = faces[0].landmarks.find { it.type == FaceLandmarkType.LEFT_EYE }
    val rightEye = faces[0].landmarks.find { it.type == FaceLandmarkType.RIGHT_EYE }

    if (leftEye == null || rightEye == null || mouthPosition == null) {
        Logger.w(TAG, "No face features for bitmap extraction.")
        return frameData.cameraImage.toImageBitmap()
    }

    // Heuristic multiplier to fit the face normalized to the eyes pupilar distance.
    val faceCropFactor = 4f

    // Heuristic multiplier to offset vertically so the face is better centered within the rectangular crop.
    val faceVerticalOffsetFactor = 0.25f

    var faceCenterX = (leftEye.position.x + rightEye.position.x) / 2
    var faceCenterY = (leftEye.position.y + rightEye.position.y) / 2
    val eyeOffsetX = leftEye.position.x - rightEye.position.x
    val eyeOffsetY = leftEye.position.y - rightEye.position.y
    val eyeDistance = sqrt(eyeOffsetX * eyeOffsetX + eyeOffsetY * eyeOffsetY)
    val faceWidth = eyeDistance * faceCropFactor
    val faceVerticalOffset = eyeDistance * faceVerticalOffsetFactor

    if (frameData.isLandscape) {
        /** Required for iOS capable of upside-down face detection. */
        faceCenterY += faceVerticalOffset * (if (leftEye.position.y < mouthPosition.position.y) 1 else -1)
    } else {
        /** Required for iOS capable of upside-down face detection. */
        faceCenterX -= faceVerticalOffset * (if (leftEye.position.x < mouthPosition.position.x) -1 else 1)
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


