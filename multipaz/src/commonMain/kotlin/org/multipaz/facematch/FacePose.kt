package org.multipaz.facematch

/**
 * Head poses that can be requested and captured during a [FaceMatcherLivenessSession].
 */
enum class FacePose {
    /** Looking straight ahead at the camera. */
    FRONT,

    /** Turning head to the left. */
    LOOK_LEFT,

    /** Turning head to the right. */
    LOOK_RIGHT,

    /** Tilting head up. */
    LOOK_UP,

    /** Tilting head down. */
    LOOK_DOWN
}
