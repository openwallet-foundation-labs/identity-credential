package org.multipaz.compose.camera

import androidx.camera.core.ImageProxy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Android-specific implementation of [org.multipaz.compose.camera.CameraImage].
 *
 * @param imageProxy the [ImageProxy] representing the image.
 */
actual data class CameraImage(val imageProxy: ImageProxy) {
    actual fun toImageBitmap(): ImageBitmap {
        return imageProxy.toBitmap().asImageBitmap()
    }
}