package org.multipaz.compose.camera

import org.multipaz.compose.camera.CameraBuilder
import org.multipaz.compose.camera.CameraPlugin
import org.multipaz.compose.camera.CameraSelection

/**
 * iOS platform-specific implementation of [CameraBuilder].
 */
class IosCameraBuilder : CameraBuilder {

    private var cameraSelection: CameraSelection = CameraSelection.DEFAULT_BACK_CAMERA
    private var cameraPreview = false
    private val plugins = mutableListOf<CameraPlugin>()

    override fun setCameraLens(cameraSelection: CameraSelection): CameraBuilder {
        this.cameraSelection = cameraSelection
        return this
    }

    override fun showPreview(cameraPreview: Boolean): CameraBuilder {
        this.cameraPreview = cameraPreview
        return this
    }

    override fun addPlugin(plugin: CameraPlugin): CameraBuilder {
        plugins.add(plugin)
        return this
    }

    override fun build(): CameraEngine {
        val cameraEngine = CameraEngine(
            cameraSelection = cameraSelection,
            showPreview = cameraPreview,
            plugins = plugins
        )

        return cameraEngine
    }
}