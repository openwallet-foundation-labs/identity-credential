package org.multipaz.compose.camera

/** Supported cameras. */
enum class CameraSelection {
    /** Camera facing the user (taking selfie). */
    DEFAULT_FRONT_CAMERA,
    /** Camera facing away from the user (taking a photo or portrait). */
    DEFAULT_BACK_CAMERA,
}

/** Helper definitions of the camera bitmap image mirroring feature On. */
fun CameraSelection.isMirrored() = when (this) {
    CameraSelection.DEFAULT_BACK_CAMERA -> false
    CameraSelection.DEFAULT_FRONT_CAMERA -> true
}