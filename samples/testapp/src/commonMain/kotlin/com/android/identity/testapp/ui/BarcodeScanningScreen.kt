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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.multipaz.barcodes.Barcode
import org.multipaz.barcodes.scanBarcode
import org.multipaz.compose.camera.Camera
import org.multipaz.compose.camera.CameraCaptureResolution
import org.multipaz.compose.camera.CameraSelection
import org.multipaz.compose.permissions.rememberCameraPermissionState
import kotlin.math.min
import kotlin.math.max

private const val TAG = "BarcodeScanningScreen"

@Composable
fun BarcodeScanningScreen(
    showToast: (message: String) -> Unit
) {
    val showBarcodeScanningDialog = remember { mutableStateOf<Pair<CameraSelection, CameraCaptureResolution>?>(null) }
    val cameraPermissionState = rememberCameraPermissionState()
    val coroutineScope = rememberCoroutineScope()
    val showPreview = true
    val lastSeenBarcodes = remember { mutableStateOf<List<Barcode>>(emptyList())}
    val textMeasurer = rememberTextMeasurer()
    var transformationMatrix = remember { mutableStateOf<Matrix>(Matrix()) }

    showBarcodeScanningDialog.value?.let {
        AlertDialog(
            modifier = Modifier.wrapContentSize(),
            title = { Text(text = "Scan Barcode") },
            text = {
                Column(
                    Modifier.wrapContentSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Scan Barcode with a canvas on top of camera preview for " +
                                "rendering boxes and content of detected barcodes."
                    )
                    Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
                        Camera(
                            modifier = Modifier,
                            cameraSelection = it.first,
                            captureResolution = it.second,
                            showCameraPreview = showPreview,
                            onFrameCaptured = { incomingVideoFrame ->
                                // Note: this is a suspend-func called on an I/O thread managed by Camera() composable
                                lastSeenBarcodes.value =
                                    scanBarcode(incomingVideoFrame.cameraImage)
                                transformationMatrix.value = incomingVideoFrame.previewTransformation
                            }
                        )
                        Canvas(modifier = Modifier.fillMaxWidth()) {
                            val tm = transformationMatrix.value
                            for (barcode in lastSeenBarcodes.value) {
                                val path = Path()
                                for (n in barcode.cornerPoints.indices) {
                                    val point = tm.map(barcode.cornerPoints[n])
                                    if (n == 0) {
                                        path.moveTo(point.x, point.y)
                                    } else {
                                        path.lineTo(point.x, point.y)
                                    }
                                }
                                path.close()
                                drawPath(
                                    path = path,
                                    color = Color.Red,
                                    style = Stroke(width = 5f)
                                )

                                // Note: after mapping the bbox through the transformation matrix it's
                                // possible that things are flipped or rotated... so we need to derive
                                // our own top-left and bottom-right coordinates.
                                val bbMappedTopLeft = tm.map(barcode.boundingBox.topLeft)
                                val bbMappedBottomRight = tm.map(barcode.boundingBox.bottomRight)
                                val bbTopLeft = Offset(
                                    x = min(bbMappedTopLeft.x, bbMappedBottomRight.x),
                                    y = min(bbMappedTopLeft.y, bbMappedBottomRight.y)
                                )
                                val bbBottomRight = Offset(
                                    x = max(bbMappedTopLeft.x, bbMappedBottomRight.x),
                                    y = max(bbMappedTopLeft.y, bbMappedBottomRight.y)
                                )

                                drawRoundRect(
                                    color = Color.Blue,
                                    topLeft = bbTopLeft,
                                    size = Size(
                                        bbBottomRight.x - bbTopLeft.x,
                                        bbBottomRight.y - bbTopLeft.y
                                    ),
                                    cornerRadius = CornerRadius(20f, 20f),
                                    style = Stroke(width = 5f)
                                )

                                val textLayoutResult = textMeasurer.measure(
                                    text = barcode.text,
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        background = Color.Yellow,
                                        color = Color.Black
                                    ),
                                )
                                val textWidth = textLayoutResult.size.width
                                drawText(
                                    textLayoutResult = textLayoutResult,
                                    topLeft = Offset(
                                        (bbTopLeft.x + bbBottomRight.x) / 2f - textWidth / 2f,
                                        bbBottomRight.y
                                    )
                                )

                                drawRect(
                                    color = Color(0, 128, 0, 128),
                                    topLeft = Offset(0f, 0f),
                                    size = size
                                )
                            }

                        }
                    }
                }
            },
            onDismissRequest = { showBarcodeScanningDialog.value = null },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showBarcodeScanningDialog.value = null
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
                        showBarcodeScanningDialog.value = Pair(CameraSelection.DEFAULT_FRONT_CAMERA, CameraCaptureResolution.LOW)
                    }) { Text("Scan Barcode (Front Camera, Low Res)") }
                }
                item {
                    TextButton(onClick = {
                        showBarcodeScanningDialog.value = Pair(CameraSelection.DEFAULT_FRONT_CAMERA, CameraCaptureResolution.MEDIUM)
                    }) { Text("Scan Barcode (Front Camera, Medium Res)") }
                }
                item {
                    TextButton(onClick = {
                        showBarcodeScanningDialog.value = Pair(CameraSelection.DEFAULT_FRONT_CAMERA, CameraCaptureResolution.HIGH)
                    }) { Text("Scan Barcode (Front Camera, High Res)") }
                }

                item {
                    TextButton(onClick = {
                        showBarcodeScanningDialog.value = Pair(CameraSelection.DEFAULT_BACK_CAMERA, CameraCaptureResolution.LOW)
                    }) { Text("Scan Barcode (Back Camera, Low Res)") }
                }
                item {
                    TextButton(onClick = {
                        showBarcodeScanningDialog.value = Pair(CameraSelection.DEFAULT_BACK_CAMERA, CameraCaptureResolution.MEDIUM)
                    }) { Text("Scan Barcode (Back Camera, Medium Res)") }
                }
                item {
                    TextButton(onClick = {
                        showBarcodeScanningDialog.value = Pair(CameraSelection.DEFAULT_BACK_CAMERA, CameraCaptureResolution.HIGH)
                    }) { Text("Scan Barcode (Back Camera, High Res)") }
                }
            }
        }
    }
}
