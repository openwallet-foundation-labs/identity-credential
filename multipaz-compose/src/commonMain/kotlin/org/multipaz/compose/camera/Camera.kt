package org.multipaz.compose.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Custom composable taking care of the camera operations initialization and camera preview composition.
 * Should be considered a placeholder for the future Camera component used throughout the application for a variety
 * of the hardware camera tasks like QR code scanning and face recognition.
 *
 * @param cameraSelector The initial camera selection to use.
 * @param showPreview Whether to show the camera preview or process the data silently.
 * @param modifier The external modifier to apply to the composable.
 */
@Composable
expect fun Camera(
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA,
    modifier: Modifier = Modifier,
)
