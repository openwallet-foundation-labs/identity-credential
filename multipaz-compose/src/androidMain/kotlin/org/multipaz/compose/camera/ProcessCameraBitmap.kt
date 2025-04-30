package org.multipaz.compose.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Demo circles. Portrait, front camera only. */
internal fun processCameraBitmap(cameraBitmap: ImageBitmap, overlayBitmap: ImageBitmap?): ImageBitmap {
    val bitmap = cameraBitmap.asAndroidBitmap()
    val mutableBaseBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutableBaseBitmap)
    canvas.density = bitmap.density

    if (overlayBitmap != null) {
        canvas.drawBitmap(overlayBitmap.asAndroidBitmap(), 0f, 0f, Paint())
    }

    // Rotate to portrait.
    val matrix = Matrix()
    matrix.postRotate(270f) //  Portrait.
    matrix.postScale(-1f, 1f) //  Mirror.
    val rotatedBitmap = Bitmap.createBitmap(mutableBaseBitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

    return rotatedBitmap.asImageBitmap()
}