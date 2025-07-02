package org.multipaz.facematch

import kotlinx.io.bytestring.ByteString
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.InternalResourceApi
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.util.Logger

private const val TAG = "LiteRtModel"

/**
 * Data class for the the platform independent LiteRT model handling.
 *
 * @param modelData The LiteRT model file data need to be extracted on the client side to the ByteString from the
 *    FlatBuffer mode file. The compatible model file for LiteRT can be obtained from GitHub project or converted
 *    from other models available online (see https://ai.google.dev/edge/litert for more details on that).
 * @param imageSquareSize The input image (square) size in pixels. Dictated by the particular Model capabilities.
 * @param embeddingsArraySize The size of the embeddings array produced by the particular Model.
 */
data class FaceMatchLiteRtModel(val modelData: ByteString, val imageSquareSize: Int, val embeddingsArraySize: Int) {
    /**
     * Save model data to physical file on the platform file system.
     * Required for the iOS LiteRT KMP API. Optional for Android.
     * The unique internal file name for the model is generated as 65 characters "m${sha256(modelData)}".
     *
     * @param forceOverwrite If true, overwrite the destination file even if it exists (false).
     *
     * @return The absolute path to the saved file, or null on error.
     */
    @OptIn(ExperimentalResourceApi::class, InternalResourceApi::class, ExperimentalStdlibApi::class)
    internal fun saveToPlatformFile(forceOverwrite: Boolean = false): String? {
        val modelBytes = modelData.toByteArray()
        val fileName = "m${Crypto.digest(Algorithm.SHA256, modelBytes).toHexString()}"

        return try {
            saveBytesToFileSystem(fileName, modelBytes, forceOverwrite)
        } catch (e: Exception) {
            Logger.e(TAG, "Error saving model file '$fileName", e)
            null
        }
    }
}

/**
 * Save Model bytes to the platform file system.
 *
 * For the iOS impl. of LiteRT in KMP the model must be passed by the physical path to the file with the Model dat.
 * Optional for Android.
 *
 * @param fileName The name of the file to save.
 * @param data The byte array to write to the file.
 * @param forceOverwrite If true, overwrite the destination file even if it exists (default is 'false' for reuse).
 *
 * @return The absolute path to the saved file, or null on error.
 */
internal expect fun saveBytesToFileSystem(
    fileName: String,
    data: ByteArray,
    forceOverwrite: Boolean = false
): String?