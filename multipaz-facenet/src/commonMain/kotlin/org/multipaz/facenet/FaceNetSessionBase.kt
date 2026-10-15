package org.multipaz.facenet

import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.io.bytestring.ByteString
import org.multipaz.facematch.CameraFrame
import org.multipaz.facematch.Color
import org.multipaz.facematch.FaceMatcherPromptState
import org.multipaz.facematch.FaceMatcherSession
import org.multipaz.facematch.OverlayFrame
import org.multipaz.util.Logger

private const val TAG = "FaceNetSessionBase"

/**
 * Base class containing the shared biometric verification state machine for FaceNet.
 *
 * Implements:
 * - Single-phase passive verification: confirms both biometric identity match (`similarity >= matchThreshold`)
 *   and passive anti-spoofing liveness (`livenessScore >= passiveLivenessThreshold`) while the user looks directly
 *   at the camera, without requiring directional head-turn challenges.
 * - Latching prevention: requires 2 consecutive matching and live frames to prevent accidental triggers.
 * - Head pose guidance: detects head pitch, yaw, and roll with contextual user guidance.
 * - Timeout handling: 8-second match timeout, 25-second total session timeout.
 * - Dynamic visual feedback via [OverlayFrame]: pulsing ring during positioning, green highlight upon verification.
 */
abstract class FaceNetSessionBase<TFace : DetectedFacePose>(
    referencePortrait: ByteString,
    val config: FaceNetModelConfig,
    val debug: Boolean = false,
    val matcherName: String = "facenet",
    val matcherDisplayName: String = "MobileFaceNet",
    val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    randomSeed: Long = clock(),
    val enablePoseSmoothing: Boolean = true
) : FaceMatcherSession(referencePortrait) {

    init {
        require(isSupportedReferencePortraitFormat(referencePortrait)) {
            "Unsupported reference portrait format. Supported formats are PNG, JPEG, and JPEG 2000."
        }
    }

    enum class Phase {
        INITIALIZING,
        POSITIONING,
        COMPLETED,
        FAILED
    }

    var phase = Phase.INITIALIZING
        protected set

    private var startTime: Long? = null
    private var phaseStartTime: Long = 0L

    @Volatile
    var isCancelled: Boolean = false
        protected set

    protected var referenceEmbedding: FaceEmbedding? = null
    var bestSimilarity: Float = 0.0f
        protected set
    var bestLivenessScore: Float = 0.0f
        protected set
    private var initializationAttempted = false

    private var currentRingSegments: List<RingSegment> = RingSegment.defaultSegments
    internal val ringSegments: List<RingSegment>
        get() = currentRingSegments

    private var missedFaceFrames = 0
    private var consecutiveMatchFrames = 0
    private var straightFaceStartTime: Long? = null
    private val headPoseFilter = HeadPoseFilter()

    val matchTimeoutMs = 8000L
    val sessionTimeoutMs = 25000L

    init {
        updateState(
            message = "Position your face and look at the camera",
            status = FaceMatcherPromptState.Status.IN_PROGRESS,
            backgroundColor = Color.WHITE
        )
    }

    /**
     * Initializes platform-specific detector and interpreter pipelines,
     * and sets [referenceEmbedding].
     */
    protected abstract suspend fun initializePipeline()

    /**
     * Detects faces in [frame].
     */
    protected abstract suspend fun detectFaces(frame: CameraFrame): List<TFace>

    /**
     * Extracts face crop for [face] from [frame] and computes its embedding.
     */
    protected abstract suspend fun computeCameraEmbedding(frame: CameraFrame, face: TFace): FaceEmbedding?

    /**
     * Computes the passive anti-spoofing liveness score (real face probability in `[0.0, 1.0]`)
     * for [face] in [frame].
     */
    protected open suspend fun computeLivenessScore(frame: CameraFrame, face: TFace): Float? = 1.0f

    /**
     * Releases platform resources when session is closed or cancelled.
     */
    protected abstract fun onSessionClosed()

    protected open fun createOverlay(
        frame: CameraFrame,
        faces: List<TFace>,
        currentSimilarity: Float?,
        currentLivenessScore: Float? = null
    ): OverlayFrame? {
        return renderOverlay(
            frame = frame,
            faces = faces,
            ringSegments = currentRingSegments,
            debug = debug,
            currentSimilarity = currentSimilarity,
            bestSimilarity = bestSimilarity,
            matchThreshold = config.matchThreshold,
            currentLivenessScore = currentLivenessScore,
            livenessThreshold = config.passiveLivenessThreshold
        )
    }

    private val frameMutex = Mutex()

    override suspend fun feedFrame(frame: CameraFrame) {
        if (isCancelled || state.value.status != FaceMatcherPromptState.Status.IN_PROGRESS) {
            return
        }

        if (!frameMutex.tryLock()) {
            return
        }

        try {
            processFrame(frame)
        } finally {
            frameMutex.unlock()
        }
    }

    private suspend fun processFrame(frame: CameraFrame) {
        val now = clock()
        if (startTime == null) {
            startTime = now
            phaseStartTime = now
        }

        if (phase == Phase.INITIALIZING) {
            if (!initializationAttempted) {
                initializationAttempted = true
                try {
                    initializePipeline()
                    if (referenceEmbedding == null) {
                        failSession("Could not extract face from reference photo")
                        return
                    }
                    phase = Phase.POSITIONING
                    phaseStartTime = now
                    updateState(
                        message = "Position your face and look at the camera"
                    )
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to initialize pipeline", e)
                    failSession(e.message ?: "Could not start camera pipeline")
                    return
                }
            } else {
                return
            }
        }

        val refEmb = referenceEmbedding
        if (refEmb == null) {
            failSession("Missing reference face embedding")
            return
        }

        if (now - startTime!! > sessionTimeoutMs) {
            currentRingSegments = List(RingSegment.NUM_SEGMENTS) {
                RingSegment(color = RingSegment.COLOR_RED, scale = 1.0f)
            }
            val overlay = createOverlay(frame, emptyList(), null, null)
            failSession(
                message = "Face verification timed out",
                overlay = overlay
            )
            return
        }

        val detectedFaces = detectFaces(frame)

        if (isCancelled) return

        if (detectedFaces.isEmpty()) {
            missedFaceFrames++
            consecutiveMatchFrames = 0
            straightFaceStartTime = null

            val overlay = createOverlay(frame, emptyList(), null, null)
            if (missedFaceFrames >= 3) {
                headPoseFilter.reset()
                if (phase == Phase.POSITIONING) {
                    updateState(
                        message = "No face detected",
                        overlay = overlay
                    )
                }
            } else {
                updateState(overlay = overlay)
            }
            return
        }

        if (detectedFaces.size > 1) {
            missedFaceFrames = 0
            consecutiveMatchFrames = 0
            straightFaceStartTime = null
            headPoseFilter.reset()
            currentRingSegments = RingSegment.defaultSegments
            val overlay = createOverlay(frame, detectedFaces, null, null)
            updateState(
                message = "Ensure only one person is in the frame",
                overlay = overlay
            )
            return
        }

        missedFaceFrames = 0
        val rawFace = detectedFaces[0]
        val (yaw, pitch, roll) = if (enablePoseSmoothing) {
            headPoseFilter.filter(
                yaw = rawFace.yaw,
                pitch = rawFace.pitch,
                roll = rawFace.roll,
                timestampMillis = now
            )
        } else {
            Triple(rawFace.yaw, rawFace.pitch, rawFace.roll)
        }

        @Suppress("UNCHECKED_CAST")
        val face = if (enablePoseSmoothing && rawFace is BlazeFaceDetection) {
            rawFace.copy(yaw = yaw, pitch = pitch, roll = roll) as TFace
        } else {
            rawFace
        }
        val faces = listOf(face)

        val isFacingStraight = abs(yaw) < 12.0f && abs(pitch) < 12.0f && abs(roll) < 15.0f

        var currentSimilarity: Float? = null
        var currentLiveness: Float? = null

        if (isCancelled) return

        if (isFacingStraight) {
            try {
                val cameraEmbedding = computeCameraEmbedding(frame, face)
                if (cameraEmbedding != null) {
                    val similarity = refEmb.calculateSimilarity(cameraEmbedding)
                    currentSimilarity = similarity
                    if (similarity > bestSimilarity) {
                        bestSimilarity = similarity
                        Logger.d(TAG, "New best face similarity: $bestSimilarity (threshold=${config.matchThreshold})")
                    }
                }
            } catch (e: Exception) {
                if (!isCancelled) {
                    Logger.w(TAG, "Failed to compute embedding from camera frame", e)
                }
            }

            try {
                val liveness = computeLivenessScore(frame, face)
                if (liveness != null) {
                    currentLiveness = liveness
                    if (liveness > bestLivenessScore) {
                        bestLivenessScore = liveness
                    }
                }
            } catch (e: Exception) {
                if (!isCancelled) {
                    Logger.w(TAG, "Failed to compute liveness score from camera frame", e)
                }
            }
        }

        if (isCancelled) return

        when (phase) {
            Phase.POSITIONING -> {
                val elapsed = now - phaseStartTime
                val pulse = (sin(elapsed / 250.0) * 0.35 + 0.65).toFloat()
                val pulseColor = RingSegment.lerpColor(RingSegment.COLOR_DARK_GRAY, RingSegment.COLOR_BLUE, pulse)

                if (isFacingStraight) {
                    val straightStart = straightFaceStartTime ?: run {
                        straightFaceStartTime = now
                        now
                    }

                    val isMatched = currentSimilarity != null && currentSimilarity >= config.matchThreshold
                    val isLive = currentLiveness != null && currentLiveness >= config.passiveLivenessThreshold

                    if (isMatched && isLive) {
                        consecutiveMatchFrames++
                        if (consecutiveMatchFrames >= 2) {
                            phase = Phase.COMPLETED
                            headPoseFilter.reset()
                            currentRingSegments = List(RingSegment.NUM_SEGMENTS) {
                                RingSegment(color = RingSegment.COLOR_GREEN, scale = 1.2f)
                            }
                            val successOverlay = createOverlay(frame, faces, currentSimilarity, currentLiveness)
                            updateState(
                                message = "Identity verified",
                                status = FaceMatcherPromptState.Status.SUCCESS,
                                overlay = successOverlay
                            )
                            return
                        } else {
                            currentRingSegments = List(RingSegment.NUM_SEGMENTS) {
                                RingSegment(color = RingSegment.COLOR_GREEN, scale = 1.15f)
                            }
                            val overlay = createOverlay(frame, faces, currentSimilarity, currentLiveness)
                            updateState(
                                message = "Hold still...",
                                overlay = overlay
                            )
                        }
                    } else {
                        consecutiveMatchFrames = 0
                        if (now - straightStart > matchTimeoutMs) {
                            currentRingSegments = List(RingSegment.NUM_SEGMENTS) {
                                RingSegment(color = RingSegment.COLOR_RED, scale = 1.0f)
                            }
                            val overlay = createOverlay(frame, faces, currentSimilarity, currentLiveness)
                            when {
                                bestSimilarity >= config.matchThreshold && bestLivenessScore < config.passiveLivenessThreshold -> {
                                    failSession(
                                        message = "Liveness check failed - ensure you are not using a photo or screen",
                                        overlay = overlay
                                    )
                                }
                                bestSimilarity >= config.matchThreshold -> {
                                    failSession(
                                        message = "Unable to confirm match - please hold still and look directly at the camera",
                                        overlay = overlay
                                    )
                                }
                                else -> {
                                    failSession(
                                        message = "Face does not match reference portrait",
                                        overlay = overlay
                                    )
                                }
                            }
                            return
                        }
                        currentRingSegments = List(RingSegment.NUM_SEGMENTS) {
                            RingSegment(color = pulseColor, scale = 1.0f)
                        }
                        val overlay = createOverlay(frame, faces, currentSimilarity, currentLiveness)
                        updateState(
                            message = "Hold still and look directly at the camera...",
                            overlay = overlay
                        )
                    }
                } else {
                    straightFaceStartTime = null
                    consecutiveMatchFrames = 0
                    val prompt = when {
                        yaw > 12f -> "Turn your head slightly to the right"
                        yaw < -12f -> "Turn your head slightly to the left"
                        pitch > 12f -> "Tilt your head slightly down"
                        pitch < -12f -> "Tilt your head slightly up"
                        roll > 15f || roll < -15f -> "Keep your head level"
                        else -> "Look directly at the camera"
                    }
                    currentRingSegments = List(RingSegment.NUM_SEGMENTS) {
                        RingSegment(color = pulseColor, scale = 1.0f)
                    }
                    val overlay = createOverlay(frame, faces, currentSimilarity, currentLiveness)
                    updateState(
                        message = prompt,
                        overlay = overlay
                    )
                }
            }

            Phase.COMPLETED, Phase.FAILED, Phase.INITIALIZING -> {}
        }
    }

    protected fun failSession(
        message: String,
        overlay: OverlayFrame? = null
    ) {
        phase = Phase.FAILED
        headPoseFilter.reset()
        updateState(
            message = message,
            status = FaceMatcherPromptState.Status.FAILED,
            overlay = overlay
        )
    }

    override fun onTouchEvent(x: Float, y: Float) {
        if (!debug) return
        if (isCancelled || state.value.status != FaceMatcherPromptState.Status.IN_PROGRESS) return

        bestSimilarity = 1.0f
        phase = Phase.COMPLETED
        headPoseFilter.reset()
        currentRingSegments = List(RingSegment.NUM_SEGMENTS) {
            RingSegment(color = RingSegment.COLOR_GREEN, scale = 1.2f)
        }
        updateState(
            message = "Identity verified",
            status = FaceMatcherPromptState.Status.SUCCESS
        )
    }

    override fun cancel() {
        isCancelled = true
        headPoseFilter.reset()
        if (frameMutex.tryLock()) {
            try {
                onSessionClosed()
            } finally {
                frameMutex.unlock()
            }
        }
    }
}
