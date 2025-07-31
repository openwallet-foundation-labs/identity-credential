package org.multipaz.facematch

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The normalized multidimensional face embeddings vector uniquely describing the face features
 * per LiteRT model (e.g. FaceNet).
 *
 * @param embedding The normalized multidimensional face embeddings vector as obtained form LiteRT.
 */
data class FaceEmbedding(val embedding: FloatArray) {
    /**
     * Calculate cosine similarity between two normalized vectors as `similarity = (A ⋅ B) / (||A|| * ||B||)`.
     *
     * @param otherEmbeddings The other normalized embeddings vector to calculate similarity with.
     *
     * @return The cosine similarity between the two normalized vectors in the multidimensional space.
     *   See the rationale explained here: https://facerec.gjung.com/ComparingFaces
     * - Value of 1: The angle between the vectors is 0 degrees. This means the vectors point in the exact same
     *   direction. In the context of face embeddings, this indicates maximum similarity – the two faces are
     *   considered identical by the model.
     * - Value of 0: The angle is 90 degrees. The vectors are orthogonal (perpendicular). This means they are
     *   independent or have no similarity in terms of direction. For face embeddings, this would mean the faces
     *   are very different.
     * - Value of -1: The angle is 180 degrees. The vectors point in opposite directions. This indicates maximum
     *   dissimilarity.•Values between -1 and 1 represent varying degrees of similarity or dissimilarity.
     *
     * In terms of faces matching values above 0.4f should be considered as a "good match" result for given faces.
     */
    fun calculateSimilarity(otherEmbeddings: FaceEmbedding): Float {
        val otherEmbedding = otherEmbeddings.embedding
        if (embedding.size != otherEmbedding.size) {
            throw IllegalArgumentException("Vectors must have the same size.")
        }

        var mag1 = 0.0f // Stores the sum of squares for ||A||^2
        var mag2 = 0.0f // Stores the sum of squares for ||B||^2
        var product = 0.0f // Stores the dot product (A ⋅ B)

        for (i in embedding.indices) {
            mag1 += this.embedding[i].pow(2)
            mag2 += otherEmbedding[i].pow(2)
            product += this.embedding[i] * otherEmbedding[i]
        }
        mag1 = sqrt(mag1)
        mag2 = sqrt(mag2)
        return product / (mag1 * mag2)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as FaceEmbedding

        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        return embedding.contentHashCode()
    }
}
