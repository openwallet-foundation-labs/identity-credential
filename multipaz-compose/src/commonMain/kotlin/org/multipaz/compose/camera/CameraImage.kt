package org.multipaz.compose.camera

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Platform-specific image from camera capture.
 */
expect class CameraImage {
    /**
     * Converts the platform-specific image data into a [androidx.compose.ui.graphics.ImageBitmap].
     */
    fun toImageBitmap(): ImageBitmap
}