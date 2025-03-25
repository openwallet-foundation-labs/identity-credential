package org.multipaz.compose.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Cross-platform composable function to display the camera preview.
 *
 * @param modifier Modifier to be applied to the camera preview.
 * @param cameraConfiguration Lambda to configure the [CameraEngine] with needed components.
 * @param onCameraReady Callback invoked with the fully initialized [CameraEngine] object.
 */
@Composable
fun Camera(
    modifier: Modifier = Modifier,
    cameraConfiguration: CameraBuilder.() -> Unit,
    onCameraReady: (CameraEngine) -> Unit
) {
    PlatformCamera(modifier, cameraConfiguration, onCameraReady)
}

/** Platform dependent implementations of the Preview. */
@Composable
expect fun PlatformCamera(
    modifier: Modifier = Modifier,
    cameraConfiguration: CameraBuilder.() -> Unit,
    onCameraReady: (CameraEngine) -> Unit
)
