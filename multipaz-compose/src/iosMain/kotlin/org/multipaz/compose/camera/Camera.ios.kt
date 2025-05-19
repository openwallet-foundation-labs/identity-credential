package org.multipaz.compose.camera

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cValue
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import kotlinx.coroutines.runBlocking
import org.multipaz.util.Logger
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
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeLeft
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeRight
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoOrientationPortraitUpsideDown
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.position
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectZero
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.Foundation.NSError
import platform.QuartzCore.CALayer
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientation
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create
import platform.UIKit.UIImage
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreImage.CIImage
import platform.CoreImage.CIContext
import platform.CoreImage.createCGImage
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSelectorFromString
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi

private val TAG = "Camera"

@Composable
actual fun Camera(
    modifier: Modifier,
    cameraSelection: CameraSelection,
    captureResolution: CameraCaptureResolution,
    showCameraPreview: Boolean,
    onFrameCaptured: suspend (frame: CameraFrame) -> Unit
) {
    val cameraManager = remember {
        CameraManager(
            cameraSelection = cameraSelection,
            captureResolution = captureResolution,
            onCameraFrameCaptured = onFrameCaptured
        )
    }

    DisposableEffect(Unit) {
        val orientationListener = OrientationListener { orientation ->
            cameraManager.setCurrentOrientation(orientation)
        }
        orientationListener.register()
        onDispose {
            orientationListener.unregister()
        }
    }

    if (showCameraPreview) {
        UIKitView<UIView>(
            modifier = modifier.fillMaxSize(),
            factory = {
                val previewContainer = CameraPreviewView(cameraManager)
                cameraManager.startCamera(previewContainer.layer)
                previewContainer
            },
            properties = UIKitInteropProperties(
                isInteractive = true,
                isNativeAccessibilityEnabled = true,
            )
        )
    } else {
        cameraManager.startCamera(null)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.stopCamera()
        }
    }
}


@OptIn(ExperimentalForeignApi::class)
private class CameraPreviewView(
    private val coordinator: CameraManager
): UIView(frame = cValue { CGRectZero }) {
    @OptIn(ExperimentalForeignApi::class)
    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setValue(true, kCATransactionDisableActions)
        layer.setFrame(frame)
        coordinator.setFrame(frame)
        CATransaction.commit()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class CameraManager(
    val cameraSelection: CameraSelection,
    val captureResolution: CameraCaptureResolution,
    val onCameraFrameCaptured: suspend (cameraFrame: CameraFrame) -> Unit,
): AVCaptureVideoDataOutputSampleBufferDelegateProtocol, NSObject() {

    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    lateinit var captureSession: AVCaptureSession

    fun stopCamera() {
        captureSession.stopRunning()
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    fun startCamera(layer: CALayer?) {
        captureSession = AVCaptureSession()
        captureSession.sessionPreset = when (captureResolution) {
            CameraCaptureResolution.LOW -> AVCaptureSessionPreset640x480
            CameraCaptureResolution.MEDIUM -> AVCaptureSessionPreset1280x720
            CameraCaptureResolution.HIGH -> AVCaptureSessionPreset1920x1080
        }

        val devices = AVCaptureDevice.devicesWithMediaType(AVMediaTypeVideo).map { it as AVCaptureDevice }

        val requestedDevicePosition = when (cameraSelection) {
            CameraSelection.DEFAULT_BACK_CAMERA -> AVCaptureDevicePositionBack
            CameraSelection.DEFAULT_FRONT_CAMERA -> AVCaptureDevicePositionFront
        }
        val device = devices.firstOrNull { device ->
            device.position == requestedDevicePosition
        } ?: run {
            AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        }

        if (device == null) {
            Logger.e(TAG, "Device has no camera")
            return
        }

        val videoInput = memScoped {
            val error: ObjCObjectVar<NSError?> = alloc<ObjCObjectVar<NSError?>>()
            val videoInput = AVCaptureDeviceInput(device = device, error = error.ptr)
            if (error.value != null) {
                Logger.e(TAG, "Error constructing input: ${error.value}")
                null
            } else {
                videoInput
            }
        }

        if (videoInput != null && captureSession.canAddInput(videoInput)) {
            captureSession.addInput(videoInput)
        } else {
            Logger.e(TAG, "Error adding input")
            return
        }

        val videoDataOutput = AVCaptureVideoDataOutput()
        if (captureSession.canAddOutput(videoDataOutput)) {
            videoDataOutput.videoSettings = mapOf(
                kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_32BGRA,
            )
            videoDataOutput.alwaysDiscardsLateVideoFrames = true
            val queue = dispatch_queue_create("org.multipaz.compose.camera_queue", null)
            videoDataOutput.setSampleBufferDelegate(this, queue = queue)
            captureSession.addOutput(videoDataOutput)
        } else {
            Logger.e(TAG, "Error adding output")
            return
        }

        if (layer != null) {
            previewLayer = AVCaptureVideoPreviewLayer(session = captureSession)
            previewLayer!!.frame = layer.bounds
            previewLayer!!.videoGravity = AVLayerVideoGravityResizeAspectFill
            setCurrentOrientation(newOrientation = UIDevice.currentDevice.orientation)
            layer.addSublayer(previewLayer!!)
        }

        captureSession.startRunning()
    }

    fun setCurrentOrientation(newOrientation: UIDeviceOrientation) {
        when(newOrientation) {
            UIDeviceOrientation.UIDeviceOrientationLandscapeLeft ->
                previewLayer?.connection?.videoOrientation = AVCaptureVideoOrientationLandscapeRight
            UIDeviceOrientation.UIDeviceOrientationLandscapeRight ->
                previewLayer?.connection?.videoOrientation = AVCaptureVideoOrientationLandscapeLeft
            UIDeviceOrientation.UIDeviceOrientationPortrait ->
                previewLayer?.connection?.videoOrientation = AVCaptureVideoOrientationPortrait
            UIDeviceOrientation.UIDeviceOrientationPortraitUpsideDown ->
                previewLayer?.connection?.videoOrientation = AVCaptureVideoOrientationPortraitUpsideDown
            else ->
                previewLayer?.connection?.videoOrientation = AVCaptureVideoOrientationPortrait
        }
    }

    @OptIn(ExperimentalForeignApi::class, NativeRuntimeApi::class)
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection
    ) {
        val uiImage = didOutputSampleBuffer!!.toUIImage()
        val cameraImage = CameraImage(uiImage)

        // TODO: Actually calculate the correct matrix, right now we're just
        //  return the Identity matrix.
        //
        val previewTransformation = Matrix()

        val cameraFrame = CameraFrame(
            cameraImage = cameraImage,
            width = (uiImage.size.useContents { width }.toInt()),
            height = (uiImage.size.useContents { height }.toInt()),
            previewTransformation = previewTransformation
        )

        runBlocking {
            onCameraFrameCaptured(cameraFrame)
        }

        // To is needed to avoid lockups, see https://youtrack.jetbrains.com/issue/KT-74239
        GC.collect()
    }

    fun setFrame(rect: CValue<CGRect>) {
        previewLayer?.setFrame(rect)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class OrientationListener(
    val orientationChanged: (UIDeviceOrientation) -> Unit
) : NSObject() {

    val notificationName = platform.UIKit.UIDeviceOrientationDidChangeNotification

    @OptIn(BetaInteropApi::class)
    @Suppress("UNUSED_PARAMETER")
    @ObjCAction
    fun orientationDidChange(arg: NSNotification) {
        orientationChanged(UIDevice.currentDevice.orientation)
    }

    fun register() {
        NSNotificationCenter.defaultCenter.addObserver(
            observer = this,
            selector = NSSelectorFromString(
                OrientationListener::orientationDidChange.name + ":"
            ),
            name = notificationName,
            `object` = null
        )
    }

    fun unregister() {
        NSNotificationCenter.defaultCenter.removeObserver(
            observer = this,
            name = notificationName,
            `object` = null
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CMSampleBufferRef.toUIImage(): UIImage {
    val imageBuffer = CMSampleBufferGetImageBuffer(this)
    if (imageBuffer == null) {
        throw IllegalStateException("Could not get CVImageBufferRef from CMSampleBufferRef.")
    }
    val ciImage = CIImage.imageWithCVPixelBuffer(imageBuffer)
    if (ciImage == null) {
        throw IllegalStateException("Could not create CIImage from CVImageBufferRef.")
    }

    val temporaryContext: CIContext? = CIContext.contextWithOptions(null)
    if (temporaryContext == null) {
        throw IllegalStateException("Error: Could not create CIContext.")
    }

    val bufferWidth = CVPixelBufferGetWidth(imageBuffer).toDouble()
    val bufferHeight = CVPixelBufferGetHeight(imageBuffer).toDouble()
    val imageRect = platform.CoreGraphics.CGRectMake(
        x = 0.0,
        y = 0.0,
        width = bufferWidth,
        height = bufferHeight
    )

    var videoImage: CGImageRef? = null
    var finalUiImage: UIImage? = null
    try {
        videoImage = temporaryContext.createCGImage(ciImage, fromRect = imageRect)
        if (videoImage == null) {
            throw IllegalStateException("Error: Could not create CGImageRef from CIImage.")
        }
        finalUiImage = UIImage(cGImage = videoImage)
    } catch (e: Throwable) {
        throw IllegalStateException("Error during image creation", e)
    } finally {
        videoImage?.let {
            platform.CoreGraphics.CGImageRelease(it)
        }
    }
    return finalUiImage
}

