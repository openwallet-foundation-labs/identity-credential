package org.multipaz.compose

import androidx.compose.ui.graphics.ImageBitmap

expect fun decodeImage(encodedData: ByteArray): ImageBitmap
