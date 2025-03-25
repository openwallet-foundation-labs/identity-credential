package org.multipaz.facedetect.camera.plugins.facedetect

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.max

/**
 * Transform coordinates of a point in MLKit coordinates to Screen preview coordinates.
 * Used to help draw on the canvas over the camera preview.
 *
 * @param point The point in MLKit coordinates (x, y).
 * @param detectorSize The size of the MLKit frame.
 * @param imageRotation The rotation of the image (Landscape Left is 0).
 * @param outputSize The pixel size of the preview view.
 * @param isFrontCamera Whether the camera is the front camera (accustom for mirroring, ignore otherwise).
 *
 * @return The transformed point.
 */
fun transformPoint(
    point: Offset?,
    detectorSize: Size,
    imageRotation: Int,
    outputSize: Size,
    isFrontCamera: Boolean = false
): Offset {
    if (point == null) return Offset(0f, 0f)

    val rotatedImageWidth = if (imageRotation == 90 || imageRotation == 270) detectorSize.height else detectorSize.width
    val rotatedImageHeight = if (imageRotation == 90 || imageRotation == 270) detectorSize.width else detectorSize.height

    val scale = max(outputSize.width, outputSize.height) / max(detectorSize.width, detectorSize.height)

    val dx: Float
    val dy: Float

    if (outputSize.width / outputSize.height > rotatedImageWidth / rotatedImageHeight) {
        dx = 0f
        dy = (outputSize.height - rotatedImageHeight * scale) / 2
    } else {
        dx = (outputSize.width - rotatedImageWidth * scale) / 2
        dy = 0f
    }

    val transformedPoint =
            Offset(
                x = point.x * scale + dx,
                y = point.y * scale + dy
            )
    // Mirroring.
    val finalX = if (isFrontCamera) outputSize.width - transformedPoint.x else transformedPoint.x

    return Offset(finalX, transformedPoint.y)
}

/**
 * Transform Rect shape coordinates defined in MLKit coordinates to Screen preview coordinates.
 * Used to help draw on the canvas over the camera preview.
 */
fun transformRect(
    rect: Rect,
    imageSize: Size,
    imageRotation: Int,
    previewSize: Size,
    isFrontCamera: Boolean = false
): Rect {
    val topLeft = transformPoint(rect.topLeft, imageSize, imageRotation, previewSize, isFrontCamera)
    val bottomRight = transformPoint(rect.bottomRight, imageSize, imageRotation, previewSize, isFrontCamera)
    return Rect(topLeft, bottomRight)
}
