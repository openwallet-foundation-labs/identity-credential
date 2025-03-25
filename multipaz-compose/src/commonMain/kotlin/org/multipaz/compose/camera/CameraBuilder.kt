package org.multipaz.compose.camera

/**
 * Construct a CameraEngine component with customizable configuration and plugins for a particular use case.
 */
interface CameraBuilder {
    /**
     * Sets the camera lens (front or back) for the [CameraEngine].
     *
     * @param cameraSelection The camera lens ID to set
     * @see: [CameraSelection].
     */
    fun setCameraLens(cameraSelection: CameraSelection): CameraBuilder

    /**
     * Sets the camera preview visibility for the Camera composable.
     *
     * @param cameraPreview Whether to show the camera preview or not.
     */
    fun showPreview(cameraPreview: Boolean = false): CameraBuilder

    /**
     * Adds a [CameraPlugin] to the [CameraEngine].
     *
     * @param plugin The plugin to add.
     * @return The current instance of [CameraBuilder].
     */
    fun addPlugin(plugin: CameraPlugin): CameraBuilder

    /**
     * Builds a [CameraEngine] object with the provided configurations and plugins.
     *
     * @return The fully constructed [CameraEngine] object.
     */
    fun build(): CameraEngine
}