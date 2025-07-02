package org.multipaz.facematch

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

actual suspend fun getFaceEmbeddings(image: ImageBitmap, imgSize: Int, embeddingDim: Int) =
    withContext(Dispatchers.Default) {
        val imageTensorProcessor = ImageProcessor.Builder()
            .add(ResizeOp(imgSize, imgSize, ResizeOp.ResizeMethod.BILINEAR))
            .add(StandardizeOp())
            .build()

        val modelByteArray = loadModelBytes(ModelConfig.FACE_NET_MODEL_FILE)

        val modelByteBuffer = java.nio.ByteBuffer.allocateDirect(modelByteArray.size).apply {
            put(modelByteArray)
            rewind()
        }

        val interpreterOptions = Interpreter.Options().apply {
            if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                addDelegate(GpuDelegate(CompatibilityList().bestOptionsForThisDevice))
            } else {
                numThreads = 4
            }

            useXNNPACK = true
            useNNAPI = true // Generally recommended if available and model supports it.
        }

        // Pass the ByteBuffer to the Interpreter
        val interpreter = Interpreter(modelByteBuffer, interpreterOptions)
        val inputs = imageTensorProcessor.process(TensorImage.fromBitmap(image.asAndroidBitmap())).buffer
        val outputs = Array(1) { FloatArray(embeddingDim) }

        interpreter.run(inputs, outputs)

        return@withContext outputs[0]
    }

/**
 * Operation to perform points normalization.
 * As in `x_new = ( x - mean ) / std_dev`
 */
private class StandardizeOp : TensorOperator {

    override fun apply(bufferPointerTop: TensorBuffer?): TensorBuffer {
        val pixels = bufferPointerTop!!.floatArray
        val mean = pixels.average().toFloat()
        var std = sqrt(pixels.map { pi -> (pi - mean).pow(2) }.sum() / pixels.size.toFloat())
        std = max(std, 1f / sqrt(pixels.size.toFloat()))
        for (i in pixels.indices) {
            pixels[i] = (pixels[i] - mean) / std
        }
        val output = TensorBufferFloat.createFixedSize(bufferPointerTop.shape, DataType.FLOAT32)
        output.loadArray(pixels)
        return output
    }
}

