package org.multipaz.facedetection

import androidx.compose.ui.geometry.Offset

/**
 * Face landmark types detectable by MLKit.
 * (common IDs to support different Android and iOS implementation of them in MLKit).
 *
 * @property type The common type ID of the landmark.
 */
enum class FaceLandmarkType(val type: Int) {
    /** MLKFaceLandmarkTypeMouthBottom - The center of the bottom lip. */
    MOUTH_BOTTOM(0),

    /** MLKFaceLandmarkTypeMouthRight - The right corner of the mouth. */
    MOUTH_RIGHT(11),

    /** MLKFaceLandmarkTypeMouthLeft - The left corner of the mouth. */
    MOUTH_LEFT(5),

    /** MLKFaceLandmarkTypeRightEye - The right eye. */
    RIGHT_EYE(10),

    /** MLKFaceLandmarkTypeLeftEye - The left eye. */
    LEFT_EYE(4),

    /** MLKFaceLandmarkTypeRightEar - The midpoint of the right ear tip and right ear lobe. */
    RIGHT_EAR(9),

    /** MLKFaceLandmarkTypeLeftEar The midpoint of the left ear tip and left ear lobe. */
    LEFT_EAR(3),

    /** MLKFaceLandmarkTypeRightCheek - The right cheek. */
    RIGHT_CHEEK(7),

    /** MLKFaceLandmarkTypeLeftCheek - The left cheek. */
    LEFT_CHEEK(1),

    /** MLKFaceLandmarkTypeNoseBase The midpoint between the nostrils where the nose meets the face. */
    NOSE_BASE(6),

    /** Unknown landmark value (indicates an error unless MLKit extends the list of types in the future). */
    UNKNOWN(1001)
}


/** MLKit face detection result object copy of the facial landmark type recognized. */
data class FaceLandmark(

    /** Common face Landmark type. */
    val type: FaceLandmarkType,

    /**
     * List of Offset(x, y) points coordinates of the facial feature landmark point of this type relative to the
     * image in the processed bitmap coordinate system.
     */
    val position: Offset
)