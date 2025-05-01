package org.multipaz.compose.camera

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

@Composable
actual fun Camera(
    modifier: Modifier,
    cameraSelection: CameraSelection,
    captureResolution: CameraCaptureResolution,
    showCameraPreview: Boolean,
    onFrameCaptured: suspend (frame: ImageBitmap) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(imageBitmap) {
        imageBitmap?.let {
            onFrameCaptured(it)
        }
    }

    DisposableEffect(cameraSelection) {
        val cameraProvider = cameraProviderFuture.get()

        val resolutionStrategy = ResolutionStrategy(
            captureResolution.getDimensions(),
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
            coroutineScope.launch {
                val bitmap = imageProxy.toBitmap()
                withContext(Dispatchers.Main) {
                    imageBitmap = bitmap.asImageBitmap()
                    imageProxy.close()
                }
            }
        }

        cameraProvider.unbindAll()
        val useCases = mutableListOf<UseCase>(analysisCase)
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

    imageBitmap?.let {
        Image(
            modifier = modifier,
            bitmap = processCameraBitmap(it),
            contentDescription = "Camera frame",
            contentScale = ContentScale.FillWidth
        )
    }
}

/** Common to Native camera selector. */
private fun CameraSelection.toAndroidCameraSelector() =
    when (this) {
        CameraSelection.DEFAULT_BACK_CAMERA -> CameraSelector.DEFAULT_BACK_CAMERA
        CameraSelection.DEFAULT_FRONT_CAMERA -> CameraSelector.DEFAULT_FRONT_CAMERA
    }

/** Define frame dimensions used for resolution grades. */
private fun CameraCaptureResolution.getDimensions(): Size {
    return when (this) {
        CameraCaptureResolution.LOW -> Size(640, 480)
        CameraCaptureResolution.MEDIUM -> Size(1280, 720)
        CameraCaptureResolution.HIGH -> Size(1920, 1080)
    }
}