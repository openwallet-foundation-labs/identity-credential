package org.multipaz.facematch


import androidx.compose.ui.graphics.ImageBitmap
import cocoapods.TensorFlowLiteObjC.TFLInterpreter
import cocoapods.TensorFlowLiteObjC.TFLInterpreterOptions
import cocoapods.TensorFlowLiteObjC.TFLTensor
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alignOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.multipaz.multipaz_mlkit.generated.resources.Res
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.text.toLong

@OptIn(ExperimentalForeignApi::class, ExperimentalResourceApi::class, BetaInteropApi::class)
actual suspend fun getFaceEmbeddings(image: ImageBitmap, imgSize: Int, embeddingDim: Int) =
    withContext(Dispatchers.Default) {
        val resizedImageBitmap = image.resize(imgSize, imgSize) //todo: mine is already resized.
        val options = TFLInterpreterOptions().apply {
            useXNNPACK = true
            numberOfThreads = 4u
        }
        val modelFilePath = Res.getUri(ModelConfig.FACE_NET_MODEL_FILE)

        ///val delegate = TFLMetalDelegate()

        val interpreter = errorHandled { errPtr ->
            TFLInterpreter(modelFilePath, options, errPtr)
        }

        errorHandled { errPtr ->
            requireNotNull(interpreter) { "Interpreter has been closed or not initialized." }
                .allocateTensorsWithError(errPtr)
        }

        val inputTensor = interpreter!!.inputTensorAtIndex(0u, null)
        if (inputTensor == null) {
            throw Exception("Error: Failed to get input tensor.")
        }
        val inputImageData: FloatArray =
            preprocessImage(image.resize(imgSize, imgSize), imgSize)

        // Copy data to the input tensor.

        errorHandled { errPtr ->
            val inputBytes = inputImageData.usePinned { pinnedObj ->
                NSData.dataWithBytes(pinnedObj.addressOf(0), (inputImageData.size * Float.SIZE_BYTES).toULong())
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
                println("Error: Failed to get pointer to output data.")
                FloatArray(embeddingDim)
            } else {
                val actualFloatCount = (outputNSData.length.toLong() / Float.SIZE_BYTES).toInt()
                val countToRead = minOf(embeddingDim, actualFloatCount)
                if (actualFloatCount < embeddingDim) {
                    println("Warning: Model output size ($actualFloatCount) is less than expected " +
                            "embeddingDim ($embeddingDim).")
                }
                FloatArray(countToRead) { i -> floatPtr[i] }
            }
        }
        return@withContext outputArray
    }

/**
 * Placeholder for resizing an ImageBitmap.
 * You'll need to implement this using KMP or iOS-specific image libraries.
 */
private fun ImageBitmap.resize(newWidth: Int, newHeight: Int): ImageBitmap {
    // TODO: Implement image resizing logic
    // This might involve platform-specific calls or a KMP library.
    println("Warning: Image resizing not yet implemented for iOS.")
    return this // Return original for now
}

/**
 * Placeholder for preprocessing the image:
 * - Convert to a flat FloatArray.
 * - Normalize/Standardize pixel values.
 * The order of operations (e.g., channel order R-G-B vs B-G-R) and normalization
 * scheme must match what your TensorFlow Lite model expects.
 */
private fun preprocessImage(image: ImageBitmap, targetSize: Int): FloatArray {
    // TODO: Implement image preprocessing for iOS
    // 1. Convert ImageBitmap to pixel array (e.g., FloatArray).
    //    Consider the pixel format (ARGB, RGBA, etc.) and channel order.
    // 2. Apply standardization (similar to your StandardizeOp).

    // Example (very simplified, assuming RGB and normalization similar to your Android code):
    val width = image.width
    val height = image.height
    // This is a naive way to get pixel data. You'll likely need a more robust method.
    val intPixels = IntArray(width * height)
    // image.readPixels(intPixels) // This API might not be available directly or work as expected on iOS with compose ImageBitmap

    val floatPixels = FloatArray(targetSize * targetSize * 3) // Assuming 3 channels (RGB)

    // --- You MUST implement proper pixel extraction and resizing here ---
    // The following is a placeholder and likely incorrect for direct use.
    var p = 0
    for (i in 0 until targetSize) {
        for (j in 0 until targetSize) {
            // This assumes you have already resized the image and can access pixel values.
            // You'll need to extract R, G, B components and normalize them.
            // val pixel = intPixels[i * width + j] // Example, if you had original pixels
            // val r = (pixel shr 16 and 0xFF)
            // val g = (pixel shr 8 and 0xFF)
            // val b = (pixel and 0xFF)
            // floatPixels[p++] = (r - 127.5f) / 127.5f // Example normalization
            // floatPixels[p++] = (g - 127.5f) / 127.5f
            // floatPixels[p++] = (b - 127.5f) / 127.5f
        }
    }
    println("Warning: Image preprocessing (pixel extraction, standardization) not yet fully implemented for iOS.")

    // Apply standardization (similar to your StandardizeOp logic)
    val mean = floatPixels.average().toFloat()
    var std = sqrt(floatPixels.map { pi -> (pi - mean).pow(2) }.sum() / floatPixels.size.toFloat())
    std = max(std, 1f / sqrt(floatPixels.size.toFloat()))
    for (i in floatPixels.indices) {
        floatPixels[i] = (floatPixels[i] - mean) / std
    }

    return floatPixels
}