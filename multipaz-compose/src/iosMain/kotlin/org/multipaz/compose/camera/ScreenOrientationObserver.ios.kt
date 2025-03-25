package org.multipaz.compose.camera

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.multipaz.compose.camera.ScreenOrientation
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientation
import platform.UIKit.UIDeviceOrientationDidChangeNotification

actual class ScreenOrientationObserver {
    private val _orientationFlow = MutableStateFlow(ScreenOrientation.PORTRAIT)
    actual val orientationFlow: StateFlow<ScreenOrientation> get() = _orientationFlow

    private var observer: Any? = null

    actual fun start() {
        // Start generating orientation notifications.
        UIDevice.currentDevice().beginGeneratingDeviceOrientationNotifications()
        observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIDeviceOrientationDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { notification ->
            val deviceOrientation = UIDevice.currentDevice().orientation
            val newOrientation = when (deviceOrientation) {
                UIDeviceOrientation.UIDeviceOrientationLandscapeLeft,
                UIDeviceOrientation.UIDeviceOrientationLandscapeRight -> ScreenOrientation.LANDSCAPE
                UIDeviceOrientation.UIDeviceOrientationPortrait,
                UIDeviceOrientation.UIDeviceOrientationPortraitUpsideDown -> ScreenOrientation.PORTRAIT
                else -> _orientationFlow.value // leave unchanged if unknown or face up/down
            }
            _orientationFlow.value = newOrientation
        }
    }

    actual fun stop() {
        if (observer != null) {
            NSNotificationCenter.defaultCenter.removeObserver(observer!!)
            observer = null
        }
        UIDevice.currentDevice().endGeneratingDeviceOrientationNotifications()
    }
}