package org.multipaz.facematch

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import org.jetbrains.compose.resources.InternalResourceApi
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.TensorOperator
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.support.tensorbuffer.TensorBufferFloat
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

@OptIn(InternalResourceApi::class)
private object FaceNetInterpreter {
    @Volatile
    private var isInitialized = false
    private lateinit var interpreter: Interpreter
    private lateinit var imageTensorProcessor: ImageProcessor

    fun isInitialized(): Boolean = isInitialized

    fun initialize(model: FaceMatchLiteRtModel) {
        if (isInitialized) {
            return
        }

        val modelByteBuffer = ModelByteBufferLoader.getModelByteBuffer(model)
        val imgSize = model.imageSquareSize
        imageTensorProcessor = ImageProcessor.Builder()
            .add(ResizeOp(imgSize, imgSize, ResizeOp.ResizeMethod.BILINEAR))
            .add(StandardizeOp())
            .build()
        val interpreterOptions = Interpreter.Options().apply {
            if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                addDelegate(GpuDelegate(CompatibilityList().bestOptionsForThisDevice))
            } else {
                numThreads = 4
            }
            useXNNPACK = true
            useNNAPI = true
        }
        interpreter = Interpreter(modelByteBuffer, interpreterOptions)
        isInitialized = true
    }

    fun getEmbeddings(image: ImageBitmap, embeddingDim: Int): FloatArray {
        if (!isInitialized) {
            throw IllegalStateException("FaceNetInterpreter has not been initialized. Call initialize() first.")
        }
        val tensorImage = TensorImage.fromBitmap(image.asAndroidBitmap())
        val inputs = imageTensorProcessor.process(tensorImage).buffer
        val outputs = Array(1) { FloatArray(embeddingDim) }
        interpreter.run(inputs, outputs)

        return outputs[0]
    }
}

@OptIn(InternalResourceApi::class)
actual fun getFaceEmbeddings(image: ImageBitmap, model: FaceMatchLiteRtModel): FaceEmbedding? {
    if (!FaceNetInterpreter.isInitialized()) {
        FaceNetInterpreter.initialize(model)
    }
    return FaceEmbedding(FaceNetInterpreter.getEmbeddings(image, model.embeddingsArraySize))
}

/**
 * Operation to perform points normalization.
 * As in `x_new = ( x - mean ) / std_dev`
 */
private class StandardizeOp : TensorOperator {
    override fun apply(bufferPointerTop: TensorBuffer?): TensorBuffer {
        val buffer = bufferPointerTop ?: throw IllegalArgumentException("Input TensorBuffer cannot be null.")
        val pixels = buffer.floatArray
        if (pixels.isEmpty()) return buffer

        val mean = pixels.average().toFloat()
        var stdDev = sqrt(pixels.sumOf { (it - mean).toDouble().pow(2) } / pixels.size).toFloat()
        stdDev = max(stdDev, 1f / sqrt(pixels.size.toFloat()))
        val standardizedPixels = FloatArray(pixels.size) { i ->
            (pixels[i] - mean) / stdDev
        }
        val output = TensorBufferFloat.createFixedSize(buffer.shape, DataType.FLOAT32)
        output.loadArray(standardizedPixels)
        return output
    }
}

