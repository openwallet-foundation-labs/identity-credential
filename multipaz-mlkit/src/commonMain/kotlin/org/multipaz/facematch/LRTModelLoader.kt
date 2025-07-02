package org.multipaz.facematch

import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.InternalResourceApi
import org.multipaz.multipaz_mlkit.generated.resources.Res
import org.multipaz.util.Logger

private const val TAG = "LRTModelLoader"

/** Utility object for platform specific LiteRT model file operations. */
object LRTModelLoader {
    val FACE_NET_MODEL_FILE = Path("files/facenet_512.tflite")
    const val FACE_NET_MODEL_IMAGE_SIZE = 160 // Pixels.
    const val FACE_NET_MODEL_EMBEDDINGS_SIZE = 512

    /**
     * Extract data from the resource file to a ByteArray.
     */
    @OptIn(ExperimentalResourceApi::class)
    fun extractModelResourceToByteArray() =  runBlocking {  Res.readBytes(FACE_NET_MODEL_FILE.toString()) }

    /**
     * Write data to physical file on the platform.
     *
     * @param forceOverwrite If true, overwrite the destination file even if it exists (false).
     *
     * @return The absolute path to the saved file, or null on error.
     */
    @OptIn(ExperimentalResourceApi::class, InternalResourceApi::class)
    fun extractModelResourceToPlatformFile(
        forceOverwrite: Boolean = false
    ): String? =
        try {
            val resourceBytes = extractModelResourceToByteArray()
            val platformFilePath = saveBytesToFileSystem(
                FACE_NET_MODEL_FILE.name,
                resourceBytes, forceOverwrite
            )
            platformFilePath
        } catch (e: Exception) {
            Logger.e(TAG, "Error copying resource '$FACE_NET_MODEL_FILE'", e)
            null
        }

}

/**
 * Save bytes to the platform's file system. So the file can be used by iOS LiteRT API requiring its physical path.
 * (KMP iOS impl. of the common binary resource is copied to the iOS app bundle only, so no file Path available).
 *
 * @param fileName The name of the file to save.
 * @param data The byte array to write to the file.
 * @param forceOverwrite If true, overwrite the destination file even if it exists (false).
 *
 * @return The absolute path to the saved file, or null on error.
 */
internal expect fun saveBytesToFileSystem(
    fileName: String,
    data: ByteArray,
    forceOverwrite: Boolean = false
): String?