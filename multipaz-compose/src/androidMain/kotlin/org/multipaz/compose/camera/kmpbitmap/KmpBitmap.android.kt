package org.multipaz.compose.camera.kmpbitmap

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import androidx.core.graphics.scale

actual typealias PlatformImage = Bitmap

actual fun platformInitialize(image: PlatformImage): ByteArray {
    val bitmap = image as Bitmap
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
    return outputStream.toByteArray()
}

actual fun platformScale(byteArray: ByteArray, width: Int, height: Int): ByteArray {
    val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    val scaledBitmap = bitmap.scale(width, height)
    val outputStream = ByteArrayOutputStream()
    scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
    return outputStream.toByteArray()
}

actual fun platformDecode(byteArray: ByteArray): PlatformImage {
    return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
}