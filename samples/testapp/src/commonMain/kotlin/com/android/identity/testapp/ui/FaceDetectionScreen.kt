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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.android.identity.testapp.ui.Painter.getColor
import kotlinx.coroutines.launch
import org.multipaz.compose.camera.Camera
import org.multipaz.compose.camera.CameraCaptureResolution
import org.multipaz.compose.camera.CameraSelection
import org.multipaz.compose.permissions.rememberCameraPermissionState
import org.multipaz.face_detector.FaceData
import org.multipaz.face_detector.detectFaces

private const val TAG = "FaceDetectionScreen"

@Composable
fun FaceDetectionScreen(
    showToast: (message: String) -> Unit
) {
    val showCameraDialog = remember { mutableStateOf<CameraSelection?>(null) }
    val cameraPermissionState = rememberCameraPermissionState()
    val coroutineScope = rememberCoroutineScope()
    val lastSeenFaces = remember { mutableStateOf<FaceData?>(null) }
    val transformationMatrix = remember { mutableStateOf(Matrix()) }
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
                    Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
                        Camera(
                            modifier = Modifier,
                            cameraSelection = showCameraDialog.value!!,
                            showCameraPreview = showPreview,
                            captureResolution = CameraCaptureResolution.LOW,
                            onFrameCaptured = { incomingVideoFrame ->
                                // Note: this is a suspend-func called on an I/O thread managed by Camera() composable
                                lastSeenFaces.value = detectFaces(incomingVideoFrame)
                                transformationMatrix.value = incomingVideoFrame.previewTransformation
                            }
                        )
                        OverlayDrawing(lastSeenFaces.value, transformationMatrix.value)
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
private fun OverlayDrawing(faces: FaceData?, transformationMatrix: Matrix) {
    if (faces == null || faces.faces.isEmpty()) return
    val tm = transformationMatrix
    val w = faces.width.toFloat()
    val h = faces.height.toFloat()
    val a = faces.rotation

    Canvas(modifier = Modifier.fillMaxWidth()) {
        drawCircle(
            center = mapFaceData(Offset(w / 2, h / 2), tm, w, h, a),
            radius = 10f,
            color = Color.Red,
            alpha = 0.8f
        )
        faces.faces.forEach { face ->
            val contourPaths = mutableMapOf<Int, Path>()
            for (contour in face.contours) {
                contour.points.forEach { point ->
                    val vertice = mapFaceData(point, tm, w, h, a)
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
                val vertice = mapFaceData(landmark.position, tm, w, h, a)
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
                val mappedP1 = mapFaceData(originalTopLeft, tm, w, h, a)
                val mappedP2 = mapFaceData(originalTopRight, tm, w, h, a)
                val mappedP3 = mapFaceData(originalBottomLeft, tm, w, h, a)
                val mappedP4 = mapFaceData(originalBottomRight, tm, w, h, a)

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

private fun mapFaceData(point: Offset, scale: Matrix, w: Float, h: Float, a: Int): Offset {
    // Have to rotate back to preview coordinates from the detector coordinates first.
    // As the detector refusing not upright faces. Remember that width and height are swapped too.
    var x = point.x
    var y = point.y
    when (a) {
        0 -> { // Portrait.
            x = w - point.y
            y = point.x
        }

        180 -> { // Inversed Portrait.
            x = point.y
            y = h - point.x
        }

        270 -> { // Landscape left side up.
            x = w - point.x
            y = h - point.y
        }

        else -> { /* 90 Landscape right side up and unknowns - no conversion. */ }
    }

    return scale.map(Offset(x, y))
}

