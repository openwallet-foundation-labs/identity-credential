package org.multipaz.compose

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import org.multipaz.compose.camera.CameraFrame

actual fun decodeImage(encodedData: ByteArray): ImageBitmap {
    return BitmapFactory.decodeByteArray(encodedData, 0, encodedData.size).asImageBitmap()
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
