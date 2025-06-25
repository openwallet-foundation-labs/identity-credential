package org.multipaz.facedetection

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import cocoapods.GoogleMLKit.MLKFace
import cocoapods.GoogleMLKit.MLKFaceContour
import cocoapods.GoogleMLKit.MLKFaceDetector
import cocoapods.GoogleMLKit.MLKFaceDetectorClassificationModeAll
import cocoapods.GoogleMLKit.MLKFaceDetectorContourModeAll
import cocoapods.GoogleMLKit.MLKFaceDetectorLandmarkModeAll
import cocoapods.GoogleMLKit.MLKFaceDetectorOptions
import cocoapods.GoogleMLKit.MLKFaceDetectorPerformanceModeAccurate
import cocoapods.GoogleMLKit.MLKFaceDetectorPerformanceModeFast
import cocoapods.GoogleMLKit.MLKFaceLandmark
import cocoapods.MLKitVision.MLKVisionImage
import cocoapods.MLKitVision.MLKVisionPoint
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.cinterop.useContents
import org.multipaz.compose.camera.CameraFrame
import org.multipaz.compose.toUIImage
import org.multipaz.util.Logger
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRect
import platform.UIKit.UIImage
import platform.UIKit.UIImageOrientation

private const val TAG = "FaceDetector"

@OptIn(ExperimentalForeignApi::class)
actual fun detectFaces(frameData: CameraFrame): List<DetectedFace>? {
    if (frameData.width <= 0 || frameData.height <= 0) {
        throw IllegalArgumentException("Invalid bitmap dimensions.")
    }

    return detectFaces(
        MLKVisionImage(frameData.cameraImage.uiImage).apply {
            this.setOrientation(calculateMLKitImageOrientation(frameData))}
	)
}

@OptIn(ExperimentalForeignApi::class)
actual fun detectFaces(image: ImageBitmap): List<DetectedFace>? {
    return detectFaces(MLKVisionImage(image.toUIImage()!!))
}

@OptIn(ExperimentalForeignApi::class)
private val faceDetectorOptions = MLKFaceDetectorOptions().apply {
    classificationMode = MLKFaceDetectorClassificationModeAll
    performanceMode = MLKFaceDetectorPerformanceModeAccurate
    landmarkMode = MLKFaceDetectorLandmarkModeAll
    contourMode = MLKFaceDetectorContourModeAll
    minFaceSize = 0.20
}

@OptIn(ExperimentalForeignApi::class)
private fun detectFaces(mlkVisionImage: MLKVisionImage): List<DetectedFace>? =
    try {
        val detector = MLKFaceDetector.faceDetectorWithOptions(faceDetectorOptions)
        val faces = detector.resultsInImage(mlkVisionImage as objcnames.protocols.MLKCompatibleImageProtocol, null)
        (faces as List<MLKFace>).map { it.toMultiplatformFace() }
    } catch (e: Exception) {
        Logger.e(TAG, "Error during face detection", e)
        throw e
    }

@OptIn(ExperimentalForeignApi::class)
private fun CValue<CGRect>.toRect(): androidx.compose.ui.geometry.Rect {
    return useContents {
        Rect(
            left = origin.x.toFloat(),
            top = origin.y.toFloat(),
            right = (origin.x + size.width).toFloat(),
            bottom = (origin.y + size.height).toFloat()
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun MLKVisionPoint.toOffset(): Offset {
    return Offset(x.toFloat(), y.toFloat())
}

/** iOS types are text string names. Android types are Integers. Converge to the common ground Integer types. */
private val landmarkTypes = mapOf(
    "NoseBase" to FaceLandmarkType.NOSE_BASE,
    "LeftEar" to FaceLandmarkType.LEFT_EAR,
    "MouthBottom" to FaceLandmarkType.MOUTH_BOTTOM,
    "RightEye" to FaceLandmarkType.RIGHT_EYE,
    "LeftCheek" to FaceLandmarkType.LEFT_CHEEK,
    "LeftEye" to FaceLandmarkType.LEFT_EYE,
    "MouthLeft" to FaceLandmarkType.MOUTH_LEFT,
    "RightEar" to FaceLandmarkType.RIGHT_EAR,
    "MouthRight" to FaceLandmarkType.MOUTH_RIGHT,
    "RightCheek" to FaceLandmarkType.RIGHT_CHEEK)

/** iOS types are text string names. Android types are Integers. Converge to the common ground Integer types. */
private val contourTypes = mapOf(
    "LeftEyebrowBottom" to FaceContourType.LEFT_EYEBROW_BOTTOM,
    "UpperLipTop" to FaceContourType.UPPER_LIP_TOP,
    "LowerLipTop" to FaceContourType.LOWER_LIP_TOP,
    "LeftCheek" to FaceContourType.LEFT_CHEEK,
    "Face" to FaceContourType.FACE,
    "RightEyebrowBottom" to FaceContourType.RIGHT_EYEBROW_BOTTOM,
    "LeftEye" to FaceContourType.LEFT_EYE,
    "LowerLipBottom" to FaceContourType.LOWER_LIP_BOTTOM,
    "RightCheek" to FaceContourType.RIGHT_CHEEK,
    "NoseBridge" to FaceContourType.NOSE_BRIDGE,
    "LeftEyebrowTop" to FaceContourType.LEFT_EYEBROW_TOP,
    "RightEye" to FaceContourType.RIGHT_EYE,
    "RightEyebrowTop" to FaceContourType.RIGHT_EYEBROW_TOP,
    "NoseBottom" to FaceContourType.NOSE_BOTTOM,
    "UpperLipBottom" to FaceContourType.UPPER_LIP_BOTTOM)

@OptIn(ExperimentalForeignApi::class)
private fun List<MLKFaceLandmark>.toMFaceLandmarkList(): List<FaceLandmark> {
    return this.map { faceLandmark ->
        val typeString = faceLandmark.type.toString()
        val type = landmarkTypes[typeString] ?: FaceLandmarkType.UNKNOWN
        FaceLandmark(type, (faceLandmark.position as MLKVisionPoint).toOffset())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun List<MLKFaceContour>.toMFaceContourList(): List<FaceContour> {
    return this.map { faceContour ->
        val typeString = faceContour.type.toString()
        val points = faceContour.points.map { (it as MLKVisionPoint).toOffset() }
        val type = contourTypes[typeString] ?: FaceContourType.UNKNOWN
        FaceContour(type, points)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun MLKFace.toMultiplatformFace(): DetectedFace {
    return with(this) {
        DetectedFace(
            boundingBox = frame.toRect(),
            trackingId = this.trackingID.toInt(),
            rightEyeOpenProbability = rightEyeOpenProbability.toFloat(),
            leftEyeOpenProbability = leftEyeOpenProbability.toFloat(),
            smilingProbability = smilingProbability.toFloat(),
            headEulerAngleX = headEulerAngleX.toFloat(),
            headEulerAngleY = headEulerAngleY.toFloat(),
            headEulerAngleZ = headEulerAngleZ.toFloat(),
            landmarks = (landmarks as List<MLKFaceLandmark>).toMFaceLandmarkList(),
            contours = (contours as List<MLKFaceContour>).toMFaceContourList()
        )
    }
}

private fun calculateMLKitImageOrientation(frameData: CameraFrame): UIImageOrientation {
    val isMirrored = isMirrored(frameData.previewTransformation)
    return when (frameData.rotation) {
        270 -> if (isMirrored) UIImageOrientation.UIImageOrientationUp else UIImageOrientation.UIImageOrientationDown
        0 -> UIImageOrientation.UIImageOrientationRight
        90 -> if (isMirrored) UIImageOrientation.UIImageOrientationDown else UIImageOrientation.UIImageOrientationUp
        180 -> UIImageOrientation.UIImageOrientationLeft
        else -> UIImageOrientation.UIImageOrientationUp
    }
}

/** Figure if the Matrix is a mirror transformation by applying it to 2 orthogonal vectors. */
private fun isMirrored(matrix: Matrix): Boolean {
    val v1 = Offset(1f, 0f) // X-axis
    val v2 = Offset(0f, 1f) // Y-axis
    val transformedV1 = matrix.map(v1)
    val transformedV2 = matrix.map(v2)
    val determinant = transformedV1.x * transformedV2.y - transformedV1.y * transformedV2.x

    return determinant > 0
}
