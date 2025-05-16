package org.multipaz.compose.camera

import androidx.camera.core.ImageProxy
import androidx.compose.ui.graphics.ImageBitmap

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual data class ImageData(val imageBitmap: ImageBitmap) {
    actual fun toImageBitmap(): ImageBitmap {
        return imageBitmap
    }
}
