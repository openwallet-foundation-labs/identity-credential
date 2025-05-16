package org.multipaz.face_detector

import androidx.compose.ui.graphics.ImageBitmap
import org.multipaz.compose.camera.CameraFrame

// Placeholders until MLKit Cocoapods ready.

actual fun detectFaces(frameData: CameraFrame): FaceData? {
    TODO("Not yet implemented")
}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class FaceData actual constructor(actual val faces: List<FaceObject>)