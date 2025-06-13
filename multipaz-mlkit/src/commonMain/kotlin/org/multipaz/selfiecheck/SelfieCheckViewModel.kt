package org.multipaz.selfiecheck

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.multipaz.compose.camera.CameraFrame
import org.multipaz.compose.cropRotateScaleImage
import org.multipaz.compose.encodeImageAsPng
import org.multipaz.facedetection.DetectedFace
import org.multipaz.facedetection.FaceLandmarkType
import org.multipaz.util.Logger
import org.multipaz.util.getByteString
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.random.Random

enum class SelfieCheckStep {
    INITIAL,
    CENTER_FACE,
    ROTATE_HEAD_LEFT,
    ROTATE_HEAD_RIGHT,
    ROTATE_HEAD_UP,
    ROTATE_HEAD_DOWN,
    CIRCULAR_GAZE,
    CLOSE_EYES,
    SMILE,
    COMPLETED,
    FAILED
}

enum class HeadRotationDirection {
    LEFT, RIGHT, UP, DOWN
}

const val TAG = "SelfieCheck"

private const val FACE_CENTER_TOLERANCE = 0.1f
private const val HEAD_ROTATION_ANGLE_THRESHOLD = 20.0f
private const val EYE_CLOSED_THRESHOLD = 0.1f
private const val EYES_CLOSED_DURATION = 40 // In frames ~2 sec)
private const val SMILING_THRESHOLD = 0.7f
private const val STEP_TIMEOUT_SECONDS = 10
private const val ONE_SECOND_MS = 1000L
private const val GAZE_CIRCLE_SECTORS = 8 // How many distinct directions to look.
private const val GAZE_PITCH_TOLERANCE = 25f // Max degrees up/down allowed while still considered "looking around".
private const val GAZE_YAW_MAX = 50f // Max degrees left or right from center.
private const val GAZE_MIN_CONSECUTIVE_HITS_IN_SECTOR = 2 // Debouncing.

class SelfieCheckViewModel(
    externalScope: CoroutineScope? = null,
) {
    /** Public access to the latest captured frame. */
    var capturedFaceImage: ByteString? = null

    private val viewModelScope = externalScope ?: CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var stepTimeoutJob: Job? = null

    private val allVerifiableSteps = listOf( // Define all steps that can be shuffled
        SelfieCheckStep.ROTATE_HEAD_LEFT,
        SelfieCheckStep.ROTATE_HEAD_RIGHT,
        SelfieCheckStep.ROTATE_HEAD_UP,
        SelfieCheckStep.ROTATE_HEAD_DOWN,
        SelfieCheckStep.CIRCULAR_GAZE,
        SelfieCheckStep.CLOSE_EYES,
        SelfieCheckStep.SMILE
    )

    private var shuffledStepsQueue = mutableListOf<SelfieCheckStep>()
    private var currentStepIndex = -1
    private var currentRotationTargetDirection: HeadRotationDirection? = null
    private var eyesClosedCounter = 0
    private val gazeSectorsHit = mutableSetOf<Int>()
    private var lastGazeSector = -1
    private var consecutiveGazeSectorHits = 0
    private var allSectorsShown = false

    private val stepSuccessEventFlow = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val stepSuccessEvent: SharedFlow<Unit> = stepSuccessEventFlow.asSharedFlow()

    private val currentStepFlow = MutableStateFlow(SelfieCheckStep.INITIAL)
    val currentStep: StateFlow<SelfieCheckStep> = currentStepFlow.asStateFlow()

    private val stepFeedback = MutableStateFlow(StepFeedback(EventId.DEFAULT))
    val instructions: StateFlow<StepFeedback> = stepFeedback.asStateFlow()

    private val countdownSecondsFlow = MutableStateFlow(STEP_TIMEOUT_SECONDS)
    val countdownSeconds: StateFlow<Int> = countdownSecondsFlow.asStateFlow()

    private val countdownProgressFlow = MutableStateFlow(1.0f)
    val countdownProgress: StateFlow<Float> = countdownProgressFlow.asStateFlow()

    private val isLandscapeFlow = MutableStateFlow(false)
    val isLandscape: StateFlow<Boolean> = isLandscapeFlow.asStateFlow()

    init {
        prepareShuffledSteps()
    }

    private fun prepareShuffledSteps() {
        shuffledStepsQueue.clear()
        shuffledStepsQueue.addAll(allVerifiableSteps.shuffled(Random))
        currentStepIndex = -1 // Reset index
    }

    /**
     * Resets the ViewModel to its initial state and prepares for a new selfie check process.
     * This is the public function to be called to restart the selfie check.
     */
    fun resetForNewCheck() {
        cancelStepTimeout()
        resetSpecificStepStates()
        prepareShuffledSteps()
        currentStepFlow.value = SelfieCheckStep.INITIAL
        generateFeedbackEvent()
    }

    fun startSelfieCheck() {
        if (currentStepFlow.value != SelfieCheckStep.INITIAL) {
            resetForNewCheck()
        }
        // Always start with centering as some other steps depends on it.
        currentStepFlow.value = SelfieCheckStep.CENTER_FACE
        generateFeedbackEvent()
        startStepTimeout()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun resetSpecificStepStates() {
        currentRotationTargetDirection = null

        eyesClosedCounter = 0

        gazeSectorsHit.clear()
        lastGazeSector = -1
        consecutiveGazeSectorHits = 0

        countdownSecondsFlow.value = STEP_TIMEOUT_SECONDS
        countdownProgressFlow.value = 1.0f
        isLandscapeFlow.value = false
        stepSuccessEventFlow.resetReplayCache()
        capturedFaceImage = null
    }

    /** Signal the orientation value change from the UI (Composable scope). */
    internal fun setLandscape(isLandscape: Boolean) {
        isLandscapeFlow.value = isLandscape
    }

    /**
     * Update model on new frame with detected face data and frame size to feed current check processor.
     */
    internal fun onFaceDataUpdated(faceData: DetectedFace, frameData: CameraFrame) {
        if (currentStepFlow.value == SelfieCheckStep.COMPLETED) {
            storeFaceImage(faceData, frameData)
            return
        }

        if (currentStepFlow.value == SelfieCheckStep.FAILED || currentStepFlow.value == SelfieCheckStep.INITIAL) {
            return
        }

        val previewSize = Size(frameData.width.toFloat(), frameData.height.toFloat())

        when (currentStepFlow.value) {
            SelfieCheckStep.CENTER_FACE -> processFaceCentering(faceData.boundingBox, previewSize)
            SelfieCheckStep.ROTATE_HEAD_LEFT,
            SelfieCheckStep.ROTATE_HEAD_RIGHT,
            SelfieCheckStep.ROTATE_HEAD_UP,
            SelfieCheckStep.ROTATE_HEAD_DOWN -> {
                val targetDirection = when (currentStepFlow.value) {
                    SelfieCheckStep.ROTATE_HEAD_LEFT -> HeadRotationDirection.LEFT
                    SelfieCheckStep.ROTATE_HEAD_RIGHT -> HeadRotationDirection.RIGHT
                    SelfieCheckStep.ROTATE_HEAD_UP -> HeadRotationDirection.UP
                    SelfieCheckStep.ROTATE_HEAD_DOWN -> HeadRotationDirection.DOWN
                    else -> null // Should not happen.
                }
                processHeadRotation(
                    targetDirection = targetDirection,
                    headEulerAngleX = faceData.headEulerAngleX,
                    headEulerAngleY = faceData.headEulerAngleY
                )
            }

            SelfieCheckStep.CIRCULAR_GAZE -> {
                processGazeCircularMotion(
                    headEulerAngleY = faceData.headEulerAngleY,
                    headEulerAngleX = faceData.headEulerAngleX
                )
            }

            SelfieCheckStep.CLOSE_EYES -> processEyeClosure(
                faceData.leftEyeOpenProbability,
                faceData.rightEyeOpenProbability
            )

            SelfieCheckStep.SMILE -> processSmile(faceData.smilingProbability)
            else -> { /* INITIAL, COMPLETED, FAILED - no processing. */
            }
        }
    }

    private fun startStepTimeout() {
        cancelStepTimeout()
        val currentStepVal = currentStepFlow.value
        val timeoutDuration = if (currentStepVal == SelfieCheckStep.CIRCULAR_GAZE) {
            STEP_TIMEOUT_SECONDS * 2 // As it might take longer to complete compared to others.
        } else {
            STEP_TIMEOUT_SECONDS
        }
        countdownSecondsFlow.value = timeoutDuration
        countdownProgressFlow.value = 1.0f

        if (currentStepVal == SelfieCheckStep.INITIAL || currentStepVal == SelfieCheckStep.COMPLETED
            || currentStepVal == SelfieCheckStep.FAILED
        ) {
            return
        }

        stepTimeoutJob = viewModelScope.launch {
            for (i in timeoutDuration downTo 0) {
                if (!isActive) break
                countdownSecondsFlow.value = i
                countdownProgressFlow.value = i.toFloat() / timeoutDuration.toFloat()
                if (i == 0) {
                    if (currentStepFlow.value != SelfieCheckStep.COMPLETED
                        && currentStepFlow.value != SelfieCheckStep.FAILED
                    ) {
                        failCheck(StepFeedback(EventId.FAILED_TIMEOUT, currentStepFlow.value.toString()))
                    }
                    break
                }
                delay(ONE_SECOND_MS)
            }
        }
    }

    private fun cancelStepTimeout() {
        stepTimeoutJob?.cancel()
        stepTimeoutJob = null
    }

    private fun failCheck(reason: StepFeedback) {
        cancelStepTimeout()
        currentStepFlow.value = SelfieCheckStep.FAILED
        stepFeedback.value = reason
        countdownSecondsFlow.value = 0
        countdownProgressFlow.value = 0.0f
    }

    private fun processFaceCentering(faceBounds: Rect?, previewSize: Size?) {
        if ((faceBounds == null || previewSize == null) || previewSize.width < 1 || previewSize.height < 1) {
            stepFeedback.value = StepFeedback(EventId.FAILED_FACE_CENTERING)
            return
        }

        val faceCenterXpx = faceBounds.left + faceBounds.width / 2f
        val faceCenterYpx = faceBounds.top + faceBounds.height / 2f

        // Normalized face center check.
        val normalizedFaceCenterX = faceCenterXpx / previewSize.width
        val normalizedFaceCenterY = faceCenterYpx / previewSize.height
        val isHorizontallyCentered = kotlin.math.abs(normalizedFaceCenterX - 0.5f) < FACE_CENTER_TOLERANCE
        val isVerticallyCentered = kotlin.math.abs(normalizedFaceCenterY - 0.5f) < FACE_CENTER_TOLERANCE

        if (isHorizontallyCentered && isVerticallyCentered) {
            proceedToNextStep()
        } else {
            // Apparently swapped directions due to the constant 90 deg camera frame angle with the screen.
            val isFaceCentered = kotlin.math.abs(normalizedFaceCenterY - 0.5f) < FACE_CENTER_TOLERANCE
            stepFeedback.value = StepFeedback(
                if (isLandscape.value) {
                    if (isFaceCentered) EventId.MOVE_HORIZONTALLY else EventId.MOVE_VERTICALLY
                } else {
                    if (isFaceCentered) EventId.MOVE_VERTICALLY else EventId.MOVE_HORIZONTALLY
                }
            )
        }
    }

    private fun processHeadRotation(
        targetDirection: HeadRotationDirection?,
        headEulerAngleX: Float?,
        headEulerAngleY: Float?
    ) {
        if (targetDirection == null || headEulerAngleX == null || headEulerAngleY == null) return

        val achieved = when (targetDirection) {
            HeadRotationDirection.LEFT -> headEulerAngleY > HEAD_ROTATION_ANGLE_THRESHOLD
            HeadRotationDirection.RIGHT -> headEulerAngleY < -HEAD_ROTATION_ANGLE_THRESHOLD
            HeadRotationDirection.UP -> headEulerAngleX > HEAD_ROTATION_ANGLE_THRESHOLD
            HeadRotationDirection.DOWN -> headEulerAngleX < -HEAD_ROTATION_ANGLE_THRESHOLD
        }

        if (achieved) {
            proceedToNextStep()
        }
    }

    private fun processGazeCircularMotion(
        headEulerAngleY: Float, // Yaw.
        headEulerAngleX: Float  // Pitch.
    ) {
        if (kotlin.math.abs(headEulerAngleX) > GAZE_PITCH_TOLERANCE) {
            stepFeedback.value = StepFeedback(EventId.KEEP_HEAD_LEVEL, "")
            consecutiveGazeSectorHits = 0 // Reset on this mishap.
            lastGazeSector = -1
            return
        }

        val effectiveYaw = headEulerAngleY.coerceIn(-GAZE_YAW_MAX, GAZE_YAW_MAX)
        val mappedYaw = effectiveYaw + GAZE_YAW_MAX

        // Calculate current sector.
        val sectorAngleSize = 2 * GAZE_YAW_MAX / GAZE_CIRCLE_SECTORS
        val currentSector = (mappedYaw / sectorAngleSize).toInt().coerceIn(0, GAZE_CIRCLE_SECTORS - 1)
        if (currentSector != lastGazeSector) {
            consecutiveGazeSectorHits++
            if (consecutiveGazeSectorHits >= GAZE_MIN_CONSECUTIVE_HITS_IN_SECTOR) {
                // Confirmed entry into currentCalculatedSector (not a noise).
                if (gazeSectorsHit.add(currentSector)) {
                    stepFeedback.value = StepFeedback(EventId.SECTOR_HIT, currentSector.toString())
                }
                lastGazeSector = currentSector
                consecutiveGazeSectorHits = 0
            }
        } else {
            consecutiveGazeSectorHits = 0
        }

        if (gazeSectorsHit.size >= GAZE_CIRCLE_SECTORS) {
            if (allSectorsShown) {
                proceedToNextStep()
            }
            else {
                allSectorsShown = true
            }
        }
    }

    private fun processEyeClosure(leftEyeOpenProbability: Float?, rightEyeOpenProbability: Float?) {
        if (leftEyeOpenProbability != null && rightEyeOpenProbability != null &&
            leftEyeOpenProbability < EYE_CLOSED_THRESHOLD &&
            rightEyeOpenProbability < EYE_CLOSED_THRESHOLD &&
            eyesClosedCounter > EYES_CLOSED_DURATION
        ) {
            proceedToNextStep()
        } else {
            stepFeedback.value = StepFeedback(EventId.CLOSE_FIRMLY)
            eyesClosedCounter++
        }
    }

    private fun processSmile(smilingProbability: Float?) {
        if (smilingProbability != null && smilingProbability > SMILING_THRESHOLD) {
            proceedToNextStep()
        } else {
            stepFeedback.value = StepFeedback(EventId.CLEAR_SMILE)
        }
    }

    private fun proceedToNextStep() {
        cancelStepTimeout()
        val previousStep = currentStepFlow.value

        if (previousStep == SelfieCheckStep.CIRCULAR_GAZE && gazeSectorsHit.size < GAZE_CIRCLE_SECTORS) {
            gazeSectorsHit.clear()
            lastGazeSector = -1 // Reset incomplete gaze check.
            consecutiveGazeSectorHits = 0
        }

        val advancedSuccessfully: Boolean

        if (previousStep == SelfieCheckStep.CENTER_FACE) {
            currentStepIndex = 0
            if (shuffledStepsQueue.isNotEmpty()) {
                currentStepFlow.value = shuffledStepsQueue[currentStepIndex]
                advancedSuccessfully = true
            } else {
                currentStepFlow.value = SelfieCheckStep.COMPLETED
                advancedSuccessfully = true
            }
        } else {
            currentStepIndex++
            if (currentStepIndex < shuffledStepsQueue.size) {
                currentStepFlow.value = shuffledStepsQueue[currentStepIndex]
                advancedSuccessfully = true
            } else {
                currentStepFlow.value = SelfieCheckStep.COMPLETED
                advancedSuccessfully = true
            }
        }

        // Signal step success for UI feedback.
        if (advancedSuccessfully && currentStepFlow.value != SelfieCheckStep.INITIAL) {
            stepSuccessEventFlow.tryEmit(Unit)
        }

        generateFeedbackEvent()

        if (currentStepFlow.value == SelfieCheckStep.COMPLETED) {
            countdownSecondsFlow.value = STEP_TIMEOUT_SECONDS
            countdownProgressFlow.value = 1.0f
        } else if (currentStepFlow.value != SelfieCheckStep.FAILED) {
            // Circular motion needs a more specialized reset.
            if (currentStepFlow.value == SelfieCheckStep.CIRCULAR_GAZE) {
                gazeSectorsHit.clear()
                lastGazeSector = -1
                consecutiveGazeSectorHits = 0
                allSectorsShown = false
            }
            startStepTimeout()
        }
    }

    private fun generateFeedbackEvent() {
        stepFeedback.value = when (currentStepFlow.value) {
            SelfieCheckStep.INITIAL -> StepFeedback(EventId.INITIAL)
            SelfieCheckStep.CENTER_FACE -> StepFeedback(EventId.CENTER_FACE)
            SelfieCheckStep.ROTATE_HEAD_LEFT -> StepFeedback(EventId.ROTATE_HEAD_LEFT)
            SelfieCheckStep.ROTATE_HEAD_RIGHT -> StepFeedback(EventId.ROTATE_HEAD_RIGHT)
            SelfieCheckStep.ROTATE_HEAD_UP -> StepFeedback(EventId.ROTATE_HEAD_UP)
            SelfieCheckStep.ROTATE_HEAD_DOWN -> StepFeedback(EventId.ROTATE_HEAD_DOWN)
            SelfieCheckStep.CIRCULAR_GAZE -> {
                val remaining = GAZE_CIRCLE_SECTORS - gazeSectorsHit.size
                if (gazeSectorsHit.isEmpty()) {
                    StepFeedback(EventId.CIRCULAR_GAZE_START)
                } else {
                    StepFeedback(EventId.CIRCULAR_GAZE_LEFT, remaining.toString())
                }
            }
            SelfieCheckStep.CLOSE_EYES -> StepFeedback(EventId.CLOSE_EYES)
            SelfieCheckStep.SMILE -> StepFeedback(EventId.SMILE)
            SelfieCheckStep.COMPLETED -> StepFeedback(EventId.COMPLETED)
            SelfieCheckStep.FAILED -> stepFeedback.value // Propagate any error message as is.
        }
    }

    /** Store the image as a PNG file format ByteString within the model. */
    private fun storeFaceImage(faceData: DetectedFace, frameData: CameraFrame) {
        val faceImage = extractFaceBitmap(frameData, listOf(faceData))
        val byteArray = encodeImageAsPng(faceImage)
        capturedFaceImage = byteArray?.getByteString(0, byteArray.size)
    }

    /** Cut out the face square, rotate it to level eyes line, scale to the smaller size for face matching tasks. */
    private fun extractFaceBitmap(
        frameData: CameraFrame,
        faces: List<DetectedFace>?,
    ): ImageBitmap {
        if (faces.isNullOrEmpty()) {
            Logger.w(TAG, "No face data for bitmap extraction.")
            return frameData.cameraImage.toImageBitmap()
        }

        val leftEye = faces[0].landmarks.find { it.type == FaceLandmarkType.LEFT_EYE }
        val rightEye = faces[0].landmarks.find { it.type == FaceLandmarkType.RIGHT_EYE }

        if (leftEye == null || rightEye == null) {
            Logger.w(TAG, "No face features for bitmap extraction.")
            return frameData.cameraImage.toImageBitmap()
        }

        // Heuristic multiplier to fit the face normalized to the eyes pupilar distance.
        val faceCropFactor = 3.5f

        // Heuristic multiplier to offset vertically so the face is better centered within the rectangular crop.
        val faceVerticalOffsetFactor = 0.5f

        var faceCenterX = (leftEye.position.x + rightEye.position.x) / 2
        var faceCenterY = (leftEye.position.y + rightEye.position.y) / 2
        val eyeOffsetX = leftEye.position.x - rightEye.position.x
        val eyeOffsetY = leftEye.position.y - rightEye.position.y
        val eyeDistance = sqrt(eyeOffsetX * eyeOffsetX + eyeOffsetY * eyeOffsetY)
        val faceWidth = eyeDistance * faceCropFactor
        val faceVerticalOffset = eyeDistance * faceVerticalOffsetFactor
        if (frameData.isLandscape) {
            faceCenterY += faceVerticalOffset
        } else {
            faceCenterX -= faceVerticalOffset
        }
        val eyesAngleRad = atan2(eyeOffsetY, eyeOffsetX)
        val eyesAngleDeg = eyesAngleRad * 180.0 / PI
        val totalRotationDegrees = 180 - eyesAngleDeg

        // Call platform dependent bitmap transformation.
        return cropRotateScaleImage(
            frameData = frameData, // Platform-specific image data.
            cx = faceCenterX.toDouble(), // Point between eyes.
            cy = faceCenterY.toDouble(), // Point between eyes.
            angleDegrees = totalRotationDegrees, // Includes the camera rotation and eyes rotation.
            outputWidth = faceWidth.toInt(),  // Expected face width for cropping *before* final scaling.
            outputHeight = faceWidth.toInt(), // Expected face height for cropping *before* final scaling.
            targetWidth = 256 // Final square image size (for database saving and face matching tasks).
        )
    }

    /** Selfie-check unique flow event IDs. */
    enum class EventId {
        DEFAULT,
        INITIAL,
        CENTER_FACE,
        ROTATE_HEAD_LEFT,
        ROTATE_HEAD_RIGHT,
        ROTATE_HEAD_UP,
        ROTATE_HEAD_DOWN,
        CIRCULAR_GAZE_START,
        CIRCULAR_GAZE_LEFT,
        CLOSE_EYES,
        SMILE,
        COMPLETED,
        MOVE_HORIZONTALLY,
        MOVE_VERTICALLY,
        FAILED_FACE_CENTERING,
        KEEP_HEAD_LEVEL,
        KEEP_LOOKING,
        LOOK_AROUND,
        CLOSE_FIRMLY,
        CLEAR_SMILE,
        FAILED_TIMEOUT,
        SECTOR_HIT
    }

    /**
     * Selfie-check flow feedback object used for the UI to display instructions (text, graphic, etc).
     *
     * @param eventId Flow instructions Id. Currently used to display text in the UI.
     * @param textParam String value to display within the formatted string if any.
     */
    data class StepFeedback(val eventId: EventId, val textParam: String = "")

}