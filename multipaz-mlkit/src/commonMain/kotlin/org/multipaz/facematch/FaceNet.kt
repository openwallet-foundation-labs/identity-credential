package org.multipaz.facematch

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Extract face embeddings in a normalized values array.
 * The method supposed to be called from the Camera composable onFrameCaptured callback which is run on IO thread.
 *
 * @param image The input image.
 * @param imgSize The image size (square side size in pixels; 160 for the default model).
 * @param embeddingDim The embeddings array size (512 for the default model).
 *
 */
expect fun getFaceEmbeddings(
    image: ImageBitmap,
    imgSize: Int = LRTModelLoader.FACE_NET_MODEL_IMAGE_SIZE,
    embeddingDim: Int = LRTModelLoader.FACE_NET_MODEL_EMBEDDINGS_SIZE
): FloatArray?
