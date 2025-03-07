package org.multipaz_credential.wallet.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.MirrorMode.MIRROR_MODE_ON_FRONT_ONLY
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import org.multipaz.issuance.evidence.EvidenceRequestSelfieVideo
import org.multipaz.util.Logger
import org.multipaz_credential.wallet.FaceImageClassifier
import kotlinx.coroutines.guava.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat


/**
 * Video recorder to record a selfie video for identity verification.
 */
class SelfieRecorder(
    private val lifecycleOwner: LifecycleOwner,
    private val context: Context,
    private val onRecordingStarted: () -> Unit,
    private val onFinished: (ByteArray) -> Unit,
    private val onStateChange: (FaceImageClassifier.RecognitionState, EvidenceRequestSelfieVideo.Poses?) -> Unit
) {
    companion object {
        private const val TAG = "SelfieRecorder"
        private const val FILENAME_TIME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val FILENAME_FORMAT = "VerificationSelfie-\$datetime.mp4"
    }

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var videoCapture: VideoCapture<Recorder>
    private var recording: Recording? = null
    private var savedFrontImage: ByteArray? = null
    var faceClassifier: FaceImageClassifier? = null

    /**
     * Starts camera and prepares to record and return a video.
     */
    suspend fun launchCamera(surfaceProvider: Preview.SurfaceProvider) {
        if (::cameraProvider.isInitialized) {
            throw IllegalStateException("Camera already started.")
        }
        cameraProvider = ProcessCameraProvider.getInstance(context).await()

        // Configure preview, so the user can see what they're recording:
        val previewUseCase = Preview.Builder().build()
        previewUseCase.setSurfaceProvider(surfaceProvider)

        // Configure video recording:
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST,
                FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
            .build()
        videoCapture = VideoCapture.Builder(recorder)
            .setMirrorMode(MIRROR_MODE_ON_FRONT_ONLY)
            .build()

        // Configure face classifier:
        faceClassifier = FaceImageClassifier({ recognitionState, pose ->
            if (recognitionState == FaceImageClassifier.RecognitionState.POSE_RECOGNIZED &&
                pose == EvidenceRequestSelfieVideo.Poses.FRONT) {
                // The user is looking directly toward the camera. Save this image so we can send
                // it to the issuer.
                saveFrontImage()
            }
            onStateChange(recognitionState, pose)
        }, context)

        // Unbind any existing use cases and bind our own:
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA,
            previewUseCase, videoCapture, faceClassifier!!.analysisUseCase
        )
    }

    /**
     * Returns a [MediaStoreOutputOptions] for recording a selfie video and saving it to a file
     * in the MediaStore.
     */
    private fun getRecordingOptions(): FileOutputOptions {
        // Save the recording to a local file while it's being created. It'll be uploaded once it's
        // done.
        val fileName = FILENAME_FORMAT.replace(
            "\$datetime",
            SimpleDateFormat(FILENAME_TIME_FORMAT)
                .format(System.currentTimeMillis()))
        val privateDirectory = context.filesDir
        val outputFile = File(privateDirectory, fileName)
        Logger.i(TAG, "Saving selfie video to ${outputFile.absolutePath}")

        return FileOutputOptions.Builder(outputFile)
            .setFileSizeLimit(256 * 1024 * 1024)
            .build()
    }

    /**
     * Starts video recording.
     */
    fun startRecording() {
        if (!::cameraProvider.isInitialized || !::videoCapture.isInitialized) {
            throw IllegalStateException("Recording requested when camera has not been started.")
        }
        if (recording != null) {
            // The UI flow shouldn't allow multiple recordings at once.
            throw IllegalStateException("Recording already in progress.")
        }

        recording = videoCapture.output
            .prepareRecording(context, getRecordingOptions())
            .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> onRecordingStarted()
                    is VideoRecordEvent.Finalize -> {
                        recording?.close()
                        recording = null
                        if (recordEvent.hasError()) {
                            Logger.e(TAG, "Selfie failed to record: ${recordEvent.error}")
                            onFinished(ByteArray(0))
                        } else {
                            Logger.i(
                                TAG,
                                "Selfie recorded: ${recordEvent.outputResults.outputUri}"
                            )
                            // The current implementation isn't using the saved video file. Delete
                            // it. An alternate implementation could send the video file instead
                            // if the front-facing selfie image, if the issuer wants to review the
                            // full video instead of a single image.
                            Logger.i(TAG, "Deleting file ${recordEvent.outputResults.outputUri}")
                            File(recordEvent.outputResults.outputUri.path!!).delete()

                            val selfieImage = savedFrontImage ?: run {
                                Logger.e(
                                    TAG,
                                    "Selfie recording finished without saving a front image.")
                                ByteArray(0)
                            }
                            onFinished(selfieImage)
                        }
                    }
                }
            }
    }

    /**
     * Stops the recording and calls the completion handler.
     */
    fun finish() {
        if (recording == null) {
            // The UI flow shouldn't allow finishing the recording if it hasn't started.
            throw IllegalStateException("Can't stop recording if the recording hasn't started.")
        }
        recording?.stop()
        recording = null

        cameraProvider.unbindAll()
    }

    /**
     * Saves a snapshot of the current camera image to memory.
     */
    private fun saveFrontImage() {
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
            val stream = ByteArrayOutputStream()
            val bitmap = imageProxy.toBitmap()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            savedFrontImage = stream.toByteArray()
            Logger.i(TAG, "Saved selfie image from front pose.")

            imageProxy.close()
            imageAnalysis.clearAnalyzer()
        }

        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA,
            imageAnalysis)
    }

}
