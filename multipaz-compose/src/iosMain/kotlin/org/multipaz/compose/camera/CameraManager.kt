package org.multipaz.compose.camera

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import org.jetbrains.skia.makeFromEncoded
import org.jetbrains.skiko.currentNanoTime
import org.multipaz.util.Logger
import org.multipaz.util.toByteArray
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset1280x720
import platform.AVFoundation.AVCaptureSessionPreset1920x1080
import platform.AVFoundation.AVCaptureSessionPreset640x480
import platform.AVFoundation.AVCaptureSessionPresetPhoto
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoOrientation
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeLeft
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeRight
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoOrientationPortraitUpsideDown
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.position
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.CMTimeMake
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetDataSize
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.CoreVideo.kCVPixelFormatType_420YpCbCr8BiPlanarFullRange
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientation
import platform.UIKit.UIImage
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create
import platform.posix.memcpy

class CameraManager(
    private val cameraSelection: CameraSelection,
    private val captureResolution: CameraCaptureResolution
) {
    private val TAG = "CameraManager"
    private var captureSession: AVCaptureSession? = null
    private var frameCallback: ((CameraFrame) -> ImageBitmap?)? = null
    private var videoOutput = AVCaptureVideoDataOutput()
    private var frameCaptureDelegate: CameraFrameCaptureDelegate? = null

    @OptIn(ExperimentalForeignApi::class)
    fun startCamera(onFrameCaptured: (CameraFrame) -> ImageBitmap?) {
        frameCallback = onFrameCaptured
        frameCaptureDelegate = CameraFrameCaptureDelegate(frameCallback)

        captureSession = AVCaptureSession().apply {
            sessionPreset = AVCaptureSessionPresetPhoto
            sessionPreset = when (captureResolution) {
                CameraCaptureResolution.LOW -> AVCaptureSessionPreset640x480
                CameraCaptureResolution.MEDIUM -> AVCaptureSessionPreset1280x720
                CameraCaptureResolution.HIGH -> AVCaptureSessionPreset1920x1080
            }
        }

        captureSession!!.beginConfiguration()

        val devicePosition = when (cameraSelection) {
            CameraSelection.DEFAULT_BACK_CAMERA -> AVCaptureDevicePositionBack
            CameraSelection.DEFAULT_FRONT_CAMERA -> AVCaptureDevicePositionFront
        }

        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
            ?.let { defaultDevice ->
                AVCaptureDevice.devicesWithMediaType(AVMediaTypeVideo).firstOrNull {
                    (it as AVCaptureDevice).position == devicePosition
                } ?: defaultDevice
            } ?: throw IllegalStateException("No camera available")

        @OptIn(ExperimentalForeignApi::class)
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device as AVCaptureDevice, error = null)
        captureSession!!.addInput(input!!)

        val captureQueue = dispatch_queue_create("sampleBufferQueue", attr = null)
        // Suggested alternative (doesn't help with stuttering as is, needs supporting throttling code. */
        //val captureQueue = dispatch_get_global_queue(QOS_CLASS_USER_INTERACTIVE.toLong(), 0u)

        videoOutput.setSampleBufferDelegate(frameCaptureDelegate, captureQueue)
        videoOutput.alwaysDiscardsLateVideoFrames = true
        videoOutput.videoSettings =
            mapOf( // Several suggested variants. Others might help with Alpha channel decoding.
                kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_32BGRA
                // kCVPixelBufferPixelFormatTypeKey to kCMPixelFormat_32BGRA
                // kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_420YpCbCr8BiPlanarFullRange
            )

        videoOutput.setVideoSettings(
            mapOf(kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_420YpCbCr8BiPlanarFullRange)
        )
        videoOutput.connectionWithMediaType(AVMediaTypeVideo)?.videoMinFrameDuration = CMTimeMake(1, 30) // ~30fps

        /* For troubleshooting: list available formats to match the code ULong with .h header.
        videoOutput.availableVideoCVPixelFormatTypes.forEach {
        Logger.d(TAG, "AvailableVideoCVPixelFormatTypes: $it")
        }
        */
        if (captureSession!!.canAddOutput(videoOutput)) {
            captureSession!!.addOutput(videoOutput)
        } else {
            Logger.d(TAG, "Failed to add output")
        }

        captureSession!!.commitConfiguration()
        captureSession!!.startRunning()
    }

    fun stopCamera() {
        frameCaptureDelegate?.let {
            videoOutput.setSampleBufferDelegate(null, null)
            frameCaptureDelegate = null
        }
        captureSession?.stopRunning()
        captureSession = null
    }

    class CameraFrameCaptureDelegate(
        private val onFrame: ((CameraFrame) -> ImageBitmap?)?,
    ) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
        private val TAG = "CameraFrameCaptureDelegate"
        private var t0: Long = 0

        @OptIn(ExperimentalForeignApi::class)
        override fun captureOutput(
            output: AVCaptureOutput,
            didOutputSampleBuffer: CMSampleBufferRef?,
            fromConnection: AVCaptureConnection,
        ) {
            if (onFrame == null) return

            /** Temp. buffer decode variants. */
            val decodePath = 2

            val imageBuffer = CMSampleBufferGetImageBuffer(didOutputSampleBuffer) ?: return
            val t = currentNanoTime()

            when (decodePath) {
                1 -> {
                    // Working option (when imageData is ImageBitmap). Slow, wrong colors, crashing after a while.
                    val imageBitmap = cvpToImageBitmap(imageBuffer)
                    onFrame.invoke(
                        CameraFrame(
                            imageData = ImageData(UIImage()), // Mock.
                            imageBitmap.width,
                            imageBitmap.height,
                            270
                        )
                    )
                }

                2 -> {
                    // Not working Skia option. Might be fixed with sk
                    CVPixelBufferLockBaseAddress(imageBuffer, 0uL)
                    val baseAddress = CVPixelBufferGetBaseAddress(imageBuffer)
                    val bufferSize = CVPixelBufferGetDataSize(imageBuffer)
                    val data = NSData.dataWithBytes(bytes = baseAddress, length = bufferSize)

                    CVPixelBufferUnlockBaseAddress(imageBuffer, 0uL)
                    Logger.d(TAG, "Data size: $bufferSize, ${data.length}")
                    val bytes = data.toByteArray()
                    Logger.d(TAG, "Bytes size: ${bytes.size}")
                    t0 = t
                    try {
                        //val image = Image.makeFromEncoded(bytes) // correct but fails (IllegalArgumentException)
                        val image = Image.makeFromEncoded(data) //coorect but also fails
                        Logger.d(TAG, "Img size: ${image.width}x${image.height}")

                        onFrame.invoke(
                            CameraFrame(
                                imageData = ImageData(UIImage()), // Mock.
                                width = image.width,
                                height = image.height,
                                rotation = 270
                            )
                        )
                    } catch (e: Exception) {
                        Logger.d(TAG, "Exception: $e")
                    }
                }

                3 -> { // Stuttering bad (8sec) due to periodic GC every ~15 frames.
                    convertCVImageBufferToUIImage(imageBuffer)?.let {
                        val width = CVPixelBufferGetWidth(imageBuffer).toInt()
                        val height = CVPixelBufferGetHeight(imageBuffer).toInt()
                        val cameraFrame = CameraFrame(ImageData(it), width, height, 270)
                        onFrame.invoke(cameraFrame)
                    }
                }
            }
            Logger.d(TAG, "Frame T=${(t - t0) / 1000000}ms")
            t0 = t
        }
    }


    @OptIn(ExperimentalForeignApi::class)
    private inline fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        val byteArray = ByteArray(size)
        if (size > 0) {
            byteArray.usePinned { pinned ->
                memcpy(pinned.addressOf(0), this.bytes, this.length)
            }
        }
        return byteArray
    }
}

/**
 * Converts the phone screen physical orientation to the appropriate AVCaptureVideoOrientation.
 *
 * @return The corresponding AVCaptureVideoOrientation or null if the device moved close to the horizontal plane
 *     to help avoid the unexpected UX.
 */
private fun getVideoOrientationForDevice(): AVCaptureVideoOrientation? {
    return when (UIDevice.currentDevice.orientation) {
        UIDeviceOrientation.UIDeviceOrientationPortrait -> AVCaptureVideoOrientationPortrait
        UIDeviceOrientation.UIDeviceOrientationLandscapeLeft -> AVCaptureVideoOrientationLandscapeRight
        UIDeviceOrientation.UIDeviceOrientationLandscapeRight -> AVCaptureVideoOrientationLandscapeLeft
        UIDeviceOrientation.UIDeviceOrientationPortraitUpsideDown -> AVCaptureVideoOrientationPortraitUpsideDown
        UIDeviceOrientation.UIDeviceOrientationFaceUp -> null // Don't change orientation.
        UIDeviceOrientation.UIDeviceOrientationFaceDown -> null // Don't change orientation.
        else -> AVCaptureVideoOrientationPortrait // Default (unknown) to portrait.
    }
}
