package org.multipaz.compose.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Custom composable taking care of the camera operations initialization and camera preview composition.
 *
 * @param modifier The composition modifier to apply to the composable.
 * @param cameraSelection The desired hardware camera to use.
 * @param showCameraPreview Whether to show the preview of the captured frame within the Camera composable.
 * @param captureResolution The desired resolution to use for the camera capture.
 * @param onFrameCaptured A callback to invoke when a frame is captured with the frame object.
 *     The callback method is run on the I/O thread and could return the overlay ImageBitmap to be displayed on top
 *     of the captured frame bitmap when the [showCameraPreview] is enabled, or `null` if that's not needed. If the
 *     returned overlay bitmap dimensions does not match the captured frame dimensions it will be ignored.
 */
@Composable
expect fun Camera(
    modifier: Modifier = Modifier,
    cameraSelection: CameraSelection = CameraSelection.DEFAULT_FRONT_CAMERA,
    captureResolution: CameraCaptureResolution,
    showCameraPreview: Boolean,
    onFrameCaptured: suspend (frame: CameraFrame)  -> ImageBitmap?
)
