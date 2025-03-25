package org.multipaz.facedetect.camera.plugins.facecapture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.multipaz.compose.camera.CameraWorkResult
import kotlin.math.atan2
import kotlin.math.sqrt

actual typealias PlatformImage = Bitmap

/**
 * Android-specific implementation of [FaceCapturePlugin] using Scoped Storage.
 *
 * @param config The configuration settings for the plugin.
 */
class PlatformFaceCapturePlugin(
    config: FaceCaptureConfig
) : FaceCapturePlugin(config) {

    override suspend fun captureFace(
        detectedFace: CameraWorkResult,
        imageName: String
    ): Flow<ImageBitmap> = callbackFlow {
        val engine = cameraEngine ?: throw IllegalStateException("CameraEngine not initialized")
        val imageCapture = engine.imageCapture ?: throw IllegalStateException("CameraEngine not initialized")

        val callback = object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                if (detectedFace != CameraWorkResult.Unsatisfactory) {
                    trySend(
                        cropImageWithRotation(
                            imageProxy = imageProxy,
                            detectionData = detectedFace as CameraWorkResult.FaceDetectionSuccess,
                            isFrontCamera = config.isFrontCamera
                        )
                    )
                    imageProxy.close() // Free resources
                    channel.close()
                } else {
                    Toast.makeText(
                        this@PlatformFaceCapturePlugin.cameraEngine!!.context,
                        "Face lost. Try again", Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                close(exception)
            }
        }
        imageCapture.takePicture(ContextCompat.getMainExecutor(engine.context), callback)

        awaitClose {
            // When channel is closed nothing to do.
        }
    }

    /** Calculate angle (Radians) of the "mouth to the middle point between eyes" line from vertical axis. */
    private fun calculateFaceRotation(faceData: CameraWorkResult.FaceData): Float {
        val l = faceData.leftEyePosition ?: return 0f
        val r = faceData.rightEyePosition ?: return 0f
        return atan2(l.x - r.x, l.y - r.y) * 57.2958f
    }

    /** Cut out the face square, rotate it to level eyes line, scale to the smaller size for face matching tasks. */
    fun cropImageWithRotation(
        imageProxy: ImageProxy,
        detectionData: CameraWorkResult.FaceDetectionSuccess,
        isFrontCamera: Boolean = false
    ): ImageBitmap {

        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val inputBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val bitmapSize = Size(inputBitmap.width.toFloat(), inputBitmap.height.toFloat())

        // TODO: AK - assuming iOS detector here is equal to bitmap size.
        val detectorSize = if (detectionData.imageSize == null) {
            Size(inputBitmap.width.toFloat(), inputBitmap.height.toFloat())
        } else {
            Size(detectionData.imageSize!!.width, detectionData.imageSize!!.height)
        }
        val cameraAngle = detectionData.cameraAngle ?: 0 // 0 for iOS
        val leftEye = scaleCoordinates(
            detectionData.faceData.leftEyePosition!!,
            detectorSize,
            cameraAngle,
            bitmapSize,
            isFrontCamera
        )
        val rightEye = scaleCoordinates(
            detectionData.faceData.rightEyePosition!!,
            detectorSize,
            cameraAngle,
            bitmapSize,
            isFrontCamera
        )

        // Face center is between eyes.
        val faceCenter = //if(cameraAngle == 270 || cameraAngle==90) {
            Offset((leftEye.x + rightEye.x) / 2, (leftEye.y + rightEye.y) / 2)
//        }
//        else {
//            Offset((leftEye.y + rightEye.y) / 2, (leftEye.x + rightEye.x) / 2)
//        }


        // Eye offset from center.
        val eyeOffset = sqrt(
            (leftEye.x - rightEye.x) * (leftEye.x - rightEye.x) + (leftEye.y - rightEye.y) * (leftEye.y - rightEye.y)
        ) / 2

        // Define face size by eye offsets.
        val faceWidth = eyeOffset * 6

        // TODO: AK - null is passed by iOS as it does not have it so use 0 + 90 for now
        val outputAngle: Double = cameraAngle.toDouble() + 90

        return cropRotateScale(
            inputBitmap,
            cx = faceCenter.x.toDouble(),
            cy = faceCenter.y.toDouble(),
            angleDegrees = outputAngle + calculateFaceRotation(detectionData.faceData).toDouble(),
            outputWidth = faceWidth.toInt(),
            outputHeight = faceWidth.toInt(),
            targetWidth = config.finalSizeWidth
        ).asImageBitmap()
    }

    /** Accustom point coordinates to its detection frame and the captured frame sizes difference. */
    private fun scaleCoordinates(
        point: Offset,
        inSize: Size,
        imageRotation: Int,
        outSize: Size,
        isFrontCamera: Boolean,
    ): Offset {
        val scaleX = outSize.width / inSize.width
        val scaleY = outSize.height / inSize.height

        var x = point.x * scaleX
        var y = point.y * scaleY

        // MLKit returns different screen angle for front/rear. E.g. front 270 corresponds to rear 90.
        when (imageRotation) {
            270 -> {
                if (isFrontCamera) {
                    val tmp = x
                    x = outSize.width - y
                    y = tmp
                } else {
                    val tmp = x
                    x = outSize.width - y
                    y = tmp
                }
            }

            90 -> {
                if (isFrontCamera) {
                    val tmp = x
                    x = y
                    y = outSize.height - tmp
                } else {
                    val tmp = x
                    x = y
                    y = outSize.height - tmp
                }
            }

            180 -> {
                x = outSize.width - x
                y = outSize.height - y
            }

            0 -> {
                // No change needed for rotation 0.
            }
        }

        return Offset(x, y)
    }
}

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
actual fun cropRotateScale(
    input: PlatformImage,
    cx: Double,
    cy: Double,
    angleDegrees: Double,
    outputWidth: Int,
    outputHeight: Int,
    targetWidth: Int
): PlatformImage {
    // Compute the final scaling factor:
    val finalScale = targetWidth.toFloat() / outputWidth.toFloat()
    val finalOutputWidth = targetWidth
    val finalOutputHeight = (outputHeight * finalScale).toInt()

    // Build the transformation matrix.
    // 1. Translate the input so that (cx,cy) goes to origin.
    // 2. Rotate by angleDegrees.
    // 3. Translate so that the origin goes to the center of the pre-scale output bitmap.
    // 4. Apply final scaling.
    val matrix = Matrix().apply {
        // Step 1: Bring pivot to (0,0).
        postTranslate((-cx).toFloat(), (-cy).toFloat())
        // Step 2: Rotate around the origin.
        postRotate(angleDegrees.toFloat())
        // Step 3: Translate so that (cx,cy) becomes the center of the pre-scale output.
        postTranslate((outputWidth / 2).toFloat(), (outputHeight / 2).toFloat())
        // Step 4: Append the final scaling so that width becomes targetWidth.
        postScale(finalScale, finalScale)
    }

    // Create the final output bitmap.
    val output = createBitmap(finalOutputWidth, finalOutputHeight, input.config ?: Bitmap.Config.ARGB_8888)
    // Draw the input image with the composed transformation.
    val canvas = Canvas(output)
    canvas.drawBitmap(input, matrix, null)
    return output
}

/**
 * Factory function to create an Android-specific [FaceCapturePlugin].
 *
 * @param config Configuration settings for the plugin.
 * @return An instance of [PlatformFaceCapturePlugin].
 */
actual fun createPlatformFaceCapturePlugin(
    config: FaceCaptureConfig
): FaceCapturePlugin {
    return PlatformFaceCapturePlugin(config)
}