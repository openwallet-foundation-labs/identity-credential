package org.multipaz.facematch

/**
 * State of the face matcher prompt dialog observed by the UI layer during verification.
 *
 * @property message instructional or status message displayed to the user.
 * @property status overall verification status.
 * @property overlay optional bitmap overlay to draw on top of the camera video stream.
 * @property backgroundColor optional background color for the prompt screen. When `null`, the UI
 *   reverts to the default theme background color.
 */
data class FaceMatcherPromptState(
    val message: String? = null,
    val status: Status = Status.IN_PROGRESS,
    val overlay: OverlayFrame? = null,
    val backgroundColor: Color? = null
) {
    /** Overall status of the face verification session. */
    enum class Status {
        /** Verification is actively in progress. */
        IN_PROGRESS,

        /** Face matched successfully against the reference portrait. */
        SUCCESS,

        /** Face matching failed or could not be completed. */
        FAILED
    }
}
