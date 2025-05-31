package org.multipaz.faces

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import cocoapods.GoogleMLKit.MLKFace
import cocoapods.GoogleMLKit.MLKFaceContour
import cocoapods.GoogleMLKit.MLKFaceDetector
import cocoapods.GoogleMLKit.MLKFaceDetectorContourModeAll
import cocoapods.GoogleMLKit.MLKFaceDetectorLandmarkModeAll
import cocoapods.GoogleMLKit.MLKFaceDetectorOptions
import cocoapods.GoogleMLKit.MLKFaceDetectorPerformanceModeFast
import cocoapods.GoogleMLKit.MLKFaceLandmark
import cocoapods.MLKitVision.MLKVisionImage
import cocoapods.MLKitVision.MLKVisionPoint
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.cinterop.useContents
import org.multipaz.compose.camera.CameraFrame
import org.multipaz.faces.MultipazFaceContour.Companion.FACE_C
import org.multipaz.faces.MultipazFaceContour.Companion.LOWER_LIP_TOP_C
import org.multipaz.faces.MultipazFaceContour.Companion.LEFT_CHEEK_C
import org.multipaz.faces.MultipazFaceContour.Companion.LOWER_LIP_BOTTOM_C
import org.multipaz.faces.MultipazFaceContour.Companion.LEFT_EYE_C
import org.multipaz.faces.MultipazFaceContour.Companion.LEFT_EYEBROW_TOP_C
import org.multipaz.faces.MultipazFaceContour.Companion.UPPER_LIP_BOTTOM_C
import org.multipaz.faces.MultipazFaceContour.Companion.LEFT_EYEBROW_BOTTOM_C
import org.multipaz.faces.MultipazFaceContour.Companion.NOSE_BOTTOM_C
import org.multipaz.faces.MultipazFaceContour.Companion.NOSE_BRIDGE_C
import org.multipaz.faces.MultipazFaceContour.Companion.RIGHT_CHEEK_C
import org.multipaz.faces.MultipazFaceContour.Companion.RIGHT_EYEBROW_BOTTOM_C
import org.multipaz.faces.MultipazFaceContour.Companion.RIGHT_EYEBROW_TOP_C
import org.multipaz.faces.MultipazFaceContour.Companion.RIGHT_EYE_C
import org.multipaz.faces.MultipazFaceContour.Companion.UNKNOWN_C
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
import org.multipaz.faces.MultipazFaceLandmark.Companion.UNKNOWN_LM
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
private val faceDetectorOptions = MLKFaceDetectorOptions().apply {
    performanceMode = MLKFaceDetectorPerformanceModeFast
    landmarkMode = MLKFaceDetectorLandmarkModeAll
    contourMode = MLKFaceDetectorContourModeAll
}

@OptIn(ExperimentalForeignApi::class)
actual fun detectFaces(frameData: CameraFrame): List<FaceObject>? {
    if (frameData.width <= 0 || frameData.height <= 0) {
        throw IllegalArgumentException("Invalid bitmap dimensions.")
    }

    return detectFaces(
        MLKVisionImage(frameData.cameraImage.uiImage).apply {
            this.setOrientation(calculateMLKitImageOrientation(frameData))}
	)
}

@OptIn(ExperimentalForeignApi::class)
actual fun detectFaces(image: ImageBitmap): List<FaceObject>? {
    return detectFaces(MLKVisionImage(image.toUIImage()!!))
}

@OptIn(ExperimentalForeignApi::class)
fun detectFaces(mlkVisionImage: MLKVisionImage): List<FaceObject>? =
    try {
        val detector = MLKFaceDetector.faceDetectorWithOptions(faceDetectorOptions)
        val faces = detector.resultsInImage(mlkVisionImage as objcnames.protocols.MLKCompatibleImageProtocol, null)
        (faces as List<MLKFace>).map { it.toMultiplatformFace() }
    } catch (e: Exception) {
        Logger.e(TAG, "Error during face detection", e)
        throw e
    }

@OptIn(ExperimentalForeignApi::class)
fun CValue<CGRect>.toRect(): androidx.compose.ui.geometry.Rect {
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
fun MLKVisionPoint.toOffset(): Offset {
    return Offset(x.toFloat(), y.toFloat())
}

/** iOS types are text string names. Android types are Integers. Converge to the common ground Integer types. */
val landmarkTypes = mapOf(
    "NoseBase" to NOSE_BASE_LM,
    "LeftEar" to LEFT_EAR_LM,
    "MouthBottom" to MOUTH_BOTTOM_LM,
    "RightEye" to RIGHT_EYE_LM,
    "LeftCheek" to LEFT_CHEEK_LM,
    "LeftEye" to LEFT_EYE_LM,
    "MouthLeft" to MOUTH_LEFT_LM,
    "RightEar" to RIGHT_EAR_LM,
    "MouthRight" to MOUTH_RIGHT_LM,
    "RightCheek" to RIGHT_CHEEK_LM)

private val contourTypes = mapOf(
    "LeftEyebrowBottom" to LEFT_EYEBROW_BOTTOM_C,
    "UpperLipTop" to UPPER_LIP_TOP_C,
    "LowerLipTop" to LOWER_LIP_TOP_C,
    "LeftCheek" to LEFT_CHEEK_C,
    "Face" to FACE_C,
    "RightEyebrowBottom" to RIGHT_EYEBROW_BOTTOM_C,
    "LeftEye" to LEFT_EYE_C,
    "LowerLipBottom" to LOWER_LIP_BOTTOM_C,
    "RightCheek" to RIGHT_CHEEK_C,
    "NoseBridge" to NOSE_BRIDGE_C,
    "LeftEyebrowTop" to LEFT_EYEBROW_TOP_C,
    "RightEye" to RIGHT_EYE_C,
    "RightEyebrowTop" to RIGHT_EYEBROW_TOP_C,
    "NoseBottom" to NOSE_BOTTOM_C,
    "UpperLipBottom" to UPPER_LIP_BOTTOM_C)

@OptIn(ExperimentalForeignApi::class)
fun List<MLKFaceLandmark>.toMFaceLandmarkList(): List<MultipazFaceLandmark> {
    return this.map { faceLandmark ->
        val typeString = faceLandmark.type.toString()
        val type = landmarkTypes[typeString] ?: UNKNOWN_LM
        MultipazFaceLandmark(type, (faceLandmark.position as MLKVisionPoint).toOffset())
    }
}

@OptIn(ExperimentalForeignApi::class)
fun List<MLKFaceContour>.toMFaceContourList(): List<MultipazFaceContour> {
    return this.map { faceContour ->
        val typeString = faceContour.type.toString()
        val points = faceContour.points.map { (it as MLKVisionPoint).toOffset() }
        val type = contourTypes[typeString] ?: UNKNOWN_C
        MultipazFaceContour(type, points)
    }
}

@OptIn(ExperimentalForeignApi::class)
fun MLKFace.toMultiplatformFace(): FaceObject {
    Logger.d(TAG, "${landmarkTypes.keys} ... ${contourTypes.keys}")
    return with(this) {
        FaceObject(
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

@OptIn(ExperimentalForeignApi::class)
private fun ImageBitmap.toUIImage(): UIImage? {
    val width = this.width
    val height = this.height
    val buffer = IntArray(width * height)

    this.readPixels(buffer)

    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val context = CGBitmapContextCreate(
        data = buffer.refTo(0),
        width = width.toULong(),
        height = height.toULong(),
        bitsPerComponent = 8u,
        bytesPerRow = (4 * width).toULong(),
        space = colorSpace,
        bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value
    )

    val cgImage = CGBitmapContextCreateImage(context)
    return cgImage?.let { UIImage.imageWithCGImage(it) }
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
