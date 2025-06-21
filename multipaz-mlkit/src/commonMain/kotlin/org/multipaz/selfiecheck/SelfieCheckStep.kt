package org.multipaz.selfiecheck


/**
 * Distinct Selfie Check process steps used to verify the photo identity along with the feedback,
 * as needed to schedule randomized and fixed selfie verification steps, and to signal updates of the UI content.
 * Some of the instructions are deliberately randomized some fixed for the beginning and the end of the verification
 * flow.
 */
enum class SelfieCheckStep {
    /** Initial phase after the model initialization and reset. */
    INITIAL,

    /** User expected to center their face in the provided frame. */
    CENTER_FACE,

    /** Directional head rotation commands confirmation expected. Randomized. */
    ROTATE_HEAD_LEFT,
    ROTATE_HEAD_RIGHT,
    ROTATE_HEAD_UP,
    ROTATE_HEAD_DOWN,

    /**
     * User expected to look left and right, gradually passing several expected face directions without leaving the
     * vertical limits for the fce orientation.
     */
    CIRCULAR_GAZE,

    /** User expected to close both eyes for a period of time. */
    CLOSE_EYES,

    /** User expected to clearly smile into camera. */
    SMILE,

    /** General failure of one of the steps (given instructions not confirmed within allocated time). */
    FAILED,

    /** Final step of the selfie check process. Also helping */
    LOOK_STRAIGHT, // Final image taking step.,

    /** All verification steps completed successfully. */
    COMPLETED,

    /** Signals that the final image is taken and ready for requesting the user consent to share it with the issuer. */
    IMAGE_TAKEN,

    /** Signals the completion of the entire process. */
    DONE
}