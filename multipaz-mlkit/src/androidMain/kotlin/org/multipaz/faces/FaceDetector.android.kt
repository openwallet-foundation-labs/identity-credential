package org.multipaz.faces

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
import org.multipaz.faces.MultipazFaceContour.Companion.FACE_C
import org.multipaz.faces.MultipazFaceContour.Companion.LEFT_CHEEK_C
import org.multipaz.faces.MultipazFaceContour.Companion.LEFT_EYEBROW_BOTTOM_C
import org.multipaz.faces.MultipazFaceContour.Companion.LEFT_EYEBROW_TOP_C
import org.multipaz.faces.MultipazFaceContour.Companion.LEFT_EYE_C
import org.multipaz.faces.MultipazFaceContour.Companion.LOWER_LIP_BOTTOM_C
import org.multipaz.faces.MultipazFaceContour.Companion.LOWER_LIP_TOP_C
import org.multipaz.faces.MultipazFaceContour.Companion.NOSE_BOTTOM_C
import org.multipaz.faces.MultipazFaceContour.Companion.NOSE_BRIDGE_C
import org.multipaz.faces.MultipazFaceContour.Companion.RIGHT_CHEEK_C
import org.multipaz.faces.MultipazFaceContour.Companion.RIGHT_EYEBROW_BOTTOM_C
import org.multipaz.faces.MultipazFaceContour.Companion.RIGHT_EYEBROW_TOP_C
import org.multipaz.faces.MultipazFaceContour.Companion.RIGHT_EYE_C
import org.multipaz.faces.MultipazFaceContour.Companion.UPPER_LIP_BOTTOM_C
import org.multipaz.faces.MultipazFaceContour.Companion.UPPER_LIP_TOP_C
import org.multipaz.faces.MultipazFaceLandmark.Companion.LEFT_CHEEK_LM
import org.multipaz.faces.MultipazFaceLandmark.Companion.LEFT_EAR_LM
import org.multipaz.faces.MultipazFaceLandmark.Companion.LEFT_EYE_LM
import org.multipaz.faces.MultipazFaceLandmark.Companion.MOUTH_BOTTOM_LM
import org.multipaz.faces.MultipazFaceLandmark.Companion.MOUTH_LEFT_LM
import org.multipaz.faces.MultipazFaceLandmark.Companion.MOUTH_RIGHT_LM
import org.multipaz.faces.MultipazFaceLandmark.Companion.NOSE_BASE_LM
import org.multipaz.faces.MultipazFaceLandmark.Companion.RIGHT_CHEEK_LM
import org.multipaz.faces.MultipazFaceLandmark.Companion.RIGHT_EAR_LM
import org.multipaz.faces.MultipazFaceLandmark.Companion.RIGHT_EYE_LM
import org.multipaz.util.Logger

private const val TAG = "FaceDetector"

private val faceDetectorOptions = FaceDetectorOptions.Builder()
    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
    .build()

actual fun detectFaces(frameData: CameraFrame): List<FaceObject>? {
    val bitmap = frameData.cameraImage.toImageBitmap()
    if (bitmap.width <= 0 || bitmap.height <= 0) {
        throw IllegalArgumentException("Invalid bitmap dimensions.")
    }
    val androidBitmap = bitmap.asAndroidBitmap()
    val rotation = (frameData.rotation + 270) % 360
    val image = InputImage.fromBitmap(androidBitmap, rotation)

    return detectFaces(image, frameData.rotation)
}

actual fun detectFaces(image: ImageBitmap): List<FaceObject>? {
    val inputImage = InputImage.fromBitmap(
        /* bitmap = */ image.asAndroidBitmap(),
        /* rotationDegrees = */ 0
    )
    return detectFaces(inputImage, 0)
}

fun detectFaces(inputImage: InputImage, rotation: Int): List<FaceObject>? =
    try {
        val detector = FaceDetection.getClient(faceDetectorOptions)
        val detectionTask = detector.process(inputImage)
        Tasks.await(detectionTask)
        detectionTask.result.map { it.toMultiplatformFace(rotation, Size(inputImage.width.toFloat(), inputImage.height.toFloat())) }
    } catch (e: Exception) {
        Logger.e(TAG, "Error during face detection", e)
        throw e
    }

fun android.graphics.Rect.toRect(rotation: Int, size: Size): androidx.compose.ui.geometry.Rect {
    val topLeft = PointF(left.toFloat(), top.toFloat()).toOffset(rotation, size)
    val bottomRight = PointF(right.toFloat(), bottom.toFloat()).toOffset(rotation, size)

    return androidx.compose.ui.geometry.Rect(topLeft, bottomRight)
}

fun PointF.toOffset(rotation: Int, size: Size): Offset {
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
val landmarkTypes = mapOf(
    FaceLandmark.NOSE_BASE to NOSE_BASE_LM,
    FaceLandmark.LEFT_EAR to LEFT_EAR_LM,
    FaceLandmark.MOUTH_BOTTOM to MOUTH_BOTTOM_LM,
    FaceLandmark.RIGHT_EYE to RIGHT_EYE_LM,
    FaceLandmark.LEFT_CHEEK to LEFT_CHEEK_LM,
    FaceLandmark.LEFT_EYE to LEFT_EYE_LM,
    FaceLandmark.MOUTH_LEFT to MOUTH_LEFT_LM,
    FaceLandmark.RIGHT_EAR to RIGHT_EAR_LM,
    FaceLandmark.MOUTH_RIGHT to MOUTH_RIGHT_LM,
    FaceLandmark.RIGHT_CHEEK to RIGHT_CHEEK_LM
)


private val contourTypes = mapOf(
    FaceContour.LEFT_EYEBROW_BOTTOM to LEFT_EYEBROW_BOTTOM_C,
    FaceContour.UPPER_LIP_TOP to UPPER_LIP_TOP_C,
    FaceContour.LOWER_LIP_TOP to LOWER_LIP_TOP_C,
    FaceContour.LEFT_CHEEK to LEFT_CHEEK_C,
    FaceContour.FACE to FACE_C,
    FaceContour.RIGHT_EYEBROW_BOTTOM to RIGHT_EYEBROW_BOTTOM_C,
    FaceContour.LEFT_EYE to LEFT_EYE_C,
    FaceContour.LOWER_LIP_BOTTOM to LOWER_LIP_BOTTOM_C,
    FaceContour.RIGHT_CHEEK to RIGHT_CHEEK_C,
    FaceContour.NOSE_BRIDGE to NOSE_BRIDGE_C,
    FaceContour.LEFT_EYEBROW_TOP to LEFT_EYEBROW_TOP_C,
    FaceContour.RIGHT_EYE to RIGHT_EYE_C,
    FaceContour.RIGHT_EYEBROW_TOP to RIGHT_EYEBROW_TOP_C,
    FaceContour.NOSE_BOTTOM to NOSE_BOTTOM_C,
    FaceContour.UPPER_LIP_BOTTOM to UPPER_LIP_BOTTOM_C
)

fun List<com.google.mlkit.vision.face.FaceLandmark>.toMFaceLandmarkList(rotation: Int, size: Size)
: List<MultipazFaceLandmark> {
    return this.map { faceLandmark ->
        MultipazFaceLandmark(
            contourTypes[faceLandmark.landmarkType] ?: MultipazFaceLandmark.Companion.UNKNOWN_LM,
            faceLandmark.position.toOffset(rotation, size)
        )
    }
}

fun List<com.google.mlkit.vision.face.FaceContour>.toMFaceContourList(rotation: Int, size: Size)
: List<MultipazFaceContour> {
    return this.map { faceContour ->
        MultipazFaceContour(
            contourTypes[faceContour.faceContourType] ?: MultipazFaceContour.Companion.UNKNOWN_C,
            faceContour.points.map { it.toOffset(rotation, size) }
        )
    }
}

fun Face.toMultiplatformFace(rotation: Int, size: Size): FaceObject {
    return with(this) {
        FaceObject(
            boundingBox = boundingBox.toRect(rotation, size),
            trackingId = trackingId,
            rightEyeOpenProbability = rightEyeOpenProbability,
            leftEyeOpenProbability = leftEyeOpenProbability,
            smilingProbability = smilingProbability,
            headEulerAngleX = headEulerAngleX,
            headEulerAngleY = headEulerAngleY,
            headEulerAngleZ = headEulerAngleZ,
            landmarks = allLandmarks.toMFaceLandmarkList(rotation, size), // Use the original SparseArray here
            contours = allContours.toMFaceContourList(rotation, size)   // Use the original SparseArray here
        )
    }
}

