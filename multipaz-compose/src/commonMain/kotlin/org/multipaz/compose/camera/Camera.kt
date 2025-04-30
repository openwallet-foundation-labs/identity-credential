package org.multipaz.compose.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Custom composable taking care of the camera operations initialization and camera preview composition.
 * Should be considered a placeholder for the future Camera component used throughout the application for a variety
 * of the hardware camera tasks like QR code scanning and face recognition.
 *
 * @param cameraSelection The initial camera selection to use.
 * @param showCameraPreview Whether to show the camera preview or process the data silently.
 * @param modifier The external modifier to apply to the composable.
 */
@Composable
expect fun Camera(
    modifier: Modifier = Modifier,
    cameraSelection: CameraSelection = CameraSelection.DEFAULT_FRONT_CAMERA,
    captureResolution: CameraCaptureResolution = CameraCaptureResolution.LOW,
    showCameraPreview: Boolean = true,
    onFrameCaptured: suspend (frame: CameraFrame) -> ImageBitmap?
)

/**
 * Common Camera Frame data object. No transformations applied
 *
 * @param data The image data converted from the native format (currently Android ImageProxy Plane only).
 * @param width The image width (pixels).
 * @param height The image height (pixels).
 * @param format The image format (currently Android constants only (e.g. YUV_420_888).
 */
data class CameraFrame(
    val data: ImageBitmap,
    val width: Int,
    val height: Int,
    val format: Int,
)

/** Placeholder for camera resolution options (as might be needed for different use cases). Default is LOW. */
enum class CameraCaptureResolution(val width: Int, val height: Int) {
    LOW(640, 480),
    MEDIUM(1280, 720),
    HIGH(1920, 1080)
}
