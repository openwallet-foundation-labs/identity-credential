package org.multipaz.compose

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.pin
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.useContents
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.multipaz.SwiftBridge
import org.multipaz.compose.camera.CameraFrame
import org.multipaz.compose.camera.CameraImage
import org.multipaz.compose.camera.toSkiaImage
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGAffineTransformConcat
import platform.CoreGraphics.CGAffineTransformMakeRotation
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.CoreGraphics.CGContextConcatCTM
import platform.CoreGraphics.CGDataProviderCopyData
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetAlphaInfo
import platform.CoreGraphics.CGImageGetBytesPerRow
import platform.CoreGraphics.CGImageGetDataProvider
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreImage.CIContext
import platform.CoreImage.CIImage
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import kotlin.math.PI

actual fun decodeImage(encodedData: ByteArray): ImageBitmap {
    return Image.makeFromEncoded(encodedData).toComposeImageBitmap()
}


@OptIn(ExperimentalForeignApi::class)
private fun CIImageToSkiaImage(ciiimage: CIImage): Image {
    val imageRef = ciiimage.CGImage

    val width = CGImageGetWidth(imageRef).toInt()
    val height = CGImageGetHeight(imageRef).toInt()

    val bytesPerRow = CGImageGetBytesPerRow(imageRef)
    val data = CGDataProviderCopyData(CGImageGetDataProvider(imageRef))
    val bytePointer = CFDataGetBytePtr(data)
    val length = CFDataGetLength(data)
    val alphaInfo = CGImageGetAlphaInfo(imageRef)

    val alphaType = when (alphaInfo) {
        CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst, CGImageAlphaInfo.kCGImageAlphaPremultipliedLast -> ColorAlphaType.PREMUL
        CGImageAlphaInfo.kCGImageAlphaFirst, CGImageAlphaInfo.kCGImageAlphaLast -> ColorAlphaType.UNPREMUL
        CGImageAlphaInfo.kCGImageAlphaNone, CGImageAlphaInfo.kCGImageAlphaNoneSkipFirst, CGImageAlphaInfo.kCGImageAlphaNoneSkipLast -> ColorAlphaType.OPAQUE
        else -> ColorAlphaType.UNKNOWN
    }

    val byteArray = bytePointer!!.readBytes(length.toInt())
    CFRelease(data)
    CFRelease(imageRef)

    return Image.makeRaster(
        imageInfo = ImageInfo(width = width, height = height, colorType = ColorType.RGBA_8888, alphaType = alphaType),
        bytes = byteArray,
        rowBytes = bytesPerRow.toInt(),
    )
}

@OptIn(ExperimentalForeignApi::class)
actual fun generateQrCode(
    url: String,
): ImageBitmap {
    return (SwiftBridge.generateQrCode(url) as UIImage?)?.toSkiaImage()?.toComposeImageBitmap()
        ?: throw IllegalStateException()
}

@OptIn(ExperimentalForeignApi::class)
actual fun cropRotateScaleImage(
    frameData: CameraFrame, //UIImage
    cx: Double,
    cy: Double,
    angleDegrees: Double,
    outputWidth: Int,
    outputHeight: Int,
    targetWidth: Int
): ImageBitmap {
    val originalUIImage = frameData.cameraImage.uiImage // You'll need a way to convert ImageBitmap to UIImage

    // 2. Calculate final dimensions and scale factor
    val finalScale = targetWidth.toDouble() / outputWidth.toDouble()
    val finalOutputWidth = targetWidth.toDouble()
    val finalOutputHeight = outputHeight.toDouble() * finalScale

    // 3. Set up the drawing context with the *final* output dimensions
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

        // Draw the original (normalized) UIImage.
        // It's drawn at (0,0) in its own coordinate system. The CTM handles the rest.
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
