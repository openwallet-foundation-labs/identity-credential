package org.multipaz.facedetect.camera.plugins.facedetect

import android.graphics.PointF
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.multipaz.compose.camera.CameraEngine
import org.multipaz.compose.camera.CameraWorkResult
import org.multipaz.util.Logger
import kotlin.coroutines.resumeWithException

private const val TAG = "FaceDetectorPlugin"

private val faceDetectorOptions = FaceDetectorOptions.Builder()
    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
    .build()

@OptIn(ExperimentalCoroutinesApi::class)
actual suspend fun platformDetectFace(bitmap: ImageBitmap): CameraWorkResult =
    withContext(Dispatchers.Default) {
        try {
            suspendCancellableCoroutine { continuation ->
                Logger.d(TAG, "Face detection on the bimap starting.")

                val detector = FaceDetection.getClient(faceDetectorOptions)

                if (bitmap.width <= 0 || bitmap.height <= 0) {
                    continuation.resumeWithException(IllegalArgumentException("Invalid bitmap dimensions."))
                    return@suspendCancellableCoroutine
                }

                val androidBitmap = bitmap.asAndroidBitmap()
                val image = InputImage.fromBitmap(androidBitmap, 0)

                detector.process(image)
                    .addOnSuccessListener { faces ->
                        val result = processFaces(faces)
                        continuation.resume(result) { _ ->
                            detector.close()
                        }
                    }
                    .addOnFailureListener { e ->
                        Logger.e(TAG, "Face detection failed", e)
                        continuation.resumeWithException(e)
                    }

                continuation.invokeOnCancellation {
                    detector.close()
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error during face detection", e)
            throw e
        }
    }

fun processFaces(faces: List<Face>): CameraWorkResult {
    // TODO("Not yet implemented")
    Logger.d("processFaces", "Not implemented")
    return CameraWorkResult.Error(Exception("process Faces not implemented"))
}


fun CameraEngine.enableFaceDetection(
    onFaceDetected: (faceData: CameraWorkResult) -> Unit,
    onImageSize: (Size) -> Unit,
    onImageRotation: (Int) -> Unit,
    coroutineScope: CoroutineScope,
    onError: (CameraWorkResult.Error) -> Unit = { Logger.e(TAG, "Face detection error", it.exception) }
) {
    Logger.d(TAG, "Configuring face detection analyzer.")

    val analyzer = FaceDetectionCameraAnalyzer(
        onFaceDetected = onFaceDetected,
        onImageSize = onImageSize,
        onImageRotation = onImageRotation,
        onError = onError
    )

    imageAnalyzer = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .apply {
            setAnalyzer(
                ContextCompat.getMainExecutor(context),
                analyzer
            )
        }

    updateImageAnalyzer()
}

class FaceDetectionCameraAnalyzer(
    private val onFaceDetected: (CameraWorkResult.FaceDetectionSuccess) -> Unit,
    private val onImageSize: (Size) -> Unit,
    private val onImageRotation: (Int) -> Unit,
    private val onError: (CameraWorkResult.Error) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(faceDetectorOptions)
    private var imageSize: Size? = null
    private var imageRotation: Int? = null

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (imageSize == null) {
            imageSize = Size(imageProxy.width.toFloat(), imageProxy.height.toFloat())
            onImageSize(imageSize!!)
        }
        if (imageRotation == null) {
            imageRotation = imageProxy.imageInfo.rotationDegrees
            onImageRotation(imageRotation!!)
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        try {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )
            detector.process(image)
                .addOnSuccessListener { faces ->
                    processFaces(
                        faces,
                        imageProxy,
                        Size(imageProxy.width.toFloat(), imageProxy.height.toFloat()),
                        imageProxy.imageInfo.rotationDegrees
                    )
                }
                .addOnFailureListener { exception ->
                    onError(CameraWorkResult.Error(exception))
                    imageProxy.close()
                }
        } catch (exception: Exception) {
            onError(CameraWorkResult.Error(exception))
            imageProxy.close()
        }
    }

    private fun processFaces(faces: List<Face>, imageProxy: ImageProxy, imageSize: Size, imageRotation: Int) {
        if (faces.isNotEmpty()) {
            if (faces.size == 1) {
                val box = faces[0].boundingBox
                val leftEyePosition = faces[0].getLandmark(FaceLandmark.LEFT_EYE)?.position?.toOffset()
                val rightEyePosition = faces[0].getLandmark(FaceLandmark.RIGHT_EYE)?.position?.toOffset()
                val mouthPosition = faces[0].getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position?.toOffset()

                onFaceDetected(
                    CameraWorkResult.FaceDetectionSuccess(
                        CameraWorkResult.FaceData(
                            Rect(
                                box.left.toFloat(),
                                box.top.toFloat(),
                                box.right.toFloat(),
                                box.bottom.toFloat()
                            ),
                            leftEyePosition,
                            rightEyePosition,
                            mouthPosition
                        ),
                        imageSize,
                        imageRotation
                    )
                )
            }
        }
        imageProxy.close()
    }

    private fun PointF.toOffset() = Offset(x, y)
}

actual fun platformStartDetection(
    cameraEngine: CameraEngine,
    onFaceDetected: (faceData: CameraWorkResult) -> Unit,
    onImageSize: (Size) -> Unit,
    onImageRotation: (Int) -> Unit,
    coroutineScope: CoroutineScope
) {
    cameraEngine.enableFaceDetection(onFaceDetected, onImageSize, onImageRotation, coroutineScope)
}