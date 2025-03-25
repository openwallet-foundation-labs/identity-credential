package org.multipaz.compose.camera

import android.annotation.SuppressLint
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import org.multipaz.compose.camera.CameraBuilder
import org.multipaz.util.Logger

/**
 * Android platform-specific implementation of [Camera].
 *
 * The BoxWithConstraints is used to maintain correct aspect ratio of the preview in portrait and Landscape modes.
 * When the preview is available (camera initialized) the size of the camera frame is retrieved to calculate the aspect
 * ratio. On CameraReady event the container need to be recomposed for that change. However, for an unknown reason,
 * some cameras report a wider sized frame (landscape) than the preview size, which sometimes leads to black sides
 * visible on the preview.
 *
 * @param modifier Modifier to be applied to the camera preview.
 * @param cameraConfiguration Lambda to configure the [CameraBuilder].
 * @param onCameraReady Callback invoked with the initialized [CameraEngine].
 */
@SuppressLint("RememberReturnType")
@Composable
actual fun PlatformCamera(
    modifier: Modifier,
    cameraConfiguration: CameraBuilder.() -> Unit,
    onCameraReady: (CameraEngine) -> Unit
) {
    Logger.d("PlatformCamera", "Composing.")
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val cameraEngine = AndroidCameraBuilder(context, lifecycleOwner)
            .apply(cameraConfiguration)
            .build()
    var error by remember { mutableStateOf<Throwable?>(null) }

    val previewView = if (cameraEngine.showPreview) {
        remember {
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FIT_CENTER
            }
        }
    } else {
        null
    }

    remember(cameraEngine) {
        coroutineScope.launch {
            try {
                cameraEngine.apply {
                    if (showPreview) {
                        bindCamera(previewView!!) {
                            onCameraReady(this)
                        }
                    } else {
                        bindCamera(null) {
                            onCameraReady(this)
                        }
                    }
                }
            } catch (e: Throwable) {
                Logger.e("PlatformCamera", "Camera binding failed", e)
                error = e
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraEngine.stopSession()
        }
    }

    if (cameraEngine.showPreview) {
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = modifier,
            factory = { previewView!! }
        )
    }
}