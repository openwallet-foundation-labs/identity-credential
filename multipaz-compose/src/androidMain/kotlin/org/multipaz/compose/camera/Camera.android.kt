package org.multipaz.compose.camera

import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.setFrom
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

private const val TAG = "Camera"

@OptIn(TransformExperimental::class)
@Composable
actual fun Camera(
    modifier: Modifier,
    cameraSelection: CameraSelection,
    captureResolution: CameraCaptureResolution,
    showCameraPreview: Boolean,
    onFrameCaptured: suspend (frame: CameraFrame) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { mutableStateOf<PreviewView?>(if (showCameraPreview) PreviewView(context) else null) }

    DisposableEffect(cameraSelection) {
        var preview: Preview? = null
        if (showCameraPreview) {
            preview = Preview.Builder().build()
            // COMPATIBLE is needed b/c we're using `outputTransform.matrix`
            previewView.value!!.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            preview.setSurfaceProvider(previewView.value!!.surfaceProvider)
        }

        val cameraProvider = cameraProviderFuture.get()

        val resolutionStrategy = ResolutionStrategy(
            captureResolution.getDimensions(),
            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER
        )
        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(resolutionStrategy)
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()

        imageAnalysis.setAnalyzer(executor) { imageProxy ->
            runBlocking {
                val transformationProxy = if (showCameraPreview) {
                    withContext(Dispatchers.Main) {
                        getCorrectionMatrix(imageProxy, previewView.value!!)
                    }
                } else {
                    Matrix()
                }
                val frame = CameraFrame(
                    cameraImage = CameraImage(imageProxy),
                    width = imageProxy.width,
                    height = imageProxy.height,
                    previewTransformation = transformationProxy
                )
                onFrameCaptured(frame)
            }
            imageProxy.close()
        }

        cameraProviderFuture.get().unbindAll()
        if (showCameraPreview) {
            cameraProviderFuture.get().bindToLifecycle(
                lifecycleOwner,
                cameraSelection.toAndroidCameraSelector(),
                preview,
                imageAnalysis
            )
        } else {
            cameraProviderFuture.get().bindToLifecycle(
                lifecycleOwner,
                cameraSelection.toAndroidCameraSelector(),
                imageAnalysis
            )
        }

        onDispose {
            cameraProvider.unbindAll()
            executor.shutdown()
        }
    }

    if (showCameraPreview) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                previewView.value!!
            }
        )
    }
}

private fun CameraCaptureResolution.getDimensions(): Size {
    return when (this) {
        CameraCaptureResolution.LOW -> Size(640, 480)
        CameraCaptureResolution.MEDIUM -> Size(1280, 720)
        CameraCaptureResolution.HIGH -> Size(1920, 1080)
    }
}

private fun CameraSelection.toAndroidCameraSelector() =
    when (this) {
        CameraSelection.DEFAULT_BACK_CAMERA -> CameraSelector.DEFAULT_BACK_CAMERA
        CameraSelection.DEFAULT_FRONT_CAMERA -> CameraSelector.DEFAULT_FRONT_CAMERA
    }

@OptIn(TransformExperimental::class)
private fun getCorrectionMatrix(imageProxy: ImageProxy, previewView: PreviewView) : Matrix {

    // This matrix maps (-1, -1) -> (1, 1) space to preview-coordinate system. This includes
    // any scaling, rotation, or cropping that's done in the preview.
    val matrix = previewView.outputTransform!!.matrix
    val composeMatrix = Matrix()
    composeMatrix.setFrom(matrix)

    // By the scale and translate below, we modify the matrix so it maps
    // (0, 0) -> (width, height) of the frame to analyze to the preview
    // coordinate system
    composeMatrix.scale(2f/imageProxy.width, 2f/imageProxy.height, 1f)
    composeMatrix.translate(-0.5f*imageProxy.width, -0.5f*imageProxy.height, 1.0f)

    return composeMatrix
}
