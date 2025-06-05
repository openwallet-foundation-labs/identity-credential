package org.multipaz.compose

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import org.jetbrains.skia.Image
import org.multipaz.compose.camera.CameraFrame
import org.multipaz.compose.camera.CameraImage
import platform.CoreGraphics.CGAffineTransformConcat
import platform.CoreGraphics.CGAffineTransformMakeRotation
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.CoreGraphics.CGContextConcatCTM
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import kotlin.math.PI

actual fun decodeImage(encodedData: ByteArray): ImageBitmap {
    return Image.makeFromEncoded(encodedData).toComposeImageBitmap()
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
