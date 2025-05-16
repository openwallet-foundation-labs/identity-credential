package org.multipaz.face_detector

import androidx.compose.ui.graphics.ImageBitmap
import org.multipaz.compose.camera.CameraFrame

expect fun detectFaces(frameData: CameraFrame): FaceData?

expect fun detectFaces(image: ImageBitmap): FaceData?

data class FaceData(
    val faces: List<FaceObject>,
    val width: Int,
    val height: Int,
    val rotation: Int,
)