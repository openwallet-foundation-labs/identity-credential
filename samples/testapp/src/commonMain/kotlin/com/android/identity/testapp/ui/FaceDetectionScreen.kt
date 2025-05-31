package com.android.identity.testapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import com.android.identity.testapp.ui.Painter.getColor
import kotlinx.coroutines.launch
import org.multipaz.compose.camera.Camera
import org.multipaz.compose.camera.CameraCaptureResolution
import org.multipaz.compose.camera.CameraCaptureResolution.*
import org.multipaz.compose.camera.CameraSelection
import org.multipaz.compose.camera.CameraSelection.*
import org.multipaz.compose.permissions.rememberCameraPermissionState
import org.multipaz.faces.FaceObject
import org.multipaz.faces.detectFaces
import org.multipaz.util.Logger
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
    val lastSeenFaces = remember { mutableStateOf<List<FaceObject>?>(null) }
    val lastBitmapCaptured = remember { mutableStateOf<ImageBitmap?>(null) }
    val transformationMatrix = remember { mutableStateOf(Matrix()) }

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
                            onFrameCaptured = { incomingVideoFrame ->
                                // Note: this is a suspend-func called on an I/O thread managed by Camera() composable
                                lastSeenFaces.value = detectFaces(incomingVideoFrame)
                                transformationMatrix.value = incomingVideoFrame.previewTransformation

                                if (showCameraDialog.value?.showPreview == false) {
                                    val bitmap = incomingVideoFrame.cameraImage.toImageBitmap()
                                    Logger.i(TAG, "Received bitmap of width ${bitmap.width} height ${bitmap.height}")
                                    lastBitmapCaptured.value = bitmap
                                }
                            }
                        )
                        if (showCameraDialog.value?.showPreview == false) {
                            lastBitmapCaptured.value?.let {
                                Image(
                                    modifier = Modifier.fillMaxWidth(),
                                    bitmap = extractFaceBitmap(it, lastSeenFaces.value, transformationMatrix.value),
                                    contentDescription = "Camera frame",
                                )
                            }
                        }
                        else {
                            OverlayDrawing(lastSeenFaces.value, transformationMatrix.value)
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

@Composable
private fun GroupDivider() {
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = Color.Gray, thickness = 1.dp)
    Spacer(modifier = Modifier.height(16.dp))
}

private fun itemText(cameraConfig: CameraConfig): String {
    with(cameraConfig) {
        val preview = if (showPreview) "Show" else "No"
        val camera =
            mapOf(DEFAULT_FRONT_CAMERA to "Front", DEFAULT_BACK_CAMERA to "Back")[cameraSelection] ?: "Undefined"
        val resolution = mapOf(LOW to "Low", MEDIUM to "Medium", HIGH to "High")[captureResolution] ?: "Unknown"
        return "$preview camera preview, $camera camera, $resolution resolution."
    }
}

private data class CameraConfig(
    val showPreview: Boolean,
    val cameraSelection: CameraSelection,
    val captureResolution: CameraCaptureResolution
)

private object Painter {
    const val FACE_LANDMARK_RADIUS = 15f
    const val FACE_CONTOUR_RADIUS = 5f
    const val FACE_FRAME_RADIUS = 25f
    const val FACE_FRAME_STROKE = 5f

    private val colors = listOf(
        Color.White, Color.Red, Color.Green, Color.Cyan, Color.Yellow, Color.Magenta, Color.Blue
    )

    fun getColor(index: Int): Color {
        return colors[index % colors.size]
    }
}

@Composable
private fun OverlayDrawing(faces: List<FaceObject>?, transformationMatrix: Matrix) {
    if (faces.isNullOrEmpty()) return

    Canvas(modifier = Modifier.fillMaxWidth()) {
        faces.forEach { face ->
            val contourPaths = mutableMapOf<Int, Path>()
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
    inputBitmap: ImageBitmap,
    faces: List<FaceObject>?,
    matrix: Matrix
): ImageBitmap {
    if (faces.isNullOrEmpty()) return inputBitmap // No transformation means no face detected
    // todo: return rotated upright at least?

    return inputBitmap //todo: temp placeholder plug.

    val bitmapSize = Size(inputBitmap.width.toFloat(), inputBitmap.height.toFloat())
    val detectorSize = bitmapSize
    val cameraAngle = 90 // todo 0 for iOS?
    val leftEye = faces[0].landmarks[1] //todo find them, make same map for ios/android
    val rightEye = faces[1].landmarks[2]

    // Face center is between eyes.
    val faceCenter = Offset(
        (leftEye.position.x + rightEye.position.x) / 2,
        (leftEye.position.y + rightEye.position.y) / 2
    )

    // Eye offset from center.
    val eyeOffset = sqrt(
        (leftEye.position.x - rightEye.position.x) * (leftEye.position.x - rightEye.position.x) +
                (leftEye.position.y - rightEye.position.y) * (leftEye.position.y - rightEye.position.y)
    ) / 2

    val eyesAngle = atan2(leftEye.position.x - rightEye.position.x, leftEye.position.y - rightEye.position.y) * 57.2958f

    // Define face size by eye offsets.
    val faceWidth = eyeOffset * 6
    val outputAngle: Double = cameraAngle.toDouble() + 90

    return cropRotateScale(
        inputBitmap,
        cx = faceCenter.x.toDouble(),
        cy = faceCenter.y.toDouble(),
        angleDegrees = outputAngle + eyesAngle.toDouble(),
        outputWidth = faceWidth.toInt(),
        outputHeight = faceWidth.toInt(),
        targetWidth = 256
    )
}

/**
 * Performs a crop and rotation of the [input] image.
 *
 * The operation is defined so that the input coordinate (cx, cy) becomes the center
 * of the output bitmap (with dimensions [outputWidth] x [outputHeight]), and
 * the image is rotated around that point by [angleDegrees] (counterclockwise).
 *
 * @param input The input image.
 * @param cx The x coordinate (in input image space) to center.
 * @param cy The y coordinate (in input image space) to center.
 * @param angleDegrees The rotation angle (in degrees; counterclockwise) to apply.
 * @param outputWidth The desired output image width in pixels.
 * @param outputHeight The desired output image height in pixels.
 *
 * @return The resulting image after crop and rotation.
 */
fun cropRotateScale(
    input: ImageBitmap,
    cx: Double,
    cy: Double,
    angleDegrees: Double,
    outputWidth: Int,
    outputHeight: Int,
    targetWidth: Int
): ImageBitmap {
    val finalScale = targetWidth.toFloat() / outputWidth.toFloat()
    val finalOutputWidth = targetWidth
    val finalOutputHeight = (outputHeight * finalScale).toInt()
    val matrix = Matrix().apply {
        translate((-cx).toFloat(), (-cy).toFloat())
        rotateZ(angleDegrees.toFloat())
        translate((outputWidth / 2).toFloat(), (outputHeight / 2).toFloat())
        scale(finalScale, finalScale)
    }
    //val output = createBitmap(finalOutputWidth, finalOutputHeight, input.config ?: Bitmap.Config.ARGB_8888)
    //val canvas = Canvas(output)
    //canvas.drawBitmap(input, matrix, null)
    return input //todo: placeholder
}

