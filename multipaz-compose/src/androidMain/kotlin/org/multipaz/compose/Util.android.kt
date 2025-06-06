package org.multipaz.compose

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import org.multipaz.compose.camera.CameraFrame
import com.google.zxing.qrcode.QRCodeWriter

actual fun decodeImage(encodedData: ByteArray): ImageBitmap {
    return BitmapFactory.decodeByteArray(encodedData, 0, encodedData.size).asImageBitmap()
}

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
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, w, h)
    return bitmap.asImageBitmap()
}


private val paint = Paint().apply {
    isAntiAlias = false
    isFilterBitmap = false // Improves scaling quality
}

actual fun cropRotateScaleImage(
    frameData: CameraFrame,
    cx: Double,
    cy: Double,
    angleDegrees: Double,
    outputWidth: Int,
    outputHeight: Int,
    targetWidth: Int
): ImageBitmap {
    val androidBitmap = frameData.cameraImage.imageProxy.toBitmap()
    val finalScale = targetWidth.toFloat() / outputWidth.toFloat()
    val finalOutputHeight = (outputHeight * finalScale).toInt()
    val matrix = android.graphics.Matrix() // Use Android's Matrix

    matrix.postTranslate(-cx.toFloat(), -cy.toFloat())
    matrix.postRotate(angleDegrees.toFloat())
    matrix.postTranslate((outputWidth / 2).toFloat(), (outputHeight / 2).toFloat())
    matrix.postScale(finalScale, finalScale)

    // Create the output bitmap with the final scaled dimensions.
    val resultBitmap = createBitmap(targetWidth, finalOutputHeight, androidBitmap.config ?: Bitmap.Config.ARGB_8888)
    Canvas(resultBitmap).drawBitmap(androidBitmap, matrix, paint)

    return resultBitmap.asImageBitmap()
}
