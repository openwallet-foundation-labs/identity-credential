package org.multipaz.compose

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun decodeImage(encodedData: ByteArray): ImageBitmap {
    return BitmapFactory.decodeByteArray(encodedData, 0, encodedData.size).asImageBitmap()
}
