package org.multipaz.compose.camera

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import org.multipaz.compose.camera.CameraBuilder
import org.multipaz.compose.camera.CameraPlugin
import org.multipaz.compose.camera.CameraSelection

/**
 * Android-specific implementation of [CameraBuilder].
 *
 * @param context The Android [Context], typically an Activity or Application context.
 * @param lifecycleOwner The [LifecycleOwner], usually the hosting Activity or Fragment.
 */
class AndroidCameraBuilder(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) : CameraBuilder {

    private var cameraSelection = CameraSelection.DEFAULT_BACK_CAMERA
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
            context = context,
            lifecycleOwner = lifecycleOwner,
            cameraSelection = cameraSelection,
            showPreview = cameraPreview,
            plugins = plugins,
        )

        plugins.forEach {
            it.initialize(cameraEngine)
        }

        return cameraEngine
    }
}