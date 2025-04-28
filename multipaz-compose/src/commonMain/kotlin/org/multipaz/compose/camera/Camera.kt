package org.multipaz.compose.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Custom composable taking care of native camera configuration, operations initiation, and camera preview composition.
 * Must be used in the Context of the expected application (within the screen composable), regardless of the need for
 * displaying the preview, as this is the primary entry point for all camera use cases.
 *
 * @param modifier optional Modifier to be applied to the camera compoisable (e.g. the preview image view).
 * @param cameraConfiguration Builder methods Lambda to configure the [CameraEngine] with settings and components.
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

/** Platform dependent implementations of the Camera composable. E.g. for passing the App Context environment. */
@Composable
expect fun PlatformCamera(
    modifier: Modifier = Modifier,
    cameraConfiguration: CameraBuilder.() -> Unit,
    onCameraReady: (CameraEngine) -> Unit
)
