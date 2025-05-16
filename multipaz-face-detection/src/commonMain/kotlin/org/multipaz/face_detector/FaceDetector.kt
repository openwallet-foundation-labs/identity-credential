package org.multipaz.face_detector

import androidx.compose.ui.graphics.ImageBitmap
import org.multipaz.compose.camera.CameraFrame

expect fun detectFaces(frameData: CameraFrame): FaceData?

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class FaceData(faces: List<FaceObject>) {
    val faces: List<FaceObject>
}