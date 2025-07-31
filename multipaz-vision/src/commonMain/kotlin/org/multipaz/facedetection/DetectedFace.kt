package org.multipaz.facedetection

import androidx.compose.ui.geometry.Rect

/** MLKit face detection result object copy of the recognized face data and facial features as detected. */
data class DetectedFace(
    /**
     * MLKit face detection result object copy of the rectangle containing the detected face relative to the image
     * in the bitmap coordinate system.
     */
    val boundingBox: Rect,

    /** MLKit face detection result object copy of the tracking ID of the recognized face (out of multiple). */
    val trackingId: Int,

    /** MLKit face detection result object copy of the probability of the right eye being open [0-1f]. */
    val rightEyeOpenProbability: Float,

    /** MLKit face detection result object copy of the probability of the left eye being open [0-1f]. */
    val leftEyeOpenProbability: Float,

    /** MLKit face detection result object copy of the probability of the face being smiling [0-1f]. */
    val smilingProbability: Float,

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
    val landmarks: List<FaceLandmark>,

    /** MLKit face detection result object copy of facial contours recognized. Constant size 15 types. */
    val contours: List<FaceContour>
)

