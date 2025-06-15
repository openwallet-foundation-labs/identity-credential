package org.multipaz.compose.qrcode

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.multipaz.compose.camera.CameraCaptureResolution
import org.multipaz.compose.camera.CameraSelection
import org.multipaz.compose.camera.IosCamera

@Composable
actual fun QrCodeScanner(
    modifier: Modifier,
    cameraSelection: CameraSelection,
    captureResolution: CameraCaptureResolution,
    showCameraPreview: Boolean,
    onCodeScanned: (qrCode: String?) -> Unit
) {
    IosCamera(
        modifier = modifier,
        cameraSelection = cameraSelection,
        captureResolution = captureResolution,
        showCameraPreview = showCameraPreview,
        onFrameCaptured = { frame -> },
        onQrCodeScanned = onCodeScanned
    )
}
