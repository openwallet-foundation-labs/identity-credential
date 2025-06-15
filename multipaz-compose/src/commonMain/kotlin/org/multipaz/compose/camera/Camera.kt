package org.multipaz.compose.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Custom composable taking care of the camera operations initialization and camera preview composition.
 *
 * This requires camera permission, see [org.multipaz.compose.permissions.rememberCameraPermissionState].
 *
 * @param modifier The composition modifier to apply to the composable.
 * @param cameraSelection The desired hardware camera to use.
 * @param captureResolution The desired resolution to use for the camera capture.
 * @param showCameraPreview Whether to show the preview of the captured frame within the Camera composable.
 * @param onFrameCaptured A callback to invoke when a frame is captured with the frame object. This is invoked
 *     on an I/O thread.
 */
@Composable
expect fun Camera(
    modifier: Modifier = Modifier,
    cameraSelection: CameraSelection = CameraSelection.DEFAULT_FRONT_CAMERA,
    captureResolution: CameraCaptureResolution,
    showCameraPreview: Boolean,
    onFrameCaptured: suspend (frame: CameraFrame) -> Unit
)
