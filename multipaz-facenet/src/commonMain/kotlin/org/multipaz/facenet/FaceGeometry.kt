package org.multipaz.facenet

/**
 * 2D point representation for facial landmarks.
 */
data class FacePoint2D(val x: Double, val y: Double) {
    constructor(x: Float, y: Float) : this(x.toDouble(), y.toDouble())
}

/**
 * 2D bounding box representation for detected faces.
 */
data class FaceBoundingBox(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double
) {
    val right: Double get() = left + width
    val bottom: Double get() = top + height
    val centerX: Double get() = left + width / 2.0
    val centerY: Double get() = top + height / 2.0
}

/**
 * 2D similarity transform (uniform scale, rotation, and translation) mapping source image
 * coordinates `(x, y)` to aligned target crop coordinates `(x', y')`:
 *
 * `x' = a * x - b * y + tx`
 * `y' = b * x + a * y + ty`
 *
 * where `a = scale * cos(theta)` and `b = scale * sin(theta)`.
 */
data class SimilarityTransform2D(
    val a: Double,
    val b: Double,
    val tx: Double,
    val ty: Double
)

/**
 * Computes the optimal 2D similarity transform (Umeyama / Procrustes least-squares alignment)
 * from the 4 central BlazeFace landmarks ([rightEye], [leftEye], [noseTip], [mouthCenter])
 * to a canonical square target face template of size [targetSize] x [targetSize].
 *
 * Unlike 2-point eye-only alignment, anchoring across the eyes, nose tip, and mouth center
 * stabilizes crop scale and vertical positioning when eyewear frames or lens refraction
 * perturb the detected eye keypoints.
 *
 * Returns `null` if the landmarks are degenerate (collinear or collapsed).
 */
fun computeFaceAlignmentTransform(
    rightEye: FacePoint2D,
    leftEye: FacePoint2D,
    noseTip: FacePoint2D,
    mouthCenter: FacePoint2D,
    targetSize: Int
): SimilarityTransform2D? {
    val eyeDist = kotlin.math.hypot(leftEye.x - rightEye.x, leftEye.y - rightEye.y)
    if (eyeDist <= 1.0 || targetSize <= 0) return null

    val s = targetSize.toDouble()
    // Canonical normalized template coordinates in [0, 1] derived from standard ArcFace/InsightFace
    // 112x112 landmarks (where mouthCenter is the midpoint of left and right mouth corners):
    //   rightEye (viewer's left): (38.2946 / 112, 51.6963 / 112) = (0.341916, 0.461574)
    //   leftEye  (viewer's right): (73.5318 / 112, 51.5014 / 112) = (0.656534, 0.459834)
    //   noseTip:                  (56.0252 / 112, 71.7366 / 112) = (0.500225, 0.640505)
    //   mouthCenter:              (56.1396 / 112, 92.2848 / 112) = (0.501246, 0.823971)
    val srcX = doubleArrayOf(rightEye.x, leftEye.x, noseTip.x, mouthCenter.x)
    val srcY = doubleArrayOf(rightEye.y, leftEye.y, noseTip.y, mouthCenter.y)
    val dstX = doubleArrayOf(0.341916 * s, 0.656534 * s, 0.500225 * s, 0.501246 * s)
    val dstY = doubleArrayOf(0.461574 * s, 0.459834 * s, 0.640505 * s, 0.823971 * s)

    var meanSrcX = 0.0
    var meanSrcY = 0.0
    var meanDstX = 0.0
    var meanDstY = 0.0
    for (i in 0 until 4) {
        meanSrcX += srcX[i]
        meanSrcY += srcY[i]
        meanDstX += dstX[i]
        meanDstY += dstY[i]
    }
    meanSrcX /= 4.0
    meanSrcY /= 4.0
    meanDstX /= 4.0
    meanDstY /= 4.0

    var srcVar = 0.0
    var sumA = 0.0
    var sumB = 0.0
    for (i in 0 until 4) {
        val sx = srcX[i] - meanSrcX
        val sy = srcY[i] - meanSrcY
        val dx = dstX[i] - meanDstX
        val dy = dstY[i] - meanDstY
        srcVar += sx * sx + sy * sy
        sumA += sx * dx + sy * dy
        sumB += sx * dy - sy * dx
    }

    if (srcVar <= 1e-6) return null

    val a = sumA / srcVar
    val b = sumB / srcVar
    val tx = meanDstX - (a * meanSrcX - b * meanSrcY)
    val ty = meanDstY - (b * meanSrcX + a * meanSrcY)

    return SimilarityTransform2D(a = a, b = b, tx = tx, ty = ty)
}

