package org.multipaz.compose

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toBitmapOrNull
import kotlinx.io.bytestring.ByteString
import org.multipaz.compose.camera.CameraFrame
import org.multipaz.context.applicationContext
import java.io.ByteArrayOutputStream

actual fun getApplicationInfo(appId: String): ApplicationInfo {
    val ai = applicationContext.packageManager.getApplicationInfo(appId, 0)
    val icon = applicationContext.packageManager.getApplicationIcon(ai)
    return ApplicationInfo(
        name = applicationContext.packageManager.getApplicationLabel(ai).toString(),
        icon = icon.toBitmap().asImageBitmap()
    )
}

actual fun decodeImage(encodedData: ByteArray): ImageBitmap {
    return BitmapFactory.decodeByteArray(encodedData, 0, encodedData.size).asImageBitmap()
}

actual fun encodeImageToPng(image: ImageBitmap): ByteString {
    val bitmap = image.asAndroidBitmap()
    val baos = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
    return ByteString(baos.toByteArray())
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
    outputWidthPx: Int,
    outputHeightPx: Int,
    targetWidthPx: Int
): ImageBitmap {
    val androidBitmap = frameData.cameraImage.imageProxy.toBitmap()
    val finalScale = targetWidthPx.toFloat() / outputWidthPx.toFloat()
    val finalOutputHeight = (outputHeightPx * finalScale).toInt()
    val matrix = Matrix() // Use Android's Matrix

    matrix.postTranslate(-cx.toFloat(), -cy.toFloat())
    matrix.postRotate(angleDegrees.toFloat())
    matrix.postTranslate((outputWidthPx / 2).toFloat(), (outputHeightPx / 2).toFloat())
    matrix.postScale(finalScale, finalScale)

    // Create the output bitmap with the final scaled dimensions.
    val resultBitmap = createBitmap(targetWidthPx, finalOutputHeight, androidBitmap.config ?: Bitmap.Config.ARGB_8888)
    Canvas(resultBitmap).drawBitmap(androidBitmap, matrix, paint)

    return resultBitmap.asImageBitmap()
}
