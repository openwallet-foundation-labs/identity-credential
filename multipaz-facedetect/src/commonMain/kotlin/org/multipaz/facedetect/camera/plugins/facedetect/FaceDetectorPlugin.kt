package org.multipaz.facedetect.camera.plugins.facedetect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.multipaz.compose.camera.CameraEngine
import org.multipaz.compose.camera.CameraPlugin
import org.multipaz.compose.camera.CameraWorkResult
import org.multipaz.util.Logger

/**
 * Common CameraPlugin implementation for the fce detection functionality.
 */
class FaceDetectorPlugin(private val coroutineScope: CoroutineScope) : CameraPlugin {
    private val TAG = "FaceDetectorPlugin"
    private var cameraEngine: CameraEngine? = null

    var isDetecting = atomic(false)
    val faceDetectionFlow = Channel<CameraWorkResult>()

    private var imageSize: Size? = null
    private var imageRotation: Int? = null

    /** Common API for the plugin initialization. */
    override fun initialize(cameraEngine: CameraEngine) {
        this.cameraEngine = cameraEngine
    }

    /** Placeholder. Detect face on the captured image bitmap. Not used yet. */
    fun detectFace(cameraImage: ImageBitmap) = coroutineScope.launch {
        Logger.d(TAG, "Starting face detection on the bitmap")
        val faceDetectedData = platformDetectFace(cameraImage)
        Logger.d(TAG, "Face Detection result: $faceDetectedData")
    }

    /**
     * Detection can be started on main thread and obtain any needed parameters, as well as map the flow.
     */
    fun startDetection() {
        isDetecting.value = true
        coroutineScope.launch {
            platformStartDetection(
                cameraEngine!!,
                onFaceDetected = {
                    if (isDetecting.value) {
                        if (isDetectionSatisfactory(it)) {
                            faceDetectionFlow.trySend(it)
                        } else {
                            faceDetectionFlow.trySend(
                                CameraWorkResult.Error(Exception("Face detection unsatisfactory")))
                        }
                    }
                },
                onImageSize = { imageSize = it },
                onImageRotation = { imageRotation = it },
                coroutineScope = coroutineScope
            )
        }
    }

    /**
     * Disable detection when needed and/or on lifecycle.
     */
    fun stopDetection() {
        isDetecting.value = false
    }

    private fun isDetectionSatisfactory(detectionResult: CameraWorkResult): Boolean {
        val data = (detectionResult as CameraWorkResult.FaceDetectionSuccess).faceData
        return if (data.leftEyePosition == null || data.rightEyePosition == null) {
            false
        } else if (
            data.leftEyePosition!!.x == 0f ||
            data.leftEyePosition!!.y == 0f ||
            data.rightEyePosition!!.x == 0f ||
            data.rightEyePosition!!.y == 0f
        ) {
            false
        } else {
            true
        }
    }
}

@Composable
fun rememberFaceDetectorPlugin(coroutineScope: CoroutineScope = rememberCoroutineScope()): FaceDetectorPlugin {
    return remember {
        FaceDetectorPlugin(coroutineScope)
    }
}

/**
 * Detect face on the decoded Bitmap (implemented for Android, but not used in the current implementation).
 */
expect suspend fun platformDetectFace(bitmap: ImageBitmap): CameraWorkResult

/**
 * Start face detection on the cameraEngine.
 *
 * @param cameraEngine CameraEngine instance.
 * @param onFaceDetected Primary data callback for the face detection result processing.
 * @param onImageSize Callback for the image size change to support UI composition.
 * @param onImageRotation Callback for the image rotation support in UI.
 * @param coroutineScope Coroutine scope for the detection.
 */
expect fun platformStartDetection(
    cameraEngine: CameraEngine,
    onFaceDetected: (faceData: CameraWorkResult) -> Unit,
    onImageSize: (Size) -> Unit,
    onImageRotation: (Int) -> Unit,
    coroutineScope: CoroutineScope
)