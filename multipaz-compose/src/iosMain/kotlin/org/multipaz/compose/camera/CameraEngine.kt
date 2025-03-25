package org.multipaz.compose.camera

import kotlinx.cinterop.ExperimentalForeignApi
import org.multipaz.compose.camera.CameraPlugin
import org.multipaz.compose.camera.CameraSelection
import org.multipaz.util.toByteArray
import platform.UIKit.UIViewController
import org.multipaz.util.Logger
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVMetadataObjectType
import platform.darwin.dispatch_get_main_queue

/** iOS platform-specific implementation of [CameraEngine]. */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class CameraEngine(
    var cameraSelection: CameraSelection,
    val showPreview: Boolean,
    internal var plugins: MutableList<CameraPlugin>
) : UIViewController(nibName = null, bundle = null) {
    private val TAG = "CameraEngine"
    private val cameraController = CameraViewController()
    private var imageCaptureListeners = mutableListOf<(ByteArray) -> Unit>()
    private var metadataOutput = AVCaptureMetadataOutput()
    private var metadataObjectsDelegate: AVCaptureMetadataOutputObjectsDelegateProtocol? = null

    override fun viewDidLoad() {
        super.viewDidLoad()
        setupCamera()
    }

    fun getCameraPreviewLayer() = cameraController.previewLayer

    internal fun currentVideoOrientation() = cameraController.currentVideoOrientation()

    private fun setupCamera() {
        with (cameraController) {
            setupSession(cameraSelection)
            setupPreview(view)

            // Native face detection.
            if (cameraController.captureSession?.canAddOutput(metadataOutput) == true) {
                cameraController.captureSession?.addOutput(metadataOutput)
            }

            startSession()

            // Placeholder (not used yet).
            onFrameCapture = { image ->
                image?.let {
                    val data = it.toByteArray()
                    imageCaptureListeners.forEach { it(data) }
                }
            }

            onError = { error ->
                Logger.d(TAG, "CameraEngine Error: $error")
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        cameraController.previewLayer?.setFrame(view.bounds)
    }

    actual fun startSession() {
        cameraController.startSession()

        initializePlugins()
    }

    actual fun stopSession() {
        cameraController.stopSession()
    }

    actual fun initializePlugins() {
        plugins.forEach {
            it.initialize(this)
        }
    }

    actual fun getAspectRatio() : Double {
        return 3.0/4
    }

    fun setMetadaObjectsDelegate(delegate: AVCaptureMetadataOutputObjectsDelegateProtocol) {
        metadataObjectsDelegate = delegate
        metadataOutput.setMetadataObjectsDelegate(delegate, dispatch_get_main_queue())
    }

    fun setMetadataObjectTypes(newTypes: List<AVMetadataObjectType?>) {
        if (cameraController.captureSession?.isRunning() == true) {
            metadataOutput.metadataObjectTypes += newTypes
        } else {
            println("Camera session is not running.")
        }
    }
}