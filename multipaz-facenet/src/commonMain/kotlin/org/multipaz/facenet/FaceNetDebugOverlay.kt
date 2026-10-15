package org.multipaz.facenet

import org.multipaz.facematch.CameraFrame
import org.multipaz.facematch.OverlayFrame

internal expect fun renderOverlay(
    frame: CameraFrame,
    faces: List<DetectedFacePose>,
    ringSegments: List<RingSegment>,
    debug: Boolean,
    currentSimilarity: Float?,
    bestSimilarity: Float,
    matchThreshold: Float,
    currentLivenessScore: Float? = null,
    livenessThreshold: Float = 0.50f
): OverlayFrame?

