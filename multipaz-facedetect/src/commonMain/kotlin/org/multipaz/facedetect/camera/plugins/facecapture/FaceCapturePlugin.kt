package org.multipaz.facedetect.camera.plugins.facecapture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.Flow
import org.multipaz.compose.camera.CameraEngine
import org.multipaz.compose.camera.CameraPlugin
import org.multipaz.compose.camera.CameraWorkResult

/**
 * Configuration for the FaceCapturePlugin.
 *
 * @param isFrontCamera true if the capture is done with the front camera (mirrored capture), false otherwise.
 * @param storageName Image ID in the storage.
 * @param finalSizeWidth Final width of the captured image. The height is defined by the detected face rectangle
 *     aspect ratio (variable). This is the final face size as stored for the future face recognition.
 */
data class FaceCaptureConfig(
    val isFrontCamera: Boolean = false,
    val storageName: String? = "recognized_face",
    val finalSizeWidth: Int = 128
)

/**
 * Common abstract CameraPlugin implementation for capturing and processing formatted face images.
 *
 * Provides methods to save images either manually or automatically based on configuration.
 *
 * @param config Configuration settings for the plugin.
 */
abstract class FaceCapturePlugin(
    val config: FaceCaptureConfig
) : CameraPlugin {
    val TAG = "FaceCapturePlugin"
    var cameraEngine: CameraEngine? = null

    /**
     * Save the captured image data to storage manually.
     *
     * @param detectedFace The information about the detected face required to consistently extract the face image.
     * @param imageName Optional custom name for the image. If not provided, a default name is generated.
     */
    abstract suspend fun captureFace(
        detectedFace: CameraWorkResult,
        imageName: String = "enrolled_face"
    ): Flow<ImageBitmap>

    /** Common plugins interface to the Camera Engine. */
    override fun initialize(cameraEngine: CameraEngine) {
        this.cameraEngine = cameraEngine
    }
}

/**
 * Factory function to create a platform-specific [FaceCapturePlugin].
 *
 * @param config Configuration settings for the plugin.
 * @return An instance of [FaceCapturePlugin].
 */
@Composable
fun rememberFaceCapturePlugin(
    config: FaceCaptureConfig
): FaceCapturePlugin {
    return remember(config) {
        createPlatformFaceCapturePlugin(config)
    }
}

/**
 * A platform-agnostic image type.
 * On Android, it will be aliased to [android.graphics.Bitmap].
 * On iOS, it will be aliased to [UIImage].
 */
expect class PlatformImage

/**
 * Crop and rotate the input image to match face features, so that the point (cx,cy) in the input becomes
 * the center of the cropped output image, then applies an extra uniform scale so the final image (targetWidth).
 *
 * The transformation is done around (cx,cy) and by the given rotation angle (in degrees,
 * counterclockwise). Finally, the resulting image is uniformly scaled.
 *
 * @param input The input image.
 * @param cx The x-coordinate (in input image space) to center.
 * @param cy The y-coordinate (in input image space) to center.
 * @param angleDegrees The rotation angle (in degrees, counterclockwise).
 * @param outputWidth The pre-scale output width (in pixels).
 * @param outputHeight The pre-scale output height (in pixels).
 * @param targetWidth The final output width (in pixels); the height is scaled proportionally.
 * @return The resulting transformed image.
 */
expect fun cropRotateScale(
    input: PlatformImage,
    cx: Double,
    cy: Double,
    angleDegrees: Double,
    outputWidth: Int,
    outputHeight: Int,
    targetWidth: Int
): PlatformImage

/**
 * Platform-specific implementation of the [rememberFaceCapturePlugin] factory function.
 */
expect fun createPlatformFaceCapturePlugin(
    config: FaceCaptureConfig
): FaceCapturePlugin
