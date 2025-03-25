package org.multipaz.facedetect.camera.plugins.facedetect

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.multipaz.compose.camera.CameraEngine
import org.multipaz.compose.camera.CameraSelection
import org.multipaz.compose.camera.CameraWorkResult
import org.multipaz.util.Logger
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVMetadataFaceObject
import platform.AVFoundation.AVMetadataObjectTypeFace
import platform.darwin.NSObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

actual suspend fun platformDetectFace(bitmap: ImageBitmap): CameraWorkResult {
    // TODO("Not yet implemented")
    Logger.d("platformDetectFace", "Not implemented")
    return CameraWorkResult.Error(Exception("platformDetectFace not implemented"))
}

actual fun platformStartDetection(
    cameraEngine: CameraEngine,
    onFaceDetected: (faceData: CameraWorkResult) -> Unit,
    onImageSize: (Size) -> Unit,
    onImageRotation: (Int) -> Unit,
    coroutineScope: CoroutineScope
) {

    val faceDetector = FaceDetector(cameraEngine, onFaceDetected = {
        onFaceDetected(it)
    })
    cameraEngine.setMetadaObjectsDelegate(faceDetector)
    cameraEngine.setMetadataObjectTypes(
        listOf(
            AVMetadataObjectTypeFace!!
        )
    )
    cameraEngine.startSession()
}

private class FaceDetector(
    private val cameraEngine: CameraEngine,
    private val onFaceDetected: (CameraWorkResult) -> Unit,
) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {

    private val TAG = "FaceDetector"

    private val isProcessing = atomic(false)
    private val scope = CoroutineScope(Dispatchers.Main)
    private var lastFaceDetected: CameraWorkResult? = null
    private var detectionJob: Job? = null

    @OptIn(ExperimentalForeignApi::class)
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection
    ) {
        if (isProcessing.value) return

        val isMirrored = cameraEngine.cameraSelection == CameraSelection.DEFAULT_FRONT_CAMERA

        val view = cameraEngine.getCameraPreviewLayer() ?: return

        Logger.d(TAG, "captureOutput ${didOutputMetadataObjects.size}")
        for (metadata in didOutputMetadataObjects) {
            if (metadata !is AVMetadataFaceObject) continue

            Logger.d(TAG, metadata.toString())

            val faceAngle = if (metadata.hasRollAngle) metadata.rollAngle + 90 else 0.0 // typical 270 deg + face angle
            var width: Double
            var height: Double
            view.bounds.useContents {
                width = size.height
                height = size.width
            }
            Logger.d(TAG, "view width=$width,height=$height")

            val r = metadata.bounds.useContents {
                val x = origin.x * width
                val y = origin.y * height
                val w = size.width * width
                val h = size.height * height
                Rect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
            }

            Logger.d(TAG, "faceAngle=$faceAngle,faceRect=$r")

            if (r.width == 0f || r.height == 0f) break

            // Fake eyes. TODO: Figure better scaling like 0.3 an 0.7 ?
            val leftEye = calculateRotatedOffset(
                r.center,
                Offset(r.left + r.width * 0.2f, r.top + r.height * 0.5f), faceAngle, isMirrored
            )
            val rightEye = calculateRotatedOffset(
                r.center,
                Offset(r.left + r.width * 0.8f, r.top + r.height * 0.5f), faceAngle, isMirrored
            )
            val mouth = calculateRotatedOffset(
                r.center,
                Offset(r.left + r.width * 0.5f, r.top + r.height * 0.8f), faceAngle, isMirrored
            )

            val faceData = CameraWorkResult.FaceData(r, leftEye, rightEye, mouth)
            val detectionResult = CameraWorkResult.FaceDetectionSuccess(faceData, null, null) ?: continue
            if (detectionResult == lastFaceDetected) continue
            lastFaceDetected = detectionResult
            processFace(lastFaceDetected!!)
            break // for(metadata) loop.
        }
    }

    private fun calculateRotatedOffset(center: Offset, point: Offset, angleDegrees: Double, isMirrored: Boolean): Offset {
        val angleRadians = angleDegrees * PI / 180.0
        val dx = point.x - center.x
        val dy = point.y - center.y
        val mirroredDx = if (isMirrored) -dx else dx
        val rotatedX = mirroredDx * cos(angleRadians) - dy * sin(angleRadians)
        val rotatedY = mirroredDx * sin(angleRadians) + dy * cos(angleRadians)
        return Offset(center.x + rotatedX.toFloat(), center.y + rotatedY.toFloat())
    }

    private fun processFace(faceData: CameraWorkResult) {
        Logger.d(TAG, "processFace")
        detectionJob?.cancel()
        detectionJob = scope.launch {
            if (isProcessing.compareAndSet(expect = false, update = true)) {
                try {
                    lastFaceDetected = faceData
                    onFaceDetected(faceData)
                } finally {
                    isProcessing.value = false
                }
            }
        }
    }
}