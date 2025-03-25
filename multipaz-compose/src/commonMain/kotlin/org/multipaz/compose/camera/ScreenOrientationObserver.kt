package org.multipaz.compose.camera

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/** API-facing supported camera IDs (extensible). */
enum class ScreenOrientation {
    PORTRAIT,
    LANDSCAPE
}

/**
 * Common device orientation observer that publishes the current screen orientation.
 *
 * This observer exposes a [orientationFlow] that emits the current orientation.
 * Composable screens can collect this flow (using collectAsState) to trigger recomposition
 * when the orientation changes.
 */
expect class ScreenOrientationObserver() {
    val orientationFlow: StateFlow<ScreenOrientation>

    /** Start observing system orientation changes. */
    fun start()

    /** Stop observing system orientation changes. */
    fun stop()
}

/** Common orientation observer. Needed for unified aspect ratio maintenance on preview canvas recomposition. */
@Composable
fun rememberScreenOrientation(observer: ScreenOrientationObserver): ScreenOrientation {
    DisposableEffect(observer) {
        observer.start()
        onDispose { observer.stop() }
    }
    val orientation by observer.orientationFlow.collectAsState()
    return orientation
}
