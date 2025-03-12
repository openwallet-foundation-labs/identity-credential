package org.multipaz.compose.camera

import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.CameraSelector as XCameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.multipaz.context.applicationContext
import org.multipaz.util.Logger
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
actual fun Camera(
    cameraSelector: CameraSelector,
    modifier: Modifier
) {
    val TAG = "Camera"
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val preview = Preview.Builder().build()
    val previewView = remember {
        PreviewView(context)
    }

    val imageAnalysis = ImageAnalysis.Builder()
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
    imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(applicationContext)) { imageProxy ->
        Logger.d(TAG, "Got image ${imageProxy.width} ${imageProxy.height} ${imageProxy.imageInfo}")
        imageProxy.close()
    }

    val useCases = mutableListOf<UseCase>()
    useCases.add(preview)
    useCases.add(imageAnalysis)

    val cameraSelection = XCameraSelector.Builder()
        .apply {
            when (cameraSelector) {
                CameraSelector.DEFAULT_FRONT_CAMERA -> requireLensFacing(XCameraSelector.LENS_FACING_FRONT)
                CameraSelector.DEFAULT_BACK_CAMERA -> requireLensFacing(XCameraSelector.LENS_FACING_BACK)
            }
        }
        .build()
    LaunchedEffect(cameraSelection) {
        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelection,
            *useCases.toTypedArray()
        )
        preview.setSurfaceProvider(previewView.surfaceProvider)
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

//TODO: See if it could be possible to request the instance simply by `ProcessCameraProvider.getInstance(this).await()`
private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { cameraProvider ->
            cameraProvider.addListener({
                continuation.resume(cameraProvider.get())
            }, ContextCompat.getMainExecutor(this))
        }
    }