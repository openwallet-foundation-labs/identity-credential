package org.multipaz.compose

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.refTo
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.io.bytestring.ByteString
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.multipaz.compose.camera.CameraFrame
import org.multipaz.compose.camera.CameraImage
import platform.CoreGraphics.CGAffineTransformConcat
import platform.CoreGraphics.CGAffineTransformMakeRotation
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextConcatCTM
import platform.CoreGraphics.CGContextRotateCTM
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.posix.memcpy
import kotlin.math.PI

actual fun getApplicationInfo(appId: String): ApplicationInfo {
    throw NotImplementedError("This information is not available not implemented on iOS")
}

actual fun decodeImage(encodedData: ByteArray): ImageBitmap {
    return Image.makeFromEncoded(encodedData).toComposeImageBitmap()
}

actual fun encodeImageToPng(image: ImageBitmap): ByteString {
    val data = Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG, 100)
        ?: throw IllegalStateException("Error encoding image to PNG")
    return ByteString(data.bytes)
}


actual fun cropRotateScaleImage(
    frameData: CameraFrame,
    cx: Double, // ASSUMED: from top-left of image data, Y increases downwards
    cy: Double, // ASSUMED: from top-left of image data, Y increases downwards
    angleDegrees: Double,
    outputWidthPx: Int,
    outputHeightPx: Int,
    targetWidthPx: Int
): ImageBitmap {
    return cropRotateScaleImage(
        originalUIImage = frameData.cameraImage.uiImage,
        isLandscape = frameData.isLandscape,
        cx = cx,
        cy = cy,
        angleDegrees = angleDegrees,
        outputWidthPx = outputWidthPx,
        outputHeightPx = outputHeightPx,
        targetWidthPx = targetWidthPx
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun cropRotateScaleImage(
    originalUIImage: UIImage,
    isLandscape: Boolean,
    cx: Double, // ASSUMED: from top-left of image data, Y increases downwards
    cy: Double, // ASSUMED: from top-left of image data, Y increases downwards
    angleDegrees: Double,
    outputWidthPx: Int,
    outputHeightPx: Int,
    targetWidthPx: Int
): ImageBitmap {
    val originalImageWidth = originalUIImage.size.useContents { width }
    val originalImageHeight = originalUIImage.size.useContents { height }

    val scaleFactor = targetWidthPx.toDouble() / outputWidthPx.toDouble()
    val finalCanvasWidth = targetWidthPx.toDouble()
    val finalCanvasHeight = outputHeightPx.toDouble() * scaleFactor

    val rendererFormat = UIGraphicsImageRendererFormat.defaultFormat().apply {
        opaque = true; scale = 0.0; preferredRange = 2
    }
    val renderer = UIGraphicsImageRenderer(
        size = CGSizeMake(finalCanvasWidth, finalCanvasHeight),
        format = rendererFormat
    )

    val resultUIImage = renderer.imageWithActions { rendererContext ->
        val cgContext = rendererContext?.CGContext

        // --- Step 1: Adjust context orientation based on output mode ---
        // Goal: Make the context effectively (0,0) at top-left, Y increases downwards
        // for subsequent drawing of UIImage.

        if (!isLandscape) { // PORTRAIT
            // Standard flip for portrait: UIImage (0,0) is top-left, CGContext default is often bottom-left.
            CGContextTranslateCTM(cgContext, 0.0, finalCanvasHeight)
            CGContextScaleCTM(cgContext, 1.0, -1.0)
        } else { // LANDSCAPE
            // If the portrait flip makes landscape upside down, it implies that for landscape,
            // the context as provided by UIGraphicsImageRenderer might already be
            // effectively top-left, Y-down, or it's rotated in a way that the standard
            // portrait flip is incorrect.
            // HYPOTHESIS: No initial flip is needed for landscape if it's already top-left Y-down.
            // (If this assumption is wrong, landscape images will be upside down OR correctly oriented
            // if the renderer provides a Y-down context for landscape by default).
        }

        // --- Step 2: Transformations to center (cx,cy) and crop/scale ---
        // (cx,cy) are from top-left of original image.

        // a. Move drawing origin to the center of the output canvas
        CGContextTranslateCTM(cgContext, finalCanvasWidth / 2.0, finalCanvasHeight / 2.0)

        // b. Scale based on desired output crop becoming target width
        CGContextScaleCTM(cgContext, scaleFactor, scaleFactor)

        // c. Rotate. angleDegrees is defined for the image being upright.
        CGContextRotateCTM(cgContext, -angleDegrees * PI / 180.0)

        // d. Translate so that (cx,cy) of the original image is at the current (transformed) origin.
        CGContextTranslateCTM(cgContext, -cx, -cy)

        // --- Step 3: Draw ---
        originalUIImage.drawInRect(
            CGRectMake(0.0, 0.0, originalImageWidth, originalImageHeight)
        )
    }
    return CameraImage(resultUIImage).toImageBitmap()
}


@OptIn(ExperimentalForeignApi::class)
fun cropRotateScaleImage0(
    frameData: CameraFrame,
    cx: Double,
    cy: Double,
    angleDegrees: Double,
    outputWidth: Int,
    outputHeight: Int,
    targetWidth: Int
): ImageBitmap {
    val originalUIImage = frameData.cameraImage.uiImage
    val finalScale = targetWidth.toDouble() / outputWidth.toDouble()
    val finalOutputWidth = targetWidth.toDouble()
    val finalOutputHeight = outputHeight.toDouble() * finalScale
    val rendererFormat = UIGraphicsImageRendererFormat.defaultFormat().apply {
        opaque = true
        scale = originalUIImage.scale //todo remove?
        preferredRange = 2
    }

    // Compensate for iOS camera flip direction.
    val cy2 = frameData.height - cy

    val renderer = UIGraphicsImageRenderer(
        size = CGSizeMake(finalOutputWidth, finalOutputHeight),
        format = rendererFormat
    )

    val resultUIImage = renderer.imageWithActions { context ->
        val cgContext = context?.CGContext
        var transform = CGAffineTransformMakeTranslation(finalOutputWidth / 2.0, finalOutputHeight / 2.0)
        transform = CGAffineTransformConcat(CGAffineTransformMakeScale(finalScale, finalScale), transform)
        transform = CGAffineTransformConcat(CGAffineTransformMakeRotation(angleDegrees * PI / 180.0), transform)
        transform = CGAffineTransformConcat(CGAffineTransformMakeTranslation(-cx, -cy2), transform)
        CGContextConcatCTM(cgContext, transform)

        originalUIImage.drawInRect(
            CGRectMake(
                0.0,
                0.0,
                originalUIImage.size.useContents { width },
                originalUIImage.size.useContents { height }
            )
        )
    }

    return CameraImage(resultUIImage).toImageBitmap()
}

/** Convert ImageBitmap to iOS UIImage. */
@OptIn(ExperimentalForeignApi::class)
fun ImageBitmap.toUIImage(): UIImage? {
    val width = this.width
    val height = this.height
    val buffer = IntArray(width * height)

    this.readPixels(buffer)

    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val context = CGBitmapContextCreate(
        data = buffer.refTo(0),
        width = width.toULong(),
        height = height.toULong(),
        bitsPerComponent = 8u,
        bytesPerRow = (4 * width).toULong(),
        space = colorSpace,
        bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value
    )

    val cgImage = CGBitmapContextCreateImage(context)
    return cgImage?.let { UIImage.imageWithCGImage(it) }
}

actual fun ImageBitmap.cropRotateScaleImage(
    cx: Double,
    cy: Double,
    angleDegrees: Double,
    outputWidthPx: Int,
    outputHeightPx: Int,
    targetWidthPx: Int
): ImageBitmap {
    return cropRotateScaleImage(
        originalUIImage = toUIImage()!!,
        isLandscape = false,
        cx = cx,
        cy = cy,
        angleDegrees = angleDegrees,
        outputWidthPx = outputWidthPx,
        outputHeightPx = outputHeightPx,
        targetWidthPx = targetWidthPx
    )
}
