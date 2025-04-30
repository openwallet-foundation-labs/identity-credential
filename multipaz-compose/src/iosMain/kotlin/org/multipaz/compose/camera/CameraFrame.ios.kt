package org.multipaz.compose.camera

import androidx.compose.ui.graphics.ImageBitmap
import platform.UIKit.UIImage

actual data class ImageData(val uiImage: UIImage) {
    actual fun toImageBitmap() : ImageBitmap {
        TODO("Not yet implemented")
    }
}