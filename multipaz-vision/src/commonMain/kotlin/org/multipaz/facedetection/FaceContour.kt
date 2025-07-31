package org.multipaz.facedetection

import androidx.compose.ui.geometry.Offset

/**
 * Facial contour types detectable by MLKit.
 * (common IDs to support different Android and iOS implementation of them in MLKit).
 *
 * @property type The common type ID of the contour.
 */
enum class FaceContourType(val type: Int) {
    /** MLKFaceContourTypeFace - A set of points that outline the face oval. */
    FACE(1),

    /** MLKFaceContourTypeLeftEyebrowTop - A set of points that outline the top of the left eyebrow. */
    LEFT_EYEBROW_TOP(2),

    /** MLKFaceContourTypeLeftEyebrowBottom - A set of points that outline the bottom of the left eyebrow. */
    LEFT_EYEBROW_BOTTOM(3),

    /** MLKFaceContourTypeRightEyebrowTop - A set of points that outline the top of the right eyebrow. */
    RIGHT_EYEBROW_TOP(4),

    /** MLKFaceContourTypeRightEyebrowBottom A set of points that outline the bottom of the right eyebrow. */
    RIGHT_EYEBROW_BOTTOM(5),

    /** MLKFaceContourTypeLeftEye - A set of points that outline the left eye. */
    LEFT_EYE(6),

    /** MLKFaceContourTypeRightEye - A set of points that outline the right eye. */
    RIGHT_EYE(7),

    /** MLKFaceContourTypeUpperLipTop - A set of points that outline the top of the upper lip. */
    UPPER_LIP_TOP(8),

    /** MLKFaceContourTypeUpperLipBottom - A set of points that outline the bottom of the upper lip. */
    UPPER_LIP_BOTTOM(9),

    /** MLKFaceContourTypeLowerLipTop - A set of points that outline the top of the lower lip. */
    LOWER_LIP_TOP(10),

    /** MLKFaceContourTypeLowerLipBottom - A set of points that outline the bottom of the lower lip. */
    LOWER_LIP_BOTTOM(11),

    /** MLKFaceContourTypeNoseBridge - A set of points that outline the nose bridge. */
    NOSE_BRIDGE(12),

    /** MLKFaceContourTypeNoseBottom - A set of points that outline the bottom of the nose. */
    NOSE_BOTTOM(13),

    /** MLKFaceContourTypeLeftCheek - A center point on the left cheek. */
    LEFT_CHEEK(14),

    /** MLKFaceContourTypeRightCheek - A center point on the right cheek. */
    RIGHT_CHEEK(15),

    /** Unknown contour type value (indicates an error unless MLKit extends the list of types in the future). */
    UNKNOWN(1002)
}

/** MLKit face detection result object copy of the facial contour type recognized. */
data class FaceContour(

    /** Type of the facial feature contour recognized. */
    val type: FaceContourType,

    /**
     * List of Offset(x, y) points coordinates that outline the facial feature contour of this type relative to the
     * image in the processed bitmap coordinate system.
     */
    val points: List<Offset>
)