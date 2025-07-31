package org.multipaz.facematch

import androidx.compose.ui.graphics.ImageBitmap
import cocoapods.TensorFlowLiteObjC.TFLInterpreter
import cocoapods.TensorFlowLiteObjC.TFLInterpreterOptions
import cocoapods.TensorFlowLiteObjC.TFLTensor
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.multipaz.util.Logger
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

private const val TAG = "FaceMatch"

@OptIn(ExperimentalForeignApi::class, ExperimentalResourceApi::class, BetaInteropApi::class)
private object FaceNetInterpreter {
    private var interpreter: TFLInterpreter? = null
    private var isInitialized = false

    fun isInitialized(): Boolean = isInitialized

    fun initialize(model: FaceMatchLiteRtModel) {
        if (isInitialized) return


        val options = TFLInterpreterOptions().apply {
            useXNNPACK = true
            numberOfThreads = 4u
        }
        val modelFilePath = model.saveToPlatformFile()
            ?: throw Exception("Error: Failed to extract model resource to platform file.")

        val localInterpreter = errorHandled { errPtr ->
            TFLInterpreter(modelFilePath, options, errPtr)
        }

        errorHandled { errPtr ->
            requireNotNull(localInterpreter) { "Interpreter has been closed or not initialized." }
                .allocateTensorsWithError(errPtr)
        }
        interpreter = localInterpreter
        isInitialized = true
    }

    fun getInterpreter(): TFLInterpreter {
        return interpreter ?: throw IllegalStateException("Model not initialized. Call initialize() first.")
    }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalResourceApi::class, BetaInteropApi::class)
actual fun getFaceEmbeddings(image: ImageBitmap, model: FaceMatchLiteRtModel): FaceEmbedding? {
    if (!FaceNetInterpreter.isInitialized()) {
        FaceNetInterpreter.initialize(model)
    }

    val interpreter = FaceNetInterpreter.getInterpreter()


    val inputTensor = interpreter.inputTensorAtIndex(0u, null)
        ?: throw Exception("Error: Failed to get input tensor.")

    val inputImageData: FloatArray = preprocessImage(image, model.imageSquareSize)

    if (inputImageData.isEmpty()) {
        return null
    }

    errorHandled { errPtr ->
        val inputBytes = inputImageData.usePinned { pinnedObj ->
            NSData.dataWithBytes(
                pinnedObj.addressOf(0),
                (inputImageData.size * Float.SIZE_BYTES).toULong()
            )
        }
        inputTensor.copyData(inputBytes, errPtr)
    }

    errorHandled { errPtr ->
        interpreter.invokeWithError(errPtr)
    }

    val outputTensor: TFLTensor? = errorHandled { errPtr ->
        interpreter.outputTensorAtIndex(0u, errPtr)
    }

    val outputNSData: NSData? = errorHandled { errPtr ->
        outputTensor!!.dataWithError(errPtr)
    }

    val outputArray = memScoped {
        val floatPtr = outputNSData!!.bytes?.reinterpret<FloatVar>()
        if (floatPtr == null) {
            Logger.e(TAG, "Error: Failed to get pointer to output data.")
            FloatArray(model.embeddingsArraySize)
        } else {
            val actualFloatCount = (outputNSData.length.toLong() / Float.SIZE_BYTES).toInt()
            val countToRead = minOf(model.embeddingsArraySize, actualFloatCount)
            if (actualFloatCount < model.embeddingsArraySize) {
                println(
                    "Warning: Model output size ($actualFloatCount) is less than expected " +
                            "embeddingDim ($model.embeddingsArraySize)."
                )
            }
            FloatArray(countToRead) { i -> floatPtr[i] }
        }
    }
    return FaceEmbedding(outputArray)

}

/**
 * Preprocessing the image for FaceNet:
 * - Convert to a flat FloatArray.
 * - Normalize/Standardize pixel values.
 */
private fun preprocessImage(image: ImageBitmap, targetSize: Int): FloatArray {
    if (image.width != targetSize || image.height != targetSize) {
        Logger.w(TAG, "Image size mismatch: ${image.width} x ${image.height}")
        return FloatArray(0) // Will ignore the frame (might happen on transition to/from Landscape.
    }
    val width = image.width
    val height = image.height
    val numPixels = width * height
    val intPixels = IntArray(numPixels)

    image.readPixels(buffer = intPixels, startX = 0, startY = 0, width = width, height = height)

    val floatPixels = FloatArray(targetSize * targetSize * 3) // RGB
    var floatIdx = 0

    for (i in 0 until numPixels) {
        val pixel = intPixels[i]
        // Assuming ARGB int format from readPixels
        val r = (pixel shr 16 and 0xFF).toFloat()
        val g = (pixel shr 8 and 0xFF).toFloat()
        val b = (pixel and 0xFF).toFloat()

        // Normalize.
        floatPixels[floatIdx++] = (r - 127.5f) / 127.5f
        floatPixels[floatIdx++] = (g - 127.5f) / 127.5f
        floatPixels[floatIdx++] = (b - 127.5f) / 127.5f
    }

    // Apply standardization
    val mean = floatPixels.average().toFloat()
    var std = sqrt(floatPixels.map { p -> (p - mean).pow(2) }.sum() / floatPixels.size.toFloat())
    std = max(std, 1f / sqrt(floatPixels.size.toFloat()))
    for (i in floatPixels.indices) {
        floatPixels[i] = (floatPixels[i] - mean) / std
    }

    return floatPixels
}