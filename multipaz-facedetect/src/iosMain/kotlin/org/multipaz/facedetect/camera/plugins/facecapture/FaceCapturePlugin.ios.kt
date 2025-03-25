package org.multipaz.facedetect.camera.plugins.facecapture

/**
 * Platform-specific implementation of the [rememberBitmapSaverPlugin] factory function.
 */
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.autoreleasepool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.multipaz.compose.camera.CameraViewController
import org.multipaz.compose.camera.CameraWorkResult
import org.multipaz.util.Logger
import org.multipaz.util.toByteArray
import platform.CoreGraphics.CGContextRotateCTM
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGFloat
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSDate
import platform.Foundation.NSTimeInterval
import platform.Foundation.date
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import kotlin.coroutines.resume
import kotlin.math.PI

/**
 * iOS-specific implementation of [FaceCapturePlugin].
 *
 * @param config The configuration settings for the plugin.
 * @param onImageSaved Callback invoked when the image is successfully saved.
 * @param onImageSavedFailed Callback invoked when the image saving fails.
 */
class PlatformFaceCapturePlugin(
    config: FaceCaptureConfig,
    private val onImageSaved: () -> Unit,
    private val onImageSavedFailed: (String) -> Unit
) : FaceCapturePlugin(config) {

    private var isCapturing = atomic(false)
    private val throttlePeriod = 500L
    private var lastCaptureTime: NSTimeInterval = 0.0

    override suspend fun captureFace(
        detectedFace: CameraWorkResult,
        imageName: String
    ): Flow<ImageBitmap> {
        // TODO("Not yet implemented")
        Logger.d("captureFace", "Not implemented")
        TODO("Not yet implemented")

        /*return withContext(Dispatchers.Main) {
            try {
                val bitmap = KmpBitmap()
                bitmap.imageData = byteArray
                val nsData = UIImagePNGRepresentation(bitmap.decode())

                if (nsData == null) {
                    println("Failed to convert ByteArray to NSData.")
                    onImageSavedFailed("Failed to convert ByteArray to NSData.")
                    return@withContext null
                }

                val image = UIImage.imageWithData(nsData)

                if (image == null) {
                    println("Failed to convert NSData to UIImage.")
                    onImageSavedFailed("Failed to create UIImage from NSData.")
                    return@withContext null
                }

                var assetId: String? = null
                val semaphore = NSCondition()

                semaphore.wait()
                assetId
            } catch (e: Exception) {
                println("Exception while saving image: ${e.message}")
                null
            }*/
    }

    /**
     * Captures an image with the capture frequency throttling. Not used in theis impl.
     *
     * @return The result of the image capture operation.
     */
    @OptIn(BetaInteropApi::class)
    suspend fun takeCameraFrame(): CameraWorkResult = suspendCancellableCoroutine { continuation ->
        val currentTime = NSDate.date().timeIntervalSince1970()
        if (currentTime - lastCaptureTime < throttlePeriod) {
            continuation.resume(CameraWorkResult.Error(Exception("Capture too frequent")))
            return@suspendCancellableCoroutine
        }

        if (!isCapturing.compareAndSet(false, true)) {
            continuation.resume(CameraWorkResult.Error(Exception("Capture already in progress")))
            return@suspendCancellableCoroutine
        }

        val engine = cameraEngine as CameraViewController?

        engine?.onFrameCapture = { image ->
            try {
                if (image != null) {
                    autoreleasepool {
                        // Using UIImageJPEGRepresentation to convert to JPEG formatJPEG Format {
                        UIImageJPEGRepresentation(UIImage(image), 0.9)?.toByteArray()
                            ?.let { imageData ->
                                continuation.resume(CameraWorkResult.FrameCaptureSuccess(imageData))
                            } ?: run {
                            continuation.resume(CameraWorkResult.Error(Exception("JPEG conversion failed")))
                        }
                    }
                } else {
                    continuation.resume(CameraWorkResult.Error(Exception("Capture failed - null image")))
                }
            } finally {
                lastCaptureTime = NSDate.date().timeIntervalSince1970()
                isCapturing.compareAndSet(expect = true, update = false)
                engine?.onFrameCapture = null
            }
        }

        continuation.invokeOnCancellation {
            engine?.onFrameCapture = null
            isCapturing.compareAndSet(expect = true, update = false)
        }

        engine?.captureFrame()
    }
}

/** Alias PlatformImage to UIImage. */
actual typealias PlatformImage = UIImage

/** Helper: Convert degrees to radians. */
private fun Double.toRadians() = this * PI / 180.0
private fun Double.toCGFloat(): CGFloat = this

/**
 * Performs a crop and rotation of the [input] image.
 *
 * The operation is defined so that the input coordinate (cx, cy) becomes the center
 * of the output bitmap (with dimensions [outputWidth] x [outputHeight]), and
 * the image is rotated around that point by [angleDegrees] (counterclockwise).
 *
 * @param input The input image.
 * @param cx The x coordinate (in input image space) to center.
 * @param cy The y coordinate (in input image space) to center.
 * @param angleDegrees The rotation angle (in degrees; counterclockwise) to apply.
 * @param outputWidth The desired output image width in pixels.
 * @param outputHeight The desired output image height in pixels.
 *
 * @return The resulting image after crop and rotation.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun cropRotateScale(
    input: PlatformImage,
    cx: Double,
    cy: Double,
    angleDegrees: Double,
    outputWidth: Int,
    outputHeight: Int,
    targetWidth: Int
): PlatformImage {
    // Compute the final scaling factor.
    val sFinal = targetWidth.toDouble() / outputWidth.toDouble()
    val finalOutputWidth = targetWidth.toDouble()
    val finalOutputHeight = outputHeight * sFinal

    // Begin the UIGraphics image context with final output size.
    val finalSize = CGSizeMake(finalOutputWidth, finalOutputHeight)
    UIGraphicsBeginImageContextWithOptions(finalSize, false, 0.0)
    val context = UIGraphicsGetCurrentContext() ?: error("Failed to get graphics context")

    // In iOS, the coordinate system origin is top left.
    // We want to perform the following (in order):
    // (a) Translate so that the pre-scale output center is reached.
    // (b) Rotate by angleDegrees (converted to radians).
    // (c) Translate by –(cx,cy) so that the pivot maps to the center.
    // (d) Finally, apply the final scaling so that the drawn image is scaled appropriately.
    //
    // However, since CGContext applies transforms in order,
    // we can combine the final scaling with the initial translation.
    //
    // Compute pre-scale center (in the “virtual” output coordinate system).
    val preScaleCenterX = outputWidth / 2.0
    val preScaleCenterY = outputHeight / 2.0

    // Apply final scaling first; this ensures our pre-scale measurements are scaled.
    CGContextScaleCTM(context, sFinal.toCGFloat(), sFinal.toCGFloat())
    // Now the effective canvas size is still in virtual coordinates.
    // Translate to the center of the virtual output.
    CGContextTranslateCTM(context, preScaleCenterX.toCGFloat(), preScaleCenterY.toCGFloat())
    // Rotate by the specified angle.
    CGContextRotateCTM(context, angleDegrees.toRadians().toCGFloat())
    // Translate by the negative pivot to center the crop.
    CGContextTranslateCTM(context, (-cx).toCGFloat(), (-cy).toCGFloat())

    // Draw the input image in its natural size at (0,0).
    // This assumes the input image’s origin is at (0,0).
    input.drawAtPoint(platform.CoreGraphics.CGPointMake(0.0, 0.0))

    // Get the resulting image from the context.
    val outputImage = UIGraphicsGetImageFromCurrentImageContext() ?: error("Failed to obtain image from context")
    UIGraphicsEndImageContext()
    return outputImage
}

/**
 * Factory function to create an iOS-specific [FaceCapturePlugin].
 *
 * @param config Configuration settings for the plugin.
 * @return An instance of [PlatformFaceCapturePlugin].
 */
actual fun createPlatformFaceCapturePlugin(
    config: FaceCaptureConfig
): FaceCapturePlugin {

    return PlatformFaceCapturePlugin(config = config, onImageSaved = {

        println("Image saved successfully!")
    }, onImageSavedFailed = { errorMessage ->

        println("Failed to save image: $errorMessage")
    })
}