package org.multipaz.compose.camera

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView

import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import org.multipaz.compose.camera.CameraBuilder
import platform.Foundation.NSNotificationCenter
import platform.UIKit.*

/**
 * iOS-specific implementation of [Camera].
 *
 * @param modifier Modifier to be applied to the camera preview.
 * @param cameraConfiguration Lambda to configure the [CameraBuilder].
 * @param onCameraReady Callback invoked with the initialized [CameraEngine].
 */
@Composable
actual fun PlatformCamera(
    modifier: Modifier,
    cameraConfiguration: CameraBuilder.() -> Unit,
    onCameraReady: (CameraEngine) -> Unit
) {

    val cameraEngine = remember {
        IosCameraBuilder()
            .apply(cameraConfiguration)
            .build()
    }

    LaunchedEffect(cameraEngine) {
        onCameraReady(cameraEngine)
    }

    // CameraEngine frame orientation updates on physical device rotation.
    DisposableEffect(Unit) {
        val notificationCenter = NSNotificationCenter.defaultCenter
        val observer = notificationCenter.addObserverForName(
            name = UIDeviceOrientationDidChangeNotification,
            `object` = null,
            queue = null
        ) { _ ->
            cameraEngine.currentVideoOrientation()?.let { newOrientation ->
                cameraEngine.getCameraPreviewLayer()?.connection?.videoOrientation = newOrientation
            }
        }

        onDispose {
            notificationCenter.removeObserver(observer)
        }
    }
    if (cameraEngine.showPreview) {
        UIKitView(
            factory = { cameraEngine.view },
            modifier = modifier.fillMaxSize(), // Important for iOS preview scaling.
            properties = UIKitInteropProperties(isInteractive = false, isNativeAccessibilityEnabled = true)
        )
    }
}