package org.multipaz.compose.camera

/**
 * Common interface for all custom CameraEngine plugins.
 */
interface CameraPlugin {
    /**
     * Initializes the plugin with the provided [CameraEngine].
     *
     * @param cameraEngine The [CameraEngine] instance this plugin will be using.
     */
    fun initialize(cameraEngine: CameraEngine)
}