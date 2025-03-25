package org.multipaz.compose.camera

import android.view.OrientationEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.multipaz.context.applicationContext
import org.multipaz.compose.camera.ScreenOrientation

actual class ScreenOrientationObserver actual constructor() {
    private val flow = MutableStateFlow(ScreenOrientation.PORTRAIT)
    actual val orientationFlow: StateFlow<ScreenOrientation> get() = flow

    private val listener = object : OrientationEventListener(applicationContext) {
        override fun onOrientationChanged(orientation: Int) {
            // A simple approach to choose between portrait and landscape.
            val newOrientation = if (orientation in 60..120 || orientation in 240..300) {
                ScreenOrientation.LANDSCAPE
            } else {
                ScreenOrientation.PORTRAIT
            }
            flow.value = newOrientation
        }
    }

    actual fun start() {
        // Call the method on the listener instance.
        if (listener.canDetectOrientation()) {
            listener.enable()
        }
    }

    actual fun stop() {
        listener.disable()
    }
}
