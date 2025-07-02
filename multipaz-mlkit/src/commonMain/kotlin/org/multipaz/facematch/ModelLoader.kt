package org.multipaz.facematch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.multipaz.multipaz_mlkit.generated.resources.Res

@OptIn(ExperimentalResourceApi::class)
suspend fun loadModelBytes(fileName: String): ByteArray = withContext(Dispatchers.IO) {
    return@withContext Res.readBytes(fileName)
}

object ModelConfig {
    const val FACE_NET_MODEL_FILE = "files/facenet_512.tflite"
}