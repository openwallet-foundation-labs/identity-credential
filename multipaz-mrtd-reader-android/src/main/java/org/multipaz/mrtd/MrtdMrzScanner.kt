package org.multipaz.mrtd

import android.os.Handler
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
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
 * OCR scanner that extracts [MrtdAccessDataMrz] from camera feed.
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
    suspend fun readFromCamera(surfaceProvider: Preview.SurfaceProvider): MrtdAccessDataMrz {
        val provider = ProcessCameraProvider.getInstance(mActivity).await()
        return onCameraReady(provider, surfaceProvider)
    }

    private suspend fun onCameraReady(
        cameraProvider: ProcessCameraProvider, surfaceProvider: Preview.SurfaceProvider
    ): MrtdAccessDataMrz {
        val mainHandler = Handler(mActivity.mainLooper)

        // Preview
        val previewUseCase = Preview.Builder().build()
        previewUseCase.setSurfaceProvider(surfaceProvider)

        // we take the portrait format of the Image.
        val analysisUseCase = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(4 * 480, 4 * 640),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()
            )
            .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
            .build()
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
    private fun recognize(imageProxy: ImageProxy, dataCb: (keyData: MrtdAccessDataMrz) -> Unit) {
        val mediaImage = imageProxy.image ?: return
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image).addOnSuccessListener { visionText ->
                imageProxy.close()
                val value = extractMrtdMrzData(visionText.text)
                if (value != null) {
                    mrtdLogI(TAG, "MRZ scanned successfully")
                    dataCb(value)
                }
            }.addOnFailureListener { err ->
                mrtdLogE(TAG, "Error MRZ scanning", err)
                imageProxy.close()
            }
    }
}
