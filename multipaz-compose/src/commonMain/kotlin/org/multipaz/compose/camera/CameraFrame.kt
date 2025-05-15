package org.multipaz.compose.camera

import androidx.compose.ui.graphics.ImageBitmap

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

    /**
     * Native image rotation in degrees. Returned from Camera engine (android), simulated for iOS impl.
     * Usually you need to rotate the imageData bitmap for that angle to make it displayed upright in the preview.
     */
    val rotation: Int
) {
    fun toImageBitmap(): ImageBitmap {
        return imageData.toImageBitmap()
    }
}