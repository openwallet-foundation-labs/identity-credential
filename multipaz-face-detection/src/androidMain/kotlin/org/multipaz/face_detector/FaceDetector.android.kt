package org.multipaz.face_detector

import android.graphics.PointF
import android.graphics.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.multipaz.compose.camera.CameraFrame
import org.multipaz.util.Logger
import kotlin.time.TimeSource

private const val TAG = "FaceDetector"
private val t = TimeSource.Monotonic
private val faceDetectorOptions = FaceDetectorOptions.Builder()
    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
    .build()

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class FaceData actual constructor(actual val faces: List<FaceObject>)

actual fun detectFaces(frameData: CameraFrame): FaceData? =
    try {
        val bitmap = frameData.toImageBitmap()
        val t1 = t.markNow()
        Logger.d(TAG, "Face detect in imgBmp: ${bitmap.width}x${bitmap.height}")
        val detector = FaceDetection.getClient(faceDetectorOptions)
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            throw IllegalArgumentException("Invalid bitmap dimensions.")
        }
        val t2 = t.markNow()
        val androidBitmap = bitmap.asAndroidBitmap()
        val image = InputImage.fromBitmap(androidBitmap, frameData.rotation) //TODO: use actual rotation for landscape later.
        val t3 = t.markNow()
        val detectionTask = detector.process(image)
        Tasks.await(detectionTask)
        val t4 = t.markNow()
        val result = detectionTask.result.toFaceData()
        val t5 = t.markNow()
        Logger.d(TAG, "timers: ${t2 - t1}, ${t3-t2}, ${t4-t3}, ${t5-t4}" )
        result

    } catch (e: Exception) {
        Logger.e(TAG, "Error during face detection", e)
        throw e
    }

private fun MutableList<Face>.toFaceData(): FaceData? {
    if (isEmpty()) return null
    val faces = map { it.toMultiplatformFace() }
    return FaceData(faces)
}

// Helper function to convert Android Rect to MRect
fun Rect.toMRect(): MRect {
    return MRect(left, top, right, bottom)
}

// Helper function to convert Android PointF to MPointF
fun PointF.toMPointF(): MPointF {
    return MPointF(x, y)
}

fun List<com.google.mlkit.vision.face.FaceLandmark>.toMFaceLandmarkList(): List<MFaceLandmark> {
    return this.map { faceLandmark ->
        MFaceLandmark(faceLandmark.landmarkType, faceLandmark.position.toMPointF())
    }
}

fun List<com.google.mlkit.vision.face.FaceContour>.toMFaceContourList(): List<MFaceContour> {
    return this.map { faceContour ->
        val points = faceContour.points.map { it.toMPointF() }
        MFaceContour(faceContour.faceContourType, points)
    }
}

fun Face.toMultiplatformFace(): FaceObject {
    return with(this) {
        FaceObject(
            boundingBox = boundingBox.toMRect(),
            trackingId = trackingId,
            rightEyeOpenProbability = rightEyeOpenProbability,
            leftEyeOpenProbability = leftEyeOpenProbability,
            smilingProbability = smilingProbability,
            headEulerAngleX = headEulerAngleX,
            headEulerAngleY = headEulerAngleY,
            headEulerAngleZ = headEulerAngleZ,
            landmarks = allLandmarks.toMFaceLandmarkList(), // Use the original SparseArray here
            contours = allContours.toMFaceContourList()   // Use the original SparseArray here
        )
    }
}