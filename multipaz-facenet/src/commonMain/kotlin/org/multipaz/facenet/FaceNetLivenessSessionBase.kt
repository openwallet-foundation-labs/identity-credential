package org.multipaz.facenet

import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.io.bytestring.ByteString
import org.multipaz.facematch.CameraFrame
import org.multipaz.facematch.FaceMatcherLivenessPromptState
import org.multipaz.facematch.FaceMatcherLivenessSession
import org.multipaz.facematch.OverlayFrame
import org.multipaz.util.Logger

private const val TAG = "FaceNetLivenessSessionBase"

private val fallbackJpegBytes = ByteString(
    byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00.toByte(), 0x10.toByte(),
        0x4A.toByte(), 0x46.toByte(), 0x49.toByte(), 0x46.toByte(), 0x00.toByte(), 0x01.toByte(),
        0x01.toByte(), 0x01.toByte(), 0x00.toByte(), 0x48.toByte(), 0x00.toByte(), 0x48.toByte(),
        0x00.toByte(), 0x00.toByte(), 0xFF.toByte(), 0xDB.toByte(), 0x00.toByte(), 0x43.toByte(),
        0x00.toByte(), 0x08.toByte(), 0x06.toByte(), 0x06.toByte(), 0x07.toByte(), 0x06.toByte(),
        0x05.toByte(), 0x08.toByte(), 0x07.toByte(), 0x07.toByte(), 0x07.toByte(), 0x09.toByte(),
        0x09.toByte(), 0x08.toByte(), 0x0A.toByte(), 0x0C.toByte(), 0x14.toByte(), 0x0D.toByte(),
        0x0C.toByte(), 0x0B.toByte(), 0x0B.toByte(), 0x0C.toByte(), 0x19.toByte(), 0x12.toByte(),
        0x13.toByte(), 0x0F.toByte(), 0x14.toByte(), 0x1D.toByte(), 0x1A.toByte(), 0x1F.toByte(),
        0x1E.toByte(), 0x1D.toByte(), 0x1A.toByte(), 0x1C.toByte(), 0x1C.toByte(), 0x20.toByte(),
        0x24.toByte(), 0x2E.toByte(), 0x27.toByte(), 0x20.toByte(), 0x22.toByte(), 0x2C.toByte(),
        0x23.toByte(), 0x1C.toByte(), 0x1C.toByte(), 0x28.toByte(), 0x37.toByte(), 0x29.toByte(),
        0x2C.toByte(), 0x30.toByte(), 0x31.toByte(), 0x34.toByte(), 0x34.toByte(), 0x34.toByte(),
        0x1F.toByte(), 0x27.toByte(), 0x39.toByte(), 0x3D.toByte(), 0x38.toByte(), 0x32.toByte(),
        0x3C.toByte(), 0x2E.toByte(), 0x33.toByte(), 0x34.toByte(), 0x32.toByte(), 0xFF.toByte(),
        0xC0.toByte(), 0x00.toByte(), 0x0B.toByte(), 0x08.toByte(), 0x00.toByte(), 0x01.toByte(),
        0x00.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x11.toByte(), 0x00.toByte(),
        0xFF.toByte(), 0xC4.toByte(), 0x00.toByte(), 0x1F.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x01.toByte(), 0x05.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(),
        0x01.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(),
        0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(),
        0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0xFF.toByte(), 0xDA.toByte(), 0x00.toByte(),
        0x08.toByte(), 0x01.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3F.toByte(),
        0x00.toByte(), 0xBF.toByte(), 0x80.toByte(), 0xFF.toByte(), 0xD9.toByte()
    )
)

/**
 * Base class containing the shared passive liveness verification and portrait capture state machine for FaceNet.
 *
 * Implements:
 * - Passive liveness verification: evaluates `MiniFASNetV2` anti-spoofing (`livenessScore >= passiveLivenessThreshold`)
 *   while the user looks straight at the camera, without requiring directional head-turn challenges.
 * - High-resolution portrait photo capture upon confirming passive liveness across 2 consecutive straight frames.
 * - Dynamic visual feedback via [OverlayFrame] and segmented ring.
 */
abstract class FaceNetLivenessSessionBase<TFace : DetectedFacePose>(
    val config: FaceNetModelConfig,
    val debug: Boolean = false,
    val matcherName: String = "facenet",
    val matcherDisplayName: String = "MobileFaceNet",
    val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    randomSeed: Long = clock(),
    val enablePoseSmoothing: Boolean = true
) : FaceMatcherLivenessSession() {

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
    @Volatile
    private var cheatModeRequested = false

    val livenessTimeoutMs = 8000L
    val sessionTimeoutMs = 25000L

    init {
        updateState(
            message = "Position your face and look at the camera",
            status = FaceMatcherLivenessPromptState.Status.IN_PROGRESS
        )
    }

    /**
     * Initializes platform-specific detector and interpreter pipelines.
     */
    protected abstract suspend fun initializePipeline()

    /**
     * Detects faces in [frame].
     */
    protected abstract suspend fun detectFaces(frame: CameraFrame): List<TFace>

    /**
     * Computes the passive anti-spoofing liveness score (real face probability in `[0.0, 1.0]`)
     * for [face] in [frame].
     */
    protected open suspend fun computeLivenessScore(frame: CameraFrame, face: TFace): Float? = 1.0f

    /**
     * Releases platform resources when session is closed or cancelled.
     */
    protected abstract fun onSessionClosed()

    /**
     * Captures a high-resolution upright portrait photo from [frame].
     */
    protected abstract suspend fun captureHighResolutionImage(frame: CameraFrame): ByteString?

    protected open fun createOverlay(
        frame: CameraFrame,
        faces: List<TFace>,
        currentLivenessScore: Float? = null
    ): OverlayFrame? {
        return renderOverlay(
            frame = frame,
            faces = faces,
            ringSegments = currentRingSegments,
            debug = debug,
            currentSimilarity = null,
            bestSimilarity = 0f,
            matchThreshold = config.matchThreshold,
            currentLivenessScore = currentLivenessScore,
            livenessThreshold = config.passiveLivenessThreshold
        )
    }

    private val frameMutex = Mutex()

    override suspend fun feedFrame(frame: CameraFrame) {
        if (isCancelled || state.value.status == FaceMatcherLivenessPromptState.Status.SUCCESS ||
            state.value.status == FaceMatcherLivenessPromptState.Status.FAILED) {
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

        if (cheatModeRequested) {
            val photoBytes = captureHighResolutionImage(frame) ?: fallbackJpegBytes
            phase = Phase.COMPLETED
            headPoseFilter.reset()
            currentRingSegments = List(RingSegment.NUM_SEGMENTS) {
                RingSegment(color = RingSegment.COLOR_GREEN, scale = 1.2f)
            }
            updateState(
                message = "Portrait captured",
                status = FaceMatcherLivenessPromptState.Status.SUCCESS,
                capturedImage = photoBytes,
                overlay = createOverlay(frame, emptyList(), null)
            )
            return
        }

        if (now - startTime!! > sessionTimeoutMs) {
            currentRingSegments = List(RingSegment.NUM_SEGMENTS) {
                RingSegment(color = RingSegment.COLOR_RED, scale = 1.0f)
            }
            failSession(
                message = "Liveness check timed out - please try again",
                overlay = createOverlay(frame, emptyList(), null)
            )
            return
        }

        val detectedFaces = detectFaces(frame)

        if (isCancelled) return

        if (detectedFaces.isEmpty()) {
            missedFaceFrames++
            consecutiveMatchFrames = 0
            straightFaceStartTime = null

            val overlay = createOverlay(frame, emptyList(), null)
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
            val overlay = createOverlay(frame, detectedFaces, null)
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
        var currentLiveness: Float? = null

        if (isCancelled) return

        if (isFacingStraight) {
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

                    val isLive = currentLiveness != null && currentLiveness >= config.passiveLivenessThreshold
                    if (isLive) {
                        consecutiveMatchFrames++
                        if (consecutiveMatchFrames >= 2) {
                            val photoBytes = captureHighResolutionImage(frame)
                            if (photoBytes == null) {
                                failSession("Could not capture portrait photo")
                                return
                            }
                            phase = Phase.COMPLETED
                            headPoseFilter.reset()
                            currentRingSegments = List(RingSegment.NUM_SEGMENTS) {
                                RingSegment(color = RingSegment.COLOR_GREEN, scale = 1.2f)
                            }
                            val successOverlay = createOverlay(frame, faces, currentLiveness)
                            updateState(
                                message = "Portrait captured",
                                status = FaceMatcherLivenessPromptState.Status.SUCCESS,
                                capturedImage = photoBytes,
                                overlay = successOverlay
                            )
                            return
                        } else {
                            currentRingSegments = List(RingSegment.NUM_SEGMENTS) {
                                RingSegment(color = RingSegment.COLOR_GREEN, scale = 1.15f)
                            }
                            val overlay = createOverlay(frame, faces, currentLiveness)
                            updateState(
                                message = "Hold still...",
                                overlay = overlay
                            )
                        }
                    } else {
                        consecutiveMatchFrames = 0
                        if (now - straightStart > livenessTimeoutMs) {
                            currentRingSegments = List(RingSegment.NUM_SEGMENTS) {
                                RingSegment(color = RingSegment.COLOR_RED, scale = 1.0f)
                            }
                            val overlay = createOverlay(frame, faces, currentLiveness)
                            failSession(
                                message = "Liveness check failed - ensure you are not using a photo or screen",
                                overlay = overlay
                            )
                            return
                        }
                        currentRingSegments = List(RingSegment.NUM_SEGMENTS) {
                            RingSegment(color = pulseColor, scale = 1.0f)
                        }
                        val overlay = createOverlay(frame, faces, currentLiveness)
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
                    val overlay = createOverlay(frame, faces, currentLiveness)
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
            status = FaceMatcherLivenessPromptState.Status.FAILED,
            overlay = overlay
        )
    }

    override fun onTouchEvent(x: Float, y: Float) {
        if (!debug) return
        if (isCancelled || state.value.status != FaceMatcherLivenessPromptState.Status.IN_PROGRESS) return

        cheatModeRequested = true
        phase = Phase.COMPLETED
        headPoseFilter.reset()
        currentRingSegments = List(RingSegment.NUM_SEGMENTS) {
            RingSegment(color = RingSegment.COLOR_GREEN, scale = 1.2f)
        }
        updateState(
            message = "Portrait captured",
            status = FaceMatcherLivenessPromptState.Status.SUCCESS,
            capturedImage = fallbackJpegBytes
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
