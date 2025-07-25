package org.multipaz.facematch

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Extract face embeddings in a normalized values array.
 * The method supposed to be called from the Camera composable onFrameCaptured callback which is run on IO thread.
 *
 * @param image The input image with the upright looking into the camera face of a person to vectorize.
 * @param model The face embeddings model to use.
 *
 * @return The normalized face embeddings vector or null on error.
 */
expect fun getFaceEmbeddings(
    image: ImageBitmap,
    model: FaceMatchLiteRtModel
): FaceEmbedding?
