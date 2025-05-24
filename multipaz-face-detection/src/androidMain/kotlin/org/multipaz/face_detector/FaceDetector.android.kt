package org.multipaz.face_detector

import android.graphics.PointF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.multipaz.compose.camera.CameraFrame
import org.multipaz.util.Logger

private const val TAG = "FaceDetector"

private val faceDetectorOptions = FaceDetectorOptions.Builder()
    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
    .build()

actual fun detectFaces(frameData: CameraFrame): FaceData? {
    val bitmap = frameData.cameraImage.toImageBitmap()
    if (bitmap.width <= 0 || bitmap.height <= 0) {
        throw IllegalArgumentException("Invalid bitmap dimensions.")
    }
    val androidBitmap = bitmap.asAndroidBitmap()
    val rotation = (frameData.rotation + 270) % 360
    Logger.d(TAG, "rotation: $rotation")
    val image = InputImage.fromBitmap(androidBitmap, rotation)

    return detectFaces(image, frameData.rotation)
}

actual fun detectFaces(image: ImageBitmap): FaceData? {
    val inputImage = InputImage.fromBitmap(
        /* bitmap = */ image.asAndroidBitmap(),
        /* rotationDegrees = */ 0
    )
    return detectFaces(inputImage, 0)
}

fun detectFaces(inputImage: InputImage, rotation: Int): FaceData? =
    try {
        val detector = FaceDetection.getClient(faceDetectorOptions)
        val detectionTask = detector.process(inputImage)
        Tasks.await(detectionTask)
        detectionTask.result.toFaceData(inputImage.width, inputImage.height, rotation)
    } catch (e: Exception) {
        Logger.e(TAG, "Error during face detection", e)
        throw e
    }

private fun MutableList<Face>.toFaceData(width: Int, height: Int, rotation: Int): FaceData? {
    if (isEmpty()) return null

    val faces = map { it.toMultiplatformFace() }
    return FaceData(faces, width, height, rotation)
}

fun android.graphics.Rect.toRect(): androidx.compose.ui.geometry.Rect {
    return androidx.compose.ui.geometry.Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
}

fun PointF.toOffset(): Offset {
    return Offset(x, y)
}

fun List<com.google.mlkit.vision.face.FaceLandmark>.toMFaceLandmarkList(): List<FaceLandmark> {
    return this.map { faceLandmark ->
        FaceLandmark(faceLandmark.landmarkType, faceLandmark.position.toOffset())
    }
}

fun List<com.google.mlkit.vision.face.FaceContour>.toMFaceContourList(): List<FaceContour> {
    return this.map { faceContour ->
        val points = faceContour.points.map { it.toOffset() }
        FaceContour(faceContour.faceContourType, points)
    }
}

fun Face.toMultiplatformFace(): FaceObject {
    return with(this) {
        FaceObject(
            boundingBox = boundingBox.toRect(),
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

