package org.multipaz.compose.camera

import androidx.compose.ui.graphics.ImageBitmap

actual data class ImageData(val imageBitmap: ImageBitmap) {
    actual fun toImageBitmap(): ImageBitmap {
        return imageBitmap
    }
}
