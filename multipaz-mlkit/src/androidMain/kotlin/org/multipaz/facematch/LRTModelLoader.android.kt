package org.multipaz.facematch

import android.content.Context
import org.jetbrains.compose.resources.InternalResourceApi
import org.multipaz.facematch.LRTModelLoader.extractModelResourceToByteArray
import org.multipaz.util.Logger
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

private const val TAG = "ModelLoader"

object AppContextHolder {
    private var _appContext: Context? = null // Private nullable backing field

    /** Property to set the context. */
    var appContext: Context
        get() {
            return _appContext
                ?: throw UninitializedPropertyAccessException("ApplicationContext has not been initialized.")
        }
        set(value) {
            if (_appContext == null) {
                _appContext = value
            } else {
                Logger.w(TAG, "Warning: AppContextHolder.appContext is being re-initialized.")
                _appContext = value
            }
        }

    /** Check if the holer is initialized. */
    fun isInitialized(): Boolean {
        return _appContext != null
    }
}

internal actual fun saveBytesToFileSystem(
    fileName: String,
    data: ByteArray,
    forceOverwrite: Boolean
): String? {
    if (!AppContextHolder.isInitialized()) {
        Logger.e(TAG, "Error: ApplicationContext not initialized in AppContextHolder.")
        return null
    }
    val context: Context = AppContextHolder.appContext
    // Using cache directory as it's generally writable and auto-cleaned by the OS if needed.
    // For more persistent files, use context.filesDir.
    val outputDir = context.cacheDir // Or context.filesDir for more permanent storage.
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
object ModelByteBufferLoader {
    private var _modelByteBuffer: ByteBuffer? = null

    fun getModelByteBuffer(): ByteBuffer =
        if (_modelByteBuffer == null) {
            Logger.d("ModelLoader", "ModelByteBuffer is null, loading model...")
            // Assuming extractModelResourceToByteArray is a suspend function
            val modelByteArray = extractModelResourceToByteArray()
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