package com.android.identity_credential.mrtd

import android.os.Handler
import android.util.Log
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.guava.await
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * OCR scanner that extracts [MrtdMrzData] from camera feed.
 *
 * TODO: hook cancellation
 */
class MrtdMrzScanner(private val mActivity: ComponentActivity) {
    companion object {
        private const val TAG = "MrtdMrzScanner"
    }

    /**
     * Starts camera and scans camera feed until it finds passport/ID image that contains valid
     * data.
     */
    suspend fun readFromCamera(surfaceProvider: Preview.SurfaceProvider): MrtdMrzData {
        val provider = ProcessCameraProvider.getInstance(mActivity).await()
        return onCameraReady(provider, surfaceProvider)
    }

    private suspend fun onCameraReady(
        cameraProvider: ProcessCameraProvider, surfaceProvider: Preview.SurfaceProvider
    ): MrtdMrzData {
        val mainHandler = Handler(mActivity.mainLooper)

        // Preview
        val previewUseCase = Preview.Builder().build()
        previewUseCase.setSurfaceProvider(surfaceProvider)

        val analysisBuilder = ImageAnalysis.Builder()
        // we take the portrait format of the Image.
        analysisBuilder.setTargetResolution(Size(4 * 480, 4 * 640))
        val analysisUseCase = analysisBuilder.build()
        val executor = Executors.newFixedThreadPool(1)!!
        return suspendCoroutine { continuation ->
            analysisUseCase.setAnalyzer(
                executor
            ) { image ->
                try {
                    recognize(image) { pictureData ->
                        if (!executor.isShutdown) {
                            executor.shutdown()
                            cameraProvider.unbindAll()
                            mainHandler.post {
                                continuation.resume(pictureData)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!executor.isShutdown) {
                        executor.shutdown()
                        mainHandler.post {
                            continuation.resumeWithException(e)
                        }
                    }
                }
            }

            // Unbind use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera
            cameraProvider.bindToLifecycle(
                mActivity, CameraSelector.DEFAULT_BACK_CAMERA, previewUseCase, analysisUseCase
            )
        }
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun recognize(imageProxy: ImageProxy, dataCb: (keyData: MrtdMrzData) -> Unit) {
        val mediaImage = imageProxy.image ?: return
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image).addOnSuccessListener { visionText ->
                imageProxy.close()
                val newValue = extractMrtdMrzData(visionText.text)
                if (newValue != null) {
                    dataCb(newValue)
                }
            }.addOnFailureListener { err ->
                Log.e(TAG, "Error scanning: $err")
                imageProxy.close()
            }
    }
}
