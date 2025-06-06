package org.multipaz.facedetection

import android.graphics.PointF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import org.multipaz.compose.camera.CameraFrame
import org.multipaz.util.Logger

private const val TAG = "FaceDetector"

actual fun detectFaces(frameData: CameraFrame): List<DetectedFace>? {
    val bitmap = frameData.cameraImage.toImageBitmap()
    if (bitmap.width <= 0 || bitmap.height <= 0) {
        throw IllegalArgumentException("Invalid bitmap dimensions.")
    }
    val androidBitmap = bitmap.asAndroidBitmap()
    val rotation = (frameData.rotation + 270) % 360
    val image = InputImage.fromBitmap(androidBitmap, rotation)

    return detectFaces(image, frameData.rotation)
}

actual fun detectFaces(image: ImageBitmap): List<DetectedFace>? {
    val inputImage = InputImage.fromBitmap(
        /* bitmap = */ image.asAndroidBitmap(),
        /* rotationDegrees = */ 0
    )
    return detectFaces(inputImage, 0)
}

private val faceDetectorOptions = FaceDetectorOptions.Builder()
    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
    .setMinFaceSize(0.20f)
    .build()

private fun detectFaces(inputImage: InputImage, rotation: Int): List<DetectedFace>? =
    try {
        val detector = FaceDetection.getClient(faceDetectorOptions)
        val detectionTask = detector.process(inputImage)
        Tasks.await(detectionTask)
        detectionTask.result.map { it.toMultiplatformFace(rotation, Size(inputImage.width.toFloat(), inputImage.height.toFloat())) }
    } catch (e: Exception) {
        Logger.e(TAG, "Error during face detection", e)
        throw e
    }

private fun android.graphics.Rect.toRect(rotation: Int, size: Size): androidx.compose.ui.geometry.Rect {
    val topLeft = PointF(left.toFloat(), top.toFloat()).toOffset(rotation, size)
    val bottomRight = PointF(right.toFloat(), bottom.toFloat()).toOffset(rotation, size)

    return androidx.compose.ui.geometry.Rect(topLeft, bottomRight)
}

private fun PointF.toOffset(rotation: Int, size: Size): Offset {
    val x0 = x
    val y0 = y
    when (rotation) {
        0 -> {
            x = size.width - y0
            y = x0
        }

        180 -> {
            x = y0
            y = size.height - x0
        }

        270 -> {
            x = size.width - x0
            y = size.height - y0
        }

        else -> { /* 90 and unknowns - no conversion. */ }
    }
    return Offset(x, y)
}

/** iOS types are text string names. Android types are Integers. Converge to the common ground Integer types. */
private val landmarkTypes = mapOf(
    FaceLandmark.NOSE_BASE to FaceLandmarkType.NOSE_BASE,
    FaceLandmark.LEFT_EAR to FaceLandmarkType.LEFT_EAR,
    FaceLandmark.MOUTH_BOTTOM to FaceLandmarkType.MOUTH_BOTTOM,
    FaceLandmark.RIGHT_EYE to FaceLandmarkType.RIGHT_EYE,
    FaceLandmark.LEFT_CHEEK to FaceLandmarkType.LEFT_CHEEK,
    FaceLandmark.LEFT_EYE to FaceLandmarkType.LEFT_EYE,
    FaceLandmark.MOUTH_LEFT to FaceLandmarkType.MOUTH_LEFT,
    FaceLandmark.RIGHT_EAR to FaceLandmarkType.RIGHT_EAR,
    FaceLandmark.MOUTH_RIGHT to FaceLandmarkType.MOUTH_RIGHT,
    FaceLandmark.RIGHT_CHEEK to FaceLandmarkType.RIGHT_CHEEK
)

/** iOS types are text string names. Android types are Integers. Converge to the common ground Integer types. */
private val contourTypes = mapOf(
    FaceContour.LEFT_EYEBROW_BOTTOM to FaceContourType.LEFT_EYEBROW_BOTTOM,
    FaceContour.UPPER_LIP_TOP to FaceContourType.UPPER_LIP_TOP,
    FaceContour.LOWER_LIP_TOP to FaceContourType.LOWER_LIP_TOP,
    FaceContour.LEFT_CHEEK to FaceContourType.LEFT_CHEEK,
    FaceContour.FACE to FaceContourType.FACE,
    FaceContour.RIGHT_EYEBROW_BOTTOM to FaceContourType.RIGHT_EYEBROW_BOTTOM,
    FaceContour.LEFT_EYE to FaceContourType.LEFT_EYE,
    FaceContour.LOWER_LIP_BOTTOM to FaceContourType.LOWER_LIP_BOTTOM,
    FaceContour.RIGHT_CHEEK to FaceContourType.RIGHT_CHEEK,
    FaceContour.NOSE_BRIDGE to FaceContourType.NOSE_BRIDGE,
    FaceContour.LEFT_EYEBROW_TOP to FaceContourType.LEFT_EYEBROW_TOP,
    FaceContour.RIGHT_EYE to FaceContourType.RIGHT_EYE,
    FaceContour.RIGHT_EYEBROW_TOP to FaceContourType.RIGHT_EYEBROW_TOP,
    FaceContour.NOSE_BOTTOM to FaceContourType.NOSE_BOTTOM,
    FaceContour.UPPER_LIP_BOTTOM to FaceContourType.UPPER_LIP_BOTTOM
)

fun List<com.google.mlkit.vision.face.FaceLandmark>.toMFaceLandmarkList(rotation: Int, size: Size)
: List<org.multipaz.facedetection.FaceLandmark> {
    return this.map { faceLandmark ->
        FaceLandmark(
            landmarkTypes[faceLandmark.landmarkType] ?: FaceLandmarkType.UNKNOWN,
            faceLandmark.position.toOffset(rotation, size)
        )
    }
}

fun List<com.google.mlkit.vision.face.FaceContour>.toMFaceContourList(rotation: Int, size: Size)
: List<org.multipaz.facedetection.FaceContour> {
    return this.map { faceContour ->
        FaceContour(
            contourTypes[faceContour.faceContourType] ?: FaceContourType.UNKNOWN,
            faceContour.points.map { it.toOffset(rotation, size) }
        )
    }
}

fun Face.toMultiplatformFace(rotation: Int, size: Size): DetectedFace {
    return with(this) {
        DetectedFace(
            boundingBox = boundingBox.toRect(rotation, size),
            trackingId = trackingId ?: -1,
            rightEyeOpenProbability = rightEyeOpenProbability ?: 1f,
            leftEyeOpenProbability = leftEyeOpenProbability ?: 1f,
            smilingProbability = smilingProbability ?: 0f,
            headEulerAngleX = headEulerAngleX,
            headEulerAngleY = headEulerAngleY,
            headEulerAngleZ = headEulerAngleZ,
            landmarks = allLandmarks.toMFaceLandmarkList(rotation, size), // Use the original SparseArray here
            contours = allContours.toMFaceContourList(rotation, size)   // Use the original SparseArray here
        )
    }
}

