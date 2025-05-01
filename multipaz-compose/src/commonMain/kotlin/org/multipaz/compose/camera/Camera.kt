package org.multipaz.compose.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Custom composable taking care of the camera operations initialization and camera preview composition.
 *
 * @param modifier The composition modifier to apply to the composable.
 * @param cameraSelection The desired hardware camera ID to use.
 * @param showCameraPreview Whether to show the native camera preview (Camera SDK bound).
 * @param captureResolution The desired resolution to use for the camera capture.
 * @param onFrameCaptured A callback to invoke when a frame is captured.
 */
@Composable
expect fun Camera(
    modifier: Modifier = Modifier,
    cameraSelection: CameraSelection,
    captureResolution: CameraCaptureResolution,
    showCameraPreview: Boolean,
    onFrameCaptured: suspend (frame: ImageBitmap) -> Unit
)
