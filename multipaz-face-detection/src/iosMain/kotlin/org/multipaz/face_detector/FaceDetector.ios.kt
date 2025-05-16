package org.multipaz.face_detector

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import org.multipaz.compose.camera.CameraFrame
import org.multipaz.util.Logger

import cocoapods.GoogleMLKit.MLKFace
import cocoapods.GoogleMLKit.MLKFaceContour
import cocoapods.GoogleMLKit.MLKFaceDetector
import cocoapods.GoogleMLKit.MLKFaceDetectorContourModeAll
import cocoapods.GoogleMLKit.MLKFaceDetectorLandmarkModeAll
import cocoapods.GoogleMLKit.MLKFaceLandmark
import cocoapods.GoogleMLKit.MLKFaceDetectorOptions
import cocoapods.GoogleMLKit.MLKFaceDetectorPerformanceModeFast
import cocoapods.MLKitVision.MLKVisionPoint
import cocoapods.MLKitVision.MLKVisionImage
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRect
import platform.UIKit.UIImage


private const val TAG = "FaceDetector"

@OptIn(ExperimentalForeignApi::class)
private val faceDetectorOptions = MLKFaceDetectorOptions().apply {
    performanceMode = MLKFaceDetectorPerformanceModeFast
    landmarkMode = MLKFaceDetectorLandmarkModeAll
    contourMode = MLKFaceDetectorContourModeAll
}

@OptIn(ExperimentalForeignApi::class)
actual fun detectFaces(frameData: CameraFrame): FaceData? {
    if (frameData.width <= 0 || frameData.height <= 0) {
        throw IllegalArgumentException("Invalid bitmap dimensions.")
    }
    return detectFaces(
        MLKVisionImage(frameData.cameraImage.uiImage),
        width = frameData.width,
        height = frameData.height,
        rotation = (frameData.rotation + 270) % 360)
}

@OptIn(ExperimentalForeignApi::class)
actual fun detectFaces(image: ImageBitmap): FaceData? {
    return detectFaces(MLKVisionImage(image.toUIImage()!!), image.width, image.height, rotation = 0)
}

@OptIn(ExperimentalForeignApi::class)
fun detectFaces(mlkVisionImage: MLKVisionImage, width: Int, height: Int, rotation: Int): FaceData? =
    try {
        val detector = MLKFaceDetector.faceDetectorWithOptions(faceDetectorOptions)
        val faces = detector.resultsInImage(mlkVisionImage as objcnames.protocols.MLKCompatibleImageProtocol, null)
        (faces as List<MLKFace>).toFaceData(width, height, rotation)

    } catch (e: Exception) {
        Logger.e(TAG, "Error during face detection", e)
        throw e
    }

@OptIn(ExperimentalForeignApi::class)
private fun List<MLKFace>.toFaceData(width: Int, height: Int, rotation: Int): FaceData? {
    if (isEmpty()) return null

    val faces = map { it.toMultiplatformFace() }
    return FaceData(faces, width, height, rotation)
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

private val landmarkTypes = mutableMapOf<String, Int>()
private val contourTypes = mutableMapOf<String, Int>()

@OptIn(ExperimentalForeignApi::class)
fun List<MLKFaceLandmark>.toMFaceLandmarkList(): List<FaceLandmark> {
    return this.map { faceLandmark ->
        val typeString = faceLandmark.type.toString()
        val type = landmarkTypes[typeString] ?: run {
            val newId = landmarkTypes.size
            landmarkTypes[typeString] = newId
            newId
        }
        //FaceLandmark(type, (faceLandmark.position as MLKVisionPoint).toOffset())
        FaceLandmark(type, Offset(1f, 1f))
    }
}

@OptIn(ExperimentalForeignApi::class)
fun List<MLKFaceContour>.toMFaceContourList(): List<FaceContour> {
    return this.map { faceContour ->
        val typeString = faceContour.type.toString()
        val points = faceContour.points.map { (it as MLKVisionPoint).toOffset() }
        val type = contourTypes[typeString] ?: run {
            val newId = contourTypes.size
            landmarkTypes[typeString] = newId
            newId
        }
        FaceContour(type, points)
    }
}

@OptIn(ExperimentalForeignApi::class)
fun MLKFace.toMultiplatformFace(): FaceObject {
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