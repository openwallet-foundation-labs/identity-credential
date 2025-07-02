package org.multipaz.facematch

import org.jetbrains.compose.resources.InternalResourceApi
import org.multipaz.context.applicationContext
import org.multipaz.util.Logger
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

private const val TAG = "LiteRtModel"

internal actual fun saveBytesToFileSystem(
    fileName: String,
    data: ByteArray,
    forceOverwrite: Boolean
): String? {

    // Using cache directory as it's generally writable and auto-cleaned by the OS if needed.
    // For more persistent files, use context.filesDir.
    val outputDir = applicationContext.cacheDir // Or context.filesDir for more permanent storage.
    val outputFile = File(outputDir, fileName)

    try {
        if (outputFile.exists() && !forceOverwrite) { // Don't overwrite if already exists.
            return outputFile.absolutePath
        }

        FileOutputStream(outputFile).use { fos ->
            fos.write(data)
        }
        return outputFile.absolutePath
    } catch (e: Exception) {
        Logger.e(TAG, "Error saving file '$fileName' to Android file system.", e)
        e.printStackTrace()
        return null
    }
}

@OptIn(InternalResourceApi::class)
internal object ModelByteBufferLoader {
    private var _modelByteBuffer: ByteBuffer? = null

    internal fun getModelByteBuffer(model: FaceMatchLiteRtModel): ByteBuffer =
        if (_modelByteBuffer == null) {
            Logger.d("ModelLoader", "ModelByteBuffer is null, loading model...")
            val modelByteArray = model.modelData.toByteArray()
            _modelByteBuffer = ByteBuffer.allocateDirect(modelByteArray.size).apply {
                put(modelByteArray)
                rewind() // Crucial: prepares the buffer for reading after writing
            }
            Logger.d("ModelLoader", "Model loaded and ByteBuffer initialized.")
            _modelByteBuffer!!
        } else {
            _modelByteBuffer!!
        }
}