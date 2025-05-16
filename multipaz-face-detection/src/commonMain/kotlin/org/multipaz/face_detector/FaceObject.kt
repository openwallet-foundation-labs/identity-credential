package org.multipaz.face_detector

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

data class FaceLandmark(
    val type: Int,
    val position: Offset
)

data class FaceContour(
    val type: Int,
    val points: List<Offset>
)

data class FaceObject(
    val boundingBox: Rect,
    val trackingId: Int?, // Mapped from zzb, nullable based on getTrackingId()
    val rightEyeOpenProbability: Float?, // Mapped from zzc, nullable based on getRightEyeOpenProbability()
    val leftEyeOpenProbability: Float?,  // Mapped from zzd, nullable based on getLeftEyeOpenProbability()
    val smilingProbability: Float?,      // Mapped from zze, nullable based on getSmilingProbability()
    val headEulerAngleX: Float,        // Mapped from zzf
    val headEulerAngleY: Float,        // Mapped from zzg
    val headEulerAngleZ: Float,        // Mapped from zzh
    val landmarks: List<FaceLandmark>, // Mapped from zzi (SparseArray of FaceLandmark)
    val contours: List<FaceContour>    // Mapped from zzj (SparseArray of FaceContour)
)

