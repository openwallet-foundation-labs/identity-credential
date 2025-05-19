package org.multipaz.compose.camera

import androidx.compose.ui.graphics.Matrix

/**
 * Type containing a frame captured from the [Camera] composable.
 */
data class CameraFrame(

    /** The platform-specific image. */
    val cameraImage: CameraImage,

    /**
     * Image width in pixels.
     */
    val width: Int,

    /**
     * Image height in pixels.
     */
    val height: Int,

    /**
     * A matrix to convert from coordinates in [CameraImage] to coordinates in the preview.
     *
     * This can be used to render graphics on top of the preview, for example to put boxes
     * around barcodes or to draw facial landmarks.
     *
     * This may involve mirroring, scaling, translation, and rotation.
     *
     * If preview is disabled, this is the identity matrix.
     */
    val previewTransformation: Matrix
)