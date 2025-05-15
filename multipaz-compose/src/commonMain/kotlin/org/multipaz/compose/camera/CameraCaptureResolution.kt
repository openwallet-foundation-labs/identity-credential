package org.multipaz.compose.camera

/**
 * Supported [Camera] resolution (frame size) options (as might be needed for different use cases).
 * Camera hardware may not support certain values, thus the actual frame size will be selected automatically
 * according to the camera hardware specifications.
 */
enum class CameraCaptureResolution {
    /** Low resolution, typically 640x480 */
    LOW,
    /** Medium resolution, typically 1280x720 */
    MEDIUM,
    /** High resolution, typically 1920x1080 */
    HIGH
}