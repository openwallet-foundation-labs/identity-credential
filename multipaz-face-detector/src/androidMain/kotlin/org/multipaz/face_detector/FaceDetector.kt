package org.multipaz.face_detector

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.multipaz.util.Logger
import kotlin.coroutines.resumeWithException

private const val TAG = "FaceDetector"

private val faceDetectorOptions = FaceDetectorOptions.Builder()
    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
    .build()

@OptIn(ExperimentalCoroutinesApi::class)
actual suspend fun detectFace(bitmap: ImageBitmap): ImageBitmap =
    withContext(Dispatchers.IO) {
        try {
            suspendCancellableCoroutine { continuation ->
                Logger.d(TAG, "Face detection on ib: ${bitmap.width}x${bitmap.height}")

                val detector = FaceDetection.getClient(faceDetectorOptions)

                if (bitmap.width <= 0 || bitmap.height <= 0) {
                    continuation.resumeWithException(IllegalArgumentException("Invalid bitmap dimensions."))
                    return@suspendCancellableCoroutine
                }

                val androidBitmap = bitmap.asAndroidBitmap()
                val image = InputImage.fromBitmap(androidBitmap, 270)

                detector.process(image)
                    .addOnSuccessListener { faces ->
                        val result = processFaces(bitmap, faces)
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

private val facePaint = Paint().apply {
    color = Color.RED
    style = Paint.Style.STROKE
    strokeWidth = 5f
}
private val canvasPaint = Paint().apply {
    color = Color.BLUE
    style = Paint.Style.STROKE
    strokeWidth = 5f
}

fun processFaces(bitmap: ImageBitmap, faces: List<Face>): ImageBitmap {
    val originalBitmap = bitmap.asAndroidBitmap()
    originalBitmap.density = Bitmap.DENSITY_NONE

    val w = originalBitmap.width
    val h = originalBitmap.height
    val outputBitmap = createBitmap(h, w, Bitmap.Config. ARGB_8888) // Rotated 90
    outputBitmap.density = Bitmap.DENSITY_NONE

    val canvas = Canvas(outputBitmap)
    canvas.drawRect(Rect(0,0,h,w), canvasPaint)
    canvas.density = Bitmap.DENSITY_NONE

    val matrix = Matrix()
    matrix.postRotate(270f) // For Portrait only.

    val rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, w, h, matrix, true)
    canvas.drawBitmap(rotatedBitmap, 0f,0f, canvasPaint)

    faces.forEach { face ->
        val box = face.boundingBox
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val mouth = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position

        canvas.drawRect(box, facePaint)
        val path = Path()
        if (leftEye != null && rightEye != null && mouth != null) {
            path.moveTo(leftEye.x, leftEye.y)
            path.lineTo(rightEye.x, rightEye.y)
            path.lineTo(mouth.x, mouth.y)
            path.close()
            canvas.drawPath(path, facePaint)
            Logger.d(TAG, "fd: ${box.right}, ${box.bottom}")
        }

        // Just draw the first face for now.
        return@forEach
    }
    return outputBitmap.asImageBitmap() // No faces? Still draw the transformed original bitmap.
}

private fun PointF?.toOffset(): Offset {
    with(this) {
        return if (this != null) {
            Offset(x, y)
        } else {
            Offset.Zero
        }
    }
}
