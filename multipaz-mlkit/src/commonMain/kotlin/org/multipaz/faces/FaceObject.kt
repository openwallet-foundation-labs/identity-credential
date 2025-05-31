package org.multipaz.faces

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** MLKit face detection result object copy of the facial landmark type recognized. */
data class MultipazFaceLandmark(
    /**
     * Type of the facial feature landmark.
     * - MLKFaceLandmarkTypeMouthBottom - The center of the bottom lip.
     * - MLKFaceLandmarkTypeMouthRight - The right corner of the mouth.
     * - MLKFaceLandmarkTypeMouthLeft - The left corner of the mouth.
     * - MLKFaceLandmarkTypeLeftEar The midpoint of the left ear tip and left ear lobe.
     * - MLKFaceLandmarkTypeRightEar - The midpoint of the right ear tip and right ear lobe.
     * - MLKFaceLandmarkTypeLeftEye - The left eye.
     * - MLKFaceLandmarkTypeRightEye - The right eye.
     * - MLKFaceLandmarkTypeLeftCheek - The left cheek.
     * - MLKFaceLandmarkTypeRightCheek - The right cheek.
     * - MLKFaceLandmarkTypeNoseBase The midpoint between the nostrils where the nose meets the face.
     */
    val type: Int,

    /**
     * List of Offset(x, y) points coordinates of the facial feature landmark point of this type relative to the
     * image in the processed bitmap coordinate system.
     */
    val position: Offset
) {
    companion object {
        const val MOUTH_BOTTOM_LM: Int = 0
        const val MOUTH_RIGHT_LM: Int = 11
        const val MOUTH_LEFT_LM: Int = 5
        const val RIGHT_EYE_LM: Int = 10
        const val LEFT_EYE_LM: Int = 4
        const val RIGHT_EAR_LM: Int = 9
        const val LEFT_EAR_LM: Int = 3
        const val RIGHT_CHEEK_LM: Int = 7
        const val LEFT_CHEEK_LM: Int = 1
        const val NOSE_BASE_LM: Int = 6
        const val UNKNOWN_LM: Int = 1001
    }
}

/** MLKit face detection result object copy of the facial contour type recognized. */
data class MultipazFaceContour(
    /**
     * Type of the facial feature contour recognized.
     * - MLKFaceContourTypeFace - A set of points that outline the face oval.
     * - MLKFaceContourTypeLeftEyebrowTop - A set of points that outline the top of the left eyebrow.
     * - MLKFaceContourTypeLeftEyebrowBottom - A set of points that outline the bottom of the left eyebrow.
     * - MLKFaceContourTypeRightEyebrowTop - A set of points that outline the top of the right eyebrow.
     * - MLKFaceContourTypeRightEyebrowBottom A set of points that outline the bottom of the right eyebrow.
     * - MLKFaceContourTypeLeftEye - A set of points that outline the left eye.
     * - MLKFaceContourTypeRightEye - A set of points that outline the right eye.
     * - MLKFaceContourTypeUpperLipTop - A set of points that outline the top of the upper lip.
     * - MLKFaceContourTypeUpperLipBottom - A set of points that outline the bottom of the upper lip.
     * - MLKFaceContourTypeLowerLipTop - A set of points that outline the top of the lower lip.
     * - MLKFaceContourTypeLowerLipBottom - A set of points that outline the bottom of the lower lip.
     * - MLKFaceContourTypeNoseBridge - A set of points that outline the nose bridge.
     * - MLKFaceContourTypeNoseBottom - A set of points that outline the bottom of the nose.
     * - MLKFaceContourTypeLeftCheek - A center point on the left cheek.
     * - MLKFaceContourTypeRightCheek - A center point on the right cheek.
     */
    val type: Int,

    /**
     * List of Offset(x, y) points coordinates that outline the facial feature contour of this type relative to the
     * image in the processed bitmap coordinate system.
     */
    val points: List<Offset>
) {
    companion object {
        const val FACE_C = 1
        const val LEFT_EYEBROW_TOP_C = 2
        const val LEFT_EYEBROW_BOTTOM_C = 3
        const val RIGHT_EYEBROW_TOP_C = 4
        const val RIGHT_EYEBROW_BOTTOM_C = 5
        const val LEFT_EYE_C = 6
        const val RIGHT_EYE_C = 7
        const val UPPER_LIP_TOP_C = 8
        const val UPPER_LIP_BOTTOM_C = 9
        const val LOWER_LIP_TOP_C = 10
        const val LOWER_LIP_BOTTOM_C = 11
        const val NOSE_BRIDGE_C = 12
        const val NOSE_BOTTOM_C = 13
        const val LEFT_CHEEK_C = 14
        const val RIGHT_CHEEK_C = 15
        const val UNKNOWN_C = 1002
    }
}

/** MLKit face detection result object copy of the recognized face data and facial features as detected. */
data class FaceObject(
    /**
     * MLKit face detection result object copy of the rectangle containing the detected face relative to the image
     * in the bitmap coordinate system.
     */
    val boundingBox: Rect,

    /** MLKit face detection result object copy of the tracking ID of the recognized face (out of multiple). */
    val trackingId: Int?,

    /** MLKit face detection result object copy of the probability of the right eye being open [0-1f]. */
    val rightEyeOpenProbability: Float?,

    /** MLKit face detection result object copy of the probability of the left eye being open [0-1f]. */
    val leftEyeOpenProbability: Float?,

    /** MLKit face detection result object copy of the probability of the face being smiling [0-1f]. */
    val smilingProbability: Float?,

    /**
     * MLKit face detection result object copy of the roll angle of the face. Degrees.
     * Indicates the rotation of the face about the horizontal axis of the image. Positive x euler angle
     * is when the face is turned upward in the image that is being processed.
     */
    val headEulerAngleX: Float,

    /**
     * MLKit face detection result object copy of the yaw angle of the face. Degrees.
     * Indicates the rotation of the face about the vertical axis of the image. Positive y euler angle
     * is when the face is turned towards the right side of the image that is being processed.
     */
    val headEulerAngleY: Float,

    /**
     * MLKit face detection result object copy of the pitch angle of the face. Degrees.
     * Indicates the rotation of the face about the axis pointing out of the image. Positive z euler
     * angle is a counter-clockwise rotation within the image plane.
     */
    val headEulerAngleZ: Float,

    /** MLKit face detection result object copy of facial landmarks recognized. Constant size 10 types. */
    val landmarks: List<MultipazFaceLandmark>,

    /** MLKit face detection result object copy of facial contours recognized. Constant size 15 types. */
    val contours: List<MultipazFaceContour>
)

