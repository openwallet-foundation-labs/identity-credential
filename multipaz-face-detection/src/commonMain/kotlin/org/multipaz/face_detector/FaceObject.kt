package org.multipaz.face_detector

data class MRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class MPointF(
    val x: Float,
    val y: Float
)

data class MFaceLandmark(
    val type: Int,
    val position: MPointF
)

data class MFaceContour(
    val type: Int,
    val points: List<MPointF>
)

data class FaceObject(
    val boundingBox: MRect,
    val trackingId: Int?, // Mapped from zzb, nullable based on getTrackingId()
    val rightEyeOpenProbability: Float?, // Mapped from zzc, nullable based on getRightEyeOpenProbability()
    val leftEyeOpenProbability: Float?,  // Mapped from zzd, nullable based on getLeftEyeOpenProbability()
    val smilingProbability: Float?,      // Mapped from zze, nullable based on getSmilingProbability()
    val headEulerAngleX: Float,        // Mapped from zzf
    val headEulerAngleY: Float,        // Mapped from zzg
    val headEulerAngleZ: Float,        // Mapped from zzh
    val landmarks: List<MFaceLandmark>, // Mapped from zzi (SparseArray of FaceLandmark)
    val contours: List<MFaceContour>    // Mapped from zzj (SparseArray of FaceContour)
)

