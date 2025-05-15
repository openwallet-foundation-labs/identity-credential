package org.multipaz.compose.camera

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.multipaz.util.Logger

private const val TAG = "Camera"

/** Not implemented fully. Mock only. */
@Composable
actual fun Camera(
    modifier: Modifier,
    cameraSelection: CameraSelection,
    captureResolution: CameraCaptureResolution,
    showCameraPreview: Boolean,
    onFrameCaptured: suspend (frame: CameraFrame) -> ImageBitmap?
) {
    val cameraManager = remember { CameraManager(cameraSelection, captureResolution) }
    var latestFrame by remember { mutableStateOf<CameraFrame?>(null) }
    var overlayBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var cameraActive by remember { mutableStateOf(true) }

    val scope = CoroutineScope(Dispatchers.Default) // Manually manage CoroutineScope

    LaunchedEffect(key1 = Unit) {
        cameraManager.startCamera { frame ->
            Logger.d(TAG, "Cam frame in")
            if (cameraActive) {
                latestFrame = frame
                Logger.d(TAG, "Cam active, process")
                scope.launch {
                    try {
                        if (cameraActive) {
                            Logger.d(TAG, "onFrame call...")
                            overlayBitmap = onFrameCaptured(frame)
                        }
                    } catch (_: CancellationException) {
                        Logger.d(TAG, "Cancellation.")
                    }
                }
            }
            overlayBitmap
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraActive = false
            Logger.d(TAG, "Cam stop")
            cameraManager.stopCamera()
            scope.cancel()
        }
    }

    if (showCameraPreview) {
        if (overlayBitmap != null &&
            (overlayBitmap?.width != latestFrame?.width || overlayBitmap?.height != latestFrame?.height)
        ) {
            Logger.w(
                TAG, "Supplied overlay bitmap has the wrong size for the preview " +
                        "(${overlayBitmap?.width}x${overlayBitmap?.height})."
            )
            overlayBitmap = null // Discard the overlay bitmap.
        }

        if (latestFrame != null) {
                // MOCK only.
                overlayBitmap?.let { frame ->
                    Image(
                        bitmap = frame,
                        contentDescription = "Live Processed Frame",
                        modifier = modifier.fillMaxSize()
                    )
                }
        } else {
            if (overlayBitmap != null) {
                Logger.w(TAG, "Camera received the frame overlay data, but no preview is requested.")
            }
        }
    }
}
