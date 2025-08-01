package org.multipaz.compose.qrcode

import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import androidx.core.graphics.createBitmap

actual fun generateQrCode(
    url: String,
): ImageBitmap {
    val width = 800
    val result: BitMatrix = try {
        MultiFormatWriter().encode(
            url,
            BarcodeFormat.QR_CODE, width, width, null
        )
    } catch (e: WriterException) {
        throw java.lang.IllegalArgumentException(e)
    }
    val w = result.width
    val h = result.height
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        val offset = y * w
        for (x in 0 until w) {
            pixels[offset + x] = if (result[x, y]) Color.BLACK else Color.WHITE
        }
    }
    val bitmap = createBitmap(w, h)
    bitmap.setPixels(pixels, 0, width, 0, 0, w, h)
    return bitmap.asImageBitmap()
}
