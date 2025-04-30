package org.multipaz.compose.camera

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
actual fun Camera(
    modifier: Modifier,
    cameraSelection: CameraSelection,
    captureResolution: CameraCaptureResolution,
    showCameraPreview: Boolean,
    onFrameCaptured: suspend (frame: CameraFrame) -> ImageBitmap?
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val previewView = remember { PreviewView(context) }

    DisposableEffect(cameraSelection) {
        val cameraProvider = cameraProviderFuture.get()

        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        val previewCase = Preview.Builder().build()
        previewCase.setSurfaceProvider(previewView.surfaceProvider)

        val resolutionStrategy = ResolutionStrategy(
            Size(captureResolution.width, captureResolution.height),
            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER
        )

        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(resolutionStrategy)
            .build()

        val analysisCase = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()

        analysisCase.setAnalyzer(executor) { imageProxy ->
            coroutineScope.launch(Dispatchers.IO) {
                val frame = imageProxy.toCameraFrame()
                onFrameCaptured(frame)
                imageProxy.close()
            }
        }

        cameraProvider.unbindAll()
        val useCases = mutableListOf<UseCase>(analysisCase)
        if (showCameraPreview) useCases.add(previewCase)
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelection.toAndroidCameraSelector(),
            *useCases.toTypedArray()
        )

        onDispose {
            cameraProvider.unbindAll()
            executor.shutdown()
        }
    }
    if (showCameraPreview) {
        Box(modifier = modifier.onSizeChanged { newSize -> viewSize = newSize }) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Common to Native camera selector. */
private fun CameraSelection.toAndroidCameraSelector() =
    when (this) {
        CameraSelection.DEFAULT_BACK_CAMERA -> CameraSelector.DEFAULT_BACK_CAMERA
        CameraSelection.DEFAULT_FRONT_CAMERA -> CameraSelector.DEFAULT_FRONT_CAMERA
        else -> throw IllegalArgumentException("Unsupported camera selector: $this")
    }

/** Native to common image data. */
private fun ImageProxy.toCameraFrame(): CameraFrame {
    val bitmap = this.toBitmap()
    return CameraFrame(bitmap.asImageBitmap(), width, height, format)
}