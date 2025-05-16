package org.multipaz.compose.camera

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix

expect class ImageData {
    fun toImageBitmap(): ImageBitmap
}

data class CameraFrame(

    /** The platform native bitmap. */
    val imageData: ImageData,

    /** Image width in pixels. Same for native and common bitmap. Used for data validation, composition, drawing.*/
    val width: Int,

    /** Image height in pixels. Same for native and common bitmap. Used for data validation, composition, drawing.*/
    val height: Int,

    val transformation: Matrix
) {
    fun toImageBitmap(): ImageBitmap {
        return imageData.toImageBitmap()
    }
}