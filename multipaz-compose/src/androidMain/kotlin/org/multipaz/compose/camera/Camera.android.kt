package org.multipaz.compose.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.multipaz.util.Logger
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "Camera"

@Composable
actual fun Camera(
    modifier: Modifier,
    cameraSelection: CameraSelection,
    captureResolution: CameraCaptureResolution,
    showCameraPreview: Boolean,
    onFrameCaptured: suspend (frame: CameraFrame)  -> ImageBitmap?
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var latestFrame by remember { mutableStateOf<CameraFrame?>(null) }
    var overlayBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val canProcessFrame = remember { AtomicBoolean(true) }

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
            // Only proceed if we are not currently processing a frame
            if (canProcessFrame.compareAndSet(true, false)) {
                coroutineScope.launch { // Launch a coroutine for processing
                    try {
                        val bitmap = imageProxy.toBitmap() // Should be on a background thread
                        val currentFrame = CameraFrame(
                            ImageData(bitmap.asImageBitmap()),
                            bitmap.width,
                            bitmap.height,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        if (showCameraPreview) {
                            withContext(Dispatchers.Main) {
                                latestFrame = currentFrame
                            }
                        }
                        // Call the potentially long-running onFrameCaptured on IO thread
                        val resultOverlay = withContext(Dispatchers.Default) {
                            onFrameCaptured(currentFrame)
                        }

                        // Update the overlay bitmap on the main thread
                        withContext(Dispatchers.Main) {
                            overlayBitmap = resultOverlay
                        }

                    } catch (e: Exception) {
                        Logger.e(TAG, "Error processing frame: ${e.localizedMessage}", e)
                    } finally {
                        imageProxy.close()
                        canProcessFrame.set(true)
                    }
                }
            } else {
                // If busy, just close the imageProxy and drop the frame
                imageProxy.close()
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

    if (showCameraPreview) {
        if (overlayBitmap?.width != latestFrame?.width || overlayBitmap?.height != latestFrame?.height) {
            Logger.w(TAG, "Supplied overlay bitmap has the wrong size for the preview " +
                    "(${overlayBitmap?.width}x${overlayBitmap?.height}.")
            overlayBitmap = null // Discard the overlay bitmap.
        }
        latestFrame?.let {
            Image(
                modifier = modifier,
                bitmap = processCameraBitmap(it, overlayBitmap, cameraSelection.isMirrored()),
                contentDescription = "Camera frame",
                contentScale = ContentScale.FillWidth
            )
        }
    }
    else {
        if (overlayBitmap != null) {
            Logger.w(TAG, "Camera received the frame overlay data, but no preview is requested.")
        }
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

/** Demo circles. Portrait mode only. */
private fun processCameraBitmap(cameraBitmap: CameraFrame, overlayBitmap: ImageBitmap?, isMirrored: Boolean)
        : ImageBitmap {
    if (cameraBitmap.imageData == null) return createBitmap(1, 1).asImageBitmap()

    val bitmap = cameraBitmap.imageData.imageBitmap.asAndroidBitmap()
    val mutableBaseBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutableBaseBitmap)
    canvas.density = bitmap.density

    if (overlayBitmap != null) {
        canvas.drawBitmap(overlayBitmap.asAndroidBitmap(), 0f, 0f, Paint())
    }

    // Rotate to portrait.
    val matrix = Matrix()
    matrix.postRotate(cameraBitmap.rotation.toFloat()) //  For device portrait mode only, landscape mode not tracked.
    if (isMirrored) matrix.postScale(-1f, 1f)
    val rotatedBitmap = Bitmap.createBitmap(mutableBaseBitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

    return rotatedBitmap.asImageBitmap()
}