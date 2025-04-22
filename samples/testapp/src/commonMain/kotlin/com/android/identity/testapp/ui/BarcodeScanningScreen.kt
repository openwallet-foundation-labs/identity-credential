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
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.multipaz.barcodes.scanBarcode
import org.multipaz.compose.camera.Camera
import org.multipaz.compose.camera.CameraCaptureResolution
import org.multipaz.compose.camera.CameraFrame
import org.multipaz.compose.camera.CameraSelection
import org.multipaz.compose.permissions.rememberCameraPermissionState
import org.multipaz.util.Logger

private const val TAG = "BarcodeScanningScreen"

@Composable
fun BarcodeScanningScreen(
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
                            scanAndRenderBarcodes(incomingVideoFrame)
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
                        Text("Scan barcodes (Front Camera)")
                    }
                }

                item {
                    TextButton(onClick = {
                        showCameraDialog.value = CameraSelection.DEFAULT_BACK_CAMERA
                    }) {
                        Text("Scan barcodes (Back Camera)")
                    }
                }
            }
        }
    }
}

private val paint = Paint().apply {
    color = Color.Red
    style = PaintingStyle.Stroke
    isAntiAlias = true
}

private val redPaint = Paint().apply {
    color = Color.Red
    style = PaintingStyle.Stroke
    isAntiAlias = true
    strokeWidth = 2.0f
}

private val bluePaint = Paint().apply {
    color = Color.Blue
    style = PaintingStyle.Stroke
    isAntiAlias = true
    strokeWidth = 2.0f
}

private fun scanAndRenderBarcodes(incomingVideoFrame: CameraFrame): ImageBitmap? {

    val barcodes = scanBarcode(incomingVideoFrame.toImageBitmap())

    val outImage = ImageBitmap(
        width = incomingVideoFrame.width,
        height = incomingVideoFrame.height,
        config = ImageBitmapConfig.Argb8888,
        hasAlpha = true
    )
    val canvas = Canvas(outImage)

    for (barcode in barcodes) {
        canvas.drawRect(barcode.boundingBox, bluePaint)
        for (cornerPoint in barcode.cornerPoints) {
            canvas.drawCircle(cornerPoint, radius = 10f, redPaint)
        }
        Logger.i(TAG, "Detected barcode format ${barcode.format} with text: '${barcode.text}'")
    }

    return outImage
}
