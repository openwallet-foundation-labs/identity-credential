package org.multipaz.compose.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap

private val topLeftPaint = Paint().apply {
    color = Color.RED
    style = Paint.Style.STROKE
    strokeWidth = 5f
}

private val topRightPaint = Paint().apply {
    color = Color.CYAN
    style = Paint.Style.STROKE
    strokeWidth = 5f
}

private val bottomRightPaint = Paint().apply {
    color = Color.GREEN
    style = Paint.Style.STROKE
    strokeWidth = 5f
}

private val bottomLeftPaint = Paint().apply {
    color = Color.YELLOW
    style = Paint.Style.STROKE
    strokeWidth = 5f
}

private val whitePaint = Paint().apply {
    color = Color.WHITE
    style = Paint.Style.STROKE
    strokeWidth = 5f
}

internal fun processCameraBitmap(bitmap: ImageBitmap): ImageBitmap {
    val originalBitmap = bitmap.asAndroidBitmap()
    originalBitmap.density = Bitmap.DENSITY_NONE

    val w = originalBitmap.width
    val h = originalBitmap.height
    val canvasBitmap = createBitmap(w, h, Bitmap.Config.ARGB_8888)
    canvasBitmap.density = Bitmap.DENSITY_NONE

    val sz = 40f
    val canvas = Canvas(canvasBitmap)
    with(canvas) {
        density = Bitmap.DENSITY_NONE

        canvas.drawBitmap(originalBitmap, 0f, 0f, whitePaint)

        // Frame Border
        drawRect(Rect(0, 0, w, h), whitePaint)

        //Corner Markers
        // TL
        drawOval(1f, 1f, sz, sz, topLeftPaint)
        // TR
        drawArc(w - sz, 1f, w - 1f, sz, 270f, 90f, true, topRightPaint)
        // BR
        drawRect(w - sz, h - sz, w - 1f, h - 1f, bottomRightPaint)
        // BL
        drawArc(1f, h - sz, sz, h - 1f, 0f, 180f, true, bottomLeftPaint)
    }

    val matrix = Matrix()
    matrix.postRotate(270f) //  Portrait.
    val rotatedBitmap = Bitmap.createBitmap(canvasBitmap, 0, 0, w, h, matrix, true)

    return rotatedBitmap.asImageBitmap()
}