// org.multipaz.compose.camera/IOSCamera.kt (iOS platform code)

package org.multipaz.compose.camera

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import platform.UIKit.UIViewController

@Composable
actual fun Camera(
    cameraSelector: CameraSelector,
    modifier: Modifier
) {
    UIViewControllerComposable(
        controllerProvider = {
            CameraViewController(
                cameraSelector = cameraSelector
            )
        },
        modifier = modifier
    )
}

@Composable
private fun UIViewControllerComposable(
    controllerProvider: () -> UIViewController,
    modifier: Modifier = Modifier
) {
    // Cache the controller instance during the composable lifecycle.
    val controller = remember { controllerProvider() as CameraViewController }

    DisposableEffect(Unit) {
        val listener = OrientationListener {
            controller.handleDeviceOrientationDidChange()
        }
        listener.register()

        onDispose {
            listener.unregister()
        }
    }

    UIKitView(
        factory = { controller.view },
        modifier = modifier.fillMaxSize(),
        properties = UIKitInteropProperties(isInteractive = false, isNativeAccessibilityEnabled = true)
    )
}
