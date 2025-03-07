package org.multipaz_credential.wallet

import android.content.Context
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionFilter
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.core.content.ContextCompat
import org.multipaz.issuance.evidence.EvidenceRequestSelfieVideo
import org.multipaz.util.Logger
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.abs

class FaceImageClassifier(private val onStateChange: (RecognitionState, EvidenceRequestSelfieVideo.Poses?) -> Unit,
    private val context: Context) : ImageAnalysis.Analyzer {
    private val detector: FaceDetector
    val analysisUseCase: ImageAnalysis
    private var expectedPose: EvidenceRequestSelfieVideo.Poses? = null

    enum class RecognitionState {
        NO_POSE_SELECTED,
        WORKING,
        TOO_MANY_FACES,
        POSE_RECOGNIZED
    }
    private var recognitionState = RecognitionState.NO_POSE_SELECTED

    companion object {
        const val TAG = "FaceImageClassifier"
        const val DEFAULT_REQUESTED_CAMERA_PREVIEW_WIDTH: Int = 480
        const val DEFAULT_REQUESTED_CAMERA_PREVIEW_HEIGHT: Int = 360
    }

    init {
        val options = FaceDetectorOptions.Builder()
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .enableTracking()
                .build()
        detector = FaceDetection.getClient(options)

        val targetResolution = Size(DEFAULT_REQUESTED_CAMERA_PREVIEW_WIDTH, DEFAULT_REQUESTED_CAMERA_PREVIEW_HEIGHT)
        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionFilter(SizedResolutionFilter(targetResolution))
            .build()
        analysisUseCase = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
        analysisUseCase.setAnalyzer(ContextCompat.getMainExecutor(context), this)
        Logger.i(TAG, "Created FaceImageClassifier")
    }

    fun setExpectedPose(pose: EvidenceRequestSelfieVideo.Poses?) {
        expectedPose = pose
        setRecognitionState(
            if (pose == null) RecognitionState.NO_POSE_SELECTED
            else RecognitionState.WORKING
        )
    }

    private fun setRecognitionState(state: RecognitionState) {
        if (recognitionState != state) {
            recognitionState = state
            Logger.d(TAG, "Recognition state changed to $state")
            onStateChange(state, expectedPose)
        }
    }

    private fun checkFace(face: Face, pose: EvidenceRequestSelfieVideo.Poses): Boolean {
        Logger.d(TAG, "Checking face, pose $pose, smiling ${face.smilingProbability}, angle X ${face.headEulerAngleX}, angle Y ${face.headEulerAngleY}, angle Z ${face.headEulerAngleZ}")
        return when (pose) {
            EvidenceRequestSelfieVideo.Poses.FRONT -> {
                abs(face.headEulerAngleX) < 15 &&
                        abs(face.headEulerAngleY) < 15 &&
                        abs(face.headEulerAngleZ) < 15
            }
            EvidenceRequestSelfieVideo.Poses.SMILE -> {
                (face.smilingProbability ?: 0.0f) > 0.85f
            }
            EvidenceRequestSelfieVideo.Poses.TILT_HEAD_UP -> {
                face.headEulerAngleX > 35 &&
                        abs(face.headEulerAngleY) < 15 &&
                        abs(face.headEulerAngleZ) < 15

            }
            EvidenceRequestSelfieVideo.Poses.TILT_HEAD_DOWN -> {
                face.headEulerAngleX < -20 &&
                        abs(face.headEulerAngleY) < 15 &&
                        abs(face.headEulerAngleZ) < 15
            }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (expectedPose == null) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            Logger.d(TAG, "Analyzing image, size ${image.width}x${image.height}")
            detector.process(image)
                .addOnSuccessListener { faces ->
                    try {
                        if (faces.isNotEmpty()) {
                            if (faces.size > 1) {
                                // Too many faces in the image. Only process the image if there's
                                // only one.
                                setRecognitionState(RecognitionState.TOO_MANY_FACES)
                                return@addOnSuccessListener
                            }

                            val face = faces[0]
                            val pose = expectedPose?: return@addOnSuccessListener
                            if (checkFace(face, pose)) {
                                setRecognitionState(RecognitionState.POSE_RECOGNIZED)
                            } else {
                                setRecognitionState(RecognitionState.WORKING)
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e(TAG, "Error analyzing image", e)
                    } finally {
                        imageProxy.close()
                    }
                }
                .addOnFailureListener { e ->
                    throw e
                }
        }
    }
}

class SizedResolutionFilter(private val targetResolution: Size): ResolutionFilter {
    override fun filter(supportedSizes: List<Size>, rotationDegrees: Int): List<Size> {
        if (supportedSizes.isEmpty()) {
            return supportedSizes
        }

        fun linearDifference(size1: Size, size2: Size): Int {
            return abs(size1.width - size2.width) + abs(size1.height - size2.height)
        }
        var bestLinearDifference = linearDifference(targetResolution, supportedSizes[0])
        var bestResolution = supportedSizes[0]
        for (size in supportedSizes) {
            val currentLinearDifference = linearDifference(targetResolution, size)
            Logger.d("SizedResolutionFilter",
                "Available image size $size, linear difference $currentLinearDifference")
            if (currentLinearDifference < bestLinearDifference) {
                bestLinearDifference = currentLinearDifference
                bestResolution = size
            }
        }
        Logger.i("SizedResolutionFilter", "Selected resolution $bestResolution")
        return listOf(bestResolution)
    }
}