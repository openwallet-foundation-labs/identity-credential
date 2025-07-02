package org.multipaz.facematch

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Extract face embeddings in a normalized values array.
 *
 * @param image The input image.
 * @param imgSize The image size (square side size in pixels; 160 fore the default model).
 * @param embeddingDim The embeddings array size (512 for the default model).
 *
 */
expect suspend fun getFaceEmbeddings(image: ImageBitmap, imgSize: Int = 160, embeddingDim: Int = 512): FloatArray
