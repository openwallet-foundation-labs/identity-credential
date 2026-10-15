package org.multipaz.facematch

import kotlinx.io.bytestring.ByteString

/**
 * State of the face matcher liveness prompt dialog observed by the UI layer.
 *
 * @property message instructional or status message displayed to the user.
 * @property status overall verification status.
 * @property overlay optional bitmap overlay to draw on top of the camera video stream.
 * @property capturedImages map from each requested [FacePose] to the portrait photo bytes captured
 * for that pose during liveness verification. This map is populated if - and only if - [status] is
 * [Status.SUCCESS]. When populated, each image is the uncropped full camera frame encoded in JPEG
 * format at the resolution of the camera stream in upright portrait orientation. For all other
 * statuses, this property is empty.
 * @property backgroundColor optional background color for the liveness screen. When `null`, the UI
 *   reverts to the default theme background color.
 */
data class FaceMatcherLivenessPromptState(
    val message: String? = null,
    val status: Status = Status.IN_PROGRESS,
    val overlay: OverlayFrame? = null,
    val capturedImages: Map<FacePose, ByteString> = emptyMap(),
    val backgroundColor: Color? = null
) {
    /** Overall status of the face liveness verification session. */
    enum class Status {
        /** Liveness challenge poses or face positioning are actively in progress. */
        IN_PROGRESS,

        /** Liveness verified and portrait photo successfully captured. */
        SUCCESS,

        /** Liveness verification failed or could not be completed. */
        FAILED
    }
}
