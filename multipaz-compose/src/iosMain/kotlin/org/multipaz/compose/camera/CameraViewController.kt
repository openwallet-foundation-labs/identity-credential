package org.multipaz.compose.camera

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import org.multipaz.util.Logger
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceDiscoverySession
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoOrientation
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeLeft
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeRight
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoOrientationPortraitUpsideDown
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVVideoCodecKey
import platform.AVFoundation.AVVideoCodecTypeJPEG
import platform.Foundation.NSNotificationCenter
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIColor
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientation
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

class CameraViewController(
    private val cameraSelector: CameraSelector,
    private val showPreview: Boolean
) : UIViewController(nibName = null, bundle = null) {

    private val doNotChange: AVCaptureVideoOrientation = 7162530L // Arbitrary code.
    private val previewView = UIView()
    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    private val captureSession = AVCaptureSession()

    override fun viewDidLoad() {
        super.viewDidLoad()

        setupCamera()
        if (showPreview) {
            view.backgroundColor = UIColor.blackColor
            setupPreviewView()
            setupPreviewLayer()
        }
        dispatch_async(dispatch_get_global_queue(0L, 0UL)) {
            captureSession.startRunning()
            println("Capture session started.")
        }
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()

        // Ensure proper resizing after layout updates.
        updatePreviewLayerFrame()
    }

    override fun viewWillDisappear(animated: Boolean) {
        super.viewWillDisappear(animated)

        // Clean up rotation notification listener.
        NSNotificationCenter.defaultCenter.removeObserver(this)
        UIDevice.currentDevice().endGeneratingDeviceOrientationNotifications()
    }

    /** Callback for [OrientationListener]. */
    @OptIn(BetaInteropApi::class)
    @ObjCAction
    fun handleDeviceOrientationDidChange() {
        println("handleDeviceOrientationDidChange.")
        // Directly update preview orientation when the device rotates.
        updatePreviewLayerFrame()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun setupCamera() {
        captureSession.beginConfiguration()

        val camera = when (cameraSelector) {
            CameraSelector.DEFAULT_FRONT_CAMERA -> {
                AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
                    listOf(AVCaptureDeviceTypeBuiltInWideAngleCamera),
                    mediaType = AVMediaTypeVideo,
                    position = AVCaptureDevicePositionFront
                ).devices.firstOrNull()
            }

            CameraSelector.DEFAULT_BACK_CAMERA -> {
                AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
                    listOf(AVCaptureDeviceTypeBuiltInWideAngleCamera),
                    mediaType = AVMediaTypeVideo,
                    position = AVCaptureDevicePositionBack
                ).devices.firstOrNull()
            }
        } ?: return

        println("Camera found: $camera.")

        val videoInput = AVCaptureDeviceInput(device = camera as AVCaptureDevice, error = null)
        if (captureSession.canAddInput(videoInput)) {
            captureSession.addInput(videoInput)
            println("Capture session input added.")
        } else {
            Logger.e(TAG, "Failed to add input to the capture session.")
            throw IllegalStateException("Failed to add input to the capture session.")
        }

        val videoOutput = AVCaptureVideoDataOutput().apply {
            videoSettings = mapOf(AVVideoCodecKey to AVVideoCodecTypeJPEG)
            alwaysDiscardsLateVideoFrames = true // Improves performance.
        }

        if (captureSession.canAddOutput(videoOutput)) {
            captureSession.addOutput(videoOutput)
        }

        captureSession.commitConfiguration()
    }

    private fun setupPreviewView() {
        view.addSubview(previewView)
        previewView.translatesAutoresizingMaskIntoConstraints = false

        NSLayoutConstraint.activateConstraints(
            listOf(
                previewView.leadingAnchor.constraintEqualToAnchor(view.leadingAnchor),
                previewView.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor),
                previewView.topAnchor.constraintEqualToAnchor(view.topAnchor),
                previewView.bottomAnchor.constraintEqualToAnchor(view.bottomAnchor)
            )
        )
    }

    private fun setupPreviewLayer() {
        previewLayer = AVCaptureVideoPreviewLayer(session = captureSession).apply {
            videoGravity = AVLayerVideoGravityResizeAspectFill // Removes black bars.
        }
        previewLayer?.let {
            previewView.layer.addSublayer(it)
            updatePreviewLayerFrame()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun updatePreviewLayerFrame() {
        // Align the preview layer to the preview view's bounds and adjust orientation.
        previewLayer?.frame = previewView.bounds
        val newOrientation = getVideoOrientationForDevice()
        if (newOrientation != doNotChange) previewLayer?.connection?.videoOrientation = newOrientation
    }

    /**
     * Converts the phone screen physical orientation to the appropriate AVCaptureVideoOrientation.
     *
     * @return The corresponding AVCaptureVideoOrientation or null if the device moved close to the horizontal plane
     *     to help avoid the unexpected UX.
     */
    private fun getVideoOrientationForDevice(): AVCaptureVideoOrientation {
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
}