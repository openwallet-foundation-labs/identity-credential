package org.multipaz.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

@OptIn(ExperimentalFoundationApi::class)
actual fun decodeImage(encodedData: ByteArray): ImageBitmap {
    return Image.makeFromEncoded(encodedData).toComposeImageBitmap()
}
