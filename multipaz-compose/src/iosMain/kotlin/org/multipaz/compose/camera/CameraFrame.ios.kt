package org.multipaz.compose.camera

import androidx.compose.ui.graphics.ImageBitmap
import platform.UIKit.UIImage

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual data class ImageData(val uiImage: UIImage) {
    actual fun toImageBitmap() : ImageBitmap {
        TODO("Not yet implemented")
    }
}