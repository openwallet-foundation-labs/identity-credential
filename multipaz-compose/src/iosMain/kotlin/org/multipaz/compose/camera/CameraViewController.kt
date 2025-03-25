package org.multipaz.compose.camera

import kotlinx.cinterop.ExperimentalForeignApi
import org.multipaz.compose.camera.CameraSelection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceDiscoverySession
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoOrientation
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeLeft
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeRight
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoOrientationPortraitUpsideDown
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.fileDataRepresentation
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientation
import platform.UIKit.UIView
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue

/** iOS specific Camera preview/capture controller. */
class CameraViewController : NSObject(), AVCapturePhotoCaptureDelegateProtocol {
    private var currentCamera: AVCaptureDevice? = null
    private var photoOutput: AVCapturePhotoOutput? = null
    var captureSession: AVCaptureSession? = null
    var previewLayer: AVCaptureVideoPreviewLayer? = null
    var onFrameCapture: ((NSData?) -> Unit)? = null
    var onError: ((CameraException) -> Unit)? = null

    sealed class CameraException : Exception() {
        class DeviceNotAvailable : CameraException()
        class ConfigurationError(message: String) : CameraException()
        class CaptureError(message: String) : CameraException()
    }

    internal fun setupSession(cameraLens: CameraSelection) {
        try {
            captureSession = AVCaptureSession()
            captureSession?.beginConfiguration()
            if (!setupInputs(cameraLens)) {
                throw CameraException.DeviceNotAvailable()
            }
            setupPhotoOutput()
            captureSession?.commitConfiguration()
        } catch (e: CameraException) {
            cleanupSession()
            onError?.invoke(e)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun setupInputs(cameraLens: CameraSelection): Boolean {
        currentCamera =
            AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
                listOf(AVCaptureDeviceTypeBuiltInWideAngleCamera),
                mediaType = AVMediaTypeVideo,
                position = when (cameraLens) {
                    CameraSelection.DEFAULT_FRONT_CAMERA -> AVCaptureDevicePositionFront
                    CameraSelection.DEFAULT_BACK_CAMERA -> AVCaptureDevicePositionBack
                    else -> throw CameraException.ConfigurationError("Invalid camera selection: $cameraLens")
                }
            ).devices.firstOrNull() as AVCaptureDevice?

        try {
            val input = AVCaptureDeviceInput.deviceInputWithDevice(
                currentCamera!!,
                null
            ) ?: return false

            if (captureSession?.canAddInput(input) == true) {
                captureSession?.addInput(input)
                return true
            }
        } catch (e: Exception) {
            throw CameraException.ConfigurationError(e.message ?: "Unknown CameraEngine error")
        }
        return false
    }

    private fun setupPhotoOutput() {
        photoOutput = AVCapturePhotoOutput()
        photoOutput?.setHighResolutionCaptureEnabled(true)
        if (captureSession?.canAddOutput(photoOutput!!) == true) {
            captureSession?.addOutput(photoOutput!!)
        } else {
            throw CameraException.ConfigurationError("Cannot add photo output")
        }
    }

    /** Start camera images capturing (iOS specific flow). */
    internal fun startSession() {
        if (captureSession?.isRunning() == false) {
            dispatch_async(
                dispatch_get_global_queue(
                    DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(),
                    0u
                )
            ) {
                captureSession?.startRunning()
            }
        }
    }

    /** Stop camera images capturing session. */
    internal fun stopSession() {
        if (captureSession?.isRunning() == true) {
            captureSession?.stopRunning()
        }
    }

    /** Cleanup Camera session. */
    private fun cleanupSession() {
        stopSession()
        previewLayer?.removeFromSuperlayer()
        previewLayer = null
        captureSession = null
        photoOutput = null
        currentCamera = null
    }

    @OptIn(ExperimentalForeignApi::class)
    internal fun setupPreview(view: UIView) {
        captureSession?.let { session ->
            val newPreviewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
                videoGravity = AVLayerVideoGravityResizeAspectFill
                setFrame(view.bounds)
                currentVideoOrientation()?.let { newOrientation -> connection?.videoOrientation = newOrientation }
            }
            view.layer.addSublayer(newPreviewLayer)

            previewLayer = newPreviewLayer
        }
    }

    /**
     * Convert UIDeviceOrientation to AVCaptureVideoOrientation.
     *
     * @return AVCaptureVideoOrientation. Portrait if not identified, null to indicate no orientation needed (used for
     *     horizontal device to avoid confusing UX forcing one unexpectedly).
     */
    internal fun currentVideoOrientation(): AVCaptureVideoOrientation? {
        return when (UIDevice.currentDevice.orientation) {
            UIDeviceOrientation.UIDeviceOrientationPortrait -> AVCaptureVideoOrientationPortrait
            UIDeviceOrientation.UIDeviceOrientationLandscapeLeft -> AVCaptureVideoOrientationLandscapeRight
            UIDeviceOrientation.UIDeviceOrientationLandscapeRight -> AVCaptureVideoOrientationLandscapeLeft
            UIDeviceOrientation.UIDeviceOrientationPortraitUpsideDown -> AVCaptureVideoOrientationPortraitUpsideDown
            UIDeviceOrientation.UIDeviceOrientationFaceUp -> null
            UIDeviceOrientation.UIDeviceOrientationFaceDown -> null
            else -> AVCaptureVideoOrientationPortrait // Unknown orientation code, assume to portrait.
        }
    }

    fun captureFrame() {
        val settings = AVCapturePhotoSettings()
        settings.isHighResolutionPhotoEnabled()
        photoOutput?.capturePhotoWithSettings(settings, delegate = this)
    }

    override fun captureOutput(
        output: AVCapturePhotoOutput,
        didFinishProcessingPhoto: AVCapturePhoto,
        error: NSError?
    ) {
        if (error != null) {
            onError?.invoke(CameraException.CaptureError(error.localizedDescription))
        }
        else {
            onFrameCapture?.invoke(didFinishProcessingPhoto.fileDataRepresentation())
        }
    }
}