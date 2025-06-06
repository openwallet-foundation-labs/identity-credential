package org.multipaz.compose

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.refTo
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
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
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy
import kotlin.math.PI

actual fun decodeImage(encodedData: ByteArray): ImageBitmap {
    return Image.makeFromEncoded(encodedData).toComposeImageBitmap()
}

actual fun encodeImageAsPng(imageBitmap: ImageBitmap): ByteArray? {
    val uiImage = imageBitmap.toUIImage()
        ?: throw RuntimeException("Could not convert imageBitmap to UIImage.")
    val nsData: NSData? = UIImagePNGRepresentation(uiImage)
    return nsData?.toByteArray()
        ?: throw RuntimeException("Could not convert UIImage to PNG NSData or NSData is null.")
}

@OptIn(ExperimentalForeignApi::class)
actual fun cropRotateScaleImage(
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
        scale = originalUIImage.scale
        preferredRange = 2
    }
    val renderer = UIGraphicsImageRenderer(
        size = CGSizeMake(finalOutputWidth, finalOutputHeight),
        format = rendererFormat
    )

    val resultUIImage = renderer.imageWithActions { context ->
        val cgContext = context?.CGContext
        var transform = CGAffineTransformMakeTranslation(finalOutputWidth / 2.0, finalOutputHeight / 2.0)
        transform = CGAffineTransformConcat(CGAffineTransformMakeScale(finalScale, finalScale), transform)
        transform = CGAffineTransformConcat(CGAffineTransformMakeRotation(angleDegrees * PI / 180.0), transform)
        transform = CGAffineTransformConcat(CGAffineTransformMakeTranslation(-cx, -cy), transform)
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

/** Helper to convert NSData to ByteArray (common in KMP). */
@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray = ByteArray(this.length.toInt()).apply {
    usePinned { pinned ->
        memcpy(pinned.addressOf(0), this@toByteArray.bytes, this@toByteArray.length)
    }
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
