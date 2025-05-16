package org.multipaz.compose.camera

import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.setFrom
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val localContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember {
        ProcessCameraProvider.getInstance(localContext)
    }

    val coroutineScope = rememberCoroutineScope()

    var latestFrame by remember { mutableStateOf<CameraFrame?>(null) }
    LaunchedEffect(latestFrame) {
        latestFrame?.let {
            withContext(Dispatchers.IO) {
                onFrameCaptured(it)
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val previewView = PreviewView(context)
            val preview = Preview.Builder().build()
            // COMPATIBLE is needed b/c we're using `outputTransform.matrix`
            previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            preview.setSurfaceProvider(previewView.surfaceProvider)

            val imageAnalysis = ImageAnalysis.Builder().build()
            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                coroutineScope.launch {
                    val bitmap = imageProxy.toBitmap()
                    withContext(Dispatchers.Main) {
                        latestFrame = CameraFrame(
                            imageData = ImageData(bitmap.asImageBitmap()),
                            width = bitmap.width,
                            height = bitmap.height,
                            transformation = getCorrectionMatrix(imageProxy, previewView)
                        )
                        imageProxy.close()
                    }
                }
            }

            cameraProviderFuture.get().unbindAll()
            cameraProviderFuture.get().bindToLifecycle(
                lifecycleOwner,
                cameraSelection.toAndroidCameraSelector(),
                preview,
                imageAnalysis
            )
            previewView
        }
    )
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
