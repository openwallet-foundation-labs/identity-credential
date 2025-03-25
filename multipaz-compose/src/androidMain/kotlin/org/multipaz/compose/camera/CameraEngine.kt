package org.multipaz.compose.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.core.content.ContextCompat
import org.multipaz.compose.camera.CameraExceptionInvalidConfiguration
import org.multipaz.compose.camera.CameraPlugin
import org.multipaz.compose.camera.CameraSelection
import org.multipaz.util.Logger

/**
 * Android platform implementation of [Camera] using CameraX.
 *
 * @param context The Android [Context].
 * @param lifecycleOwner The [LifecycleOwner] to bind the camera lifecycle.
 * @param cameraSelection The desired Camera lens.
 * @param plugins The list of custom camera plugins.
 * @param showPreview Whether to show the live camera preview.
 * @param showFrame Whether to show the captured camera frame.
 */
actual class CameraEngine(
    val context: Context,
    val lifecycleOwner: LifecycleOwner,
    internal var cameraSelection: CameraSelection,
    internal var plugins: MutableList<CameraPlugin>,
    val showPreview: Boolean
) {
    private val TAG = "CameraEngine"
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var camera: Camera? = null
    private var previewView: PreviewView? = null
    private var sensorSize = Size(1, 1)
    private var cameraPreviewAspectRatio: Double = 1.0
    var imageCapture: ImageCapture? = null
    var imageAnalyzer: ImageAnalysis? = null

    private val imageCaptureListeners = mutableListOf<(ByteArray) -> Unit>()

    actual fun startSession() {
        // CameraX handles session start based on lifecycle
    }

    actual fun stopSession() {
        cameraProvider?.unbindAll()
    }

    /**
     * Initialize/reset camera plugins (i.e.face recognition, face matcher, bar code scanner).
     */
    actual fun initializePlugins() {
        plugins.forEach { it.initialize(this) }
    }

    actual fun getAspectRatio(): Double {
        Logger.d(TAG, "CameraEngine preview aspect ratio: $cameraPreviewAspectRatio")
        return cameraPreviewAspectRatio
    }

    fun bindCamera(previewView: PreviewView?, onCameraReady: () -> Unit = {}) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                cameraProvider?.unbindAll()
                val cameraId = cameraSelection.toCameraXLensFacing()
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(cameraId)
                    .build()

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setResolutionSelector(getResolutionSelector())
                    .build()

                this.previewView = previewView // Nullable
                preview = previewView?.let {
                    Preview.Builder()
                        .setResolutionSelector(getResolutionSelector())
                        .build()
                        .also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                }

                // Setup ImageAnalysis Use Case only if needed
                val useCases = mutableListOf<UseCase>()
                preview?.let { useCases.add(it) }
                imageAnalyzer?.let { useCases.add(it) }
                imageCapture?.let { useCases.add(it) }

                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    *useCases.toTypedArray()
                )

                saveSensorSize()

                onCameraReady()

            } catch (exc: Exception) {
                Logger.d(TAG, "Use case binding failed: ${exc.message}")
            }

        }, ContextCompat.getMainExecutor(context))
    }

    private fun getResolutionSelector(): ResolutionSelector {
        return ResolutionSelector.Builder()
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
    }

    fun updateImageAnalyzer() {
        camera?.let {
            cameraProvider?.unbindAll()

            val useCases = mutableListOf<UseCase>()
            preview?.let { useCases.add(it) }
            imageAnalyzer?.let { useCases.add(it) }
            imageCapture?.let { useCases.add(it) }

            camera = cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.Builder().requireLensFacing(cameraSelection.toCameraXLensFacing()).build(),
                *useCases.toTypedArray()
            )
        } ?: throw CameraExceptionInvalidConfiguration("CameraEngine not initialized.")
    }

    /** Figure selected camera frame dimensions in pixels. */
    private fun saveSensorSize() {
        val cameraId = cameraSelection.toCameraXLensFacing()
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        sensorSize = cameraManager.cameraIdList.find { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == cameraId
        }?.let { cameraName ->
            cameraManager.getCameraCharacteristics(cameraName)
                .get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        }!!
        cameraPreviewAspectRatio = sensorSize.height.toDouble() / sensorSize.width.toDouble()
        Logger.d(TAG, "CameraEngine preview aspect ratio: $cameraPreviewAspectRatio")
    }

    /** Internal camera id to device (CameraX) camera Id. */
    private fun CameraSelection.toCameraXLensFacing(): Int =
        when (this) {
            CameraSelection.DEFAULT_FRONT_CAMERA -> CameraSelector.LENS_FACING_FRONT
            CameraSelection.DEFAULT_BACK_CAMERA -> CameraSelector.LENS_FACING_BACK
            else -> throw CameraExceptionInvalidConfiguration("Invalid camera selection: $this")
        }
}
