package org.multipaz.compose.camera

/**
 * CameraEngine operations API
 */
expect class CameraEngine {
    /**
     * Starts the camera session.
     */
    fun startSession()

    /**
     * Stops the camera session.
     */
    fun stopSession()

    /**
     * Initialize/reset camera plugins (i.e.face recognition, face matcher, bar code scanner).
     */
    fun initializePlugins()

    /** Retrieve aspect ratio from sensor size */
    fun getAspectRatio(): Double

}