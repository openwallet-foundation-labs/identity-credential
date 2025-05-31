package org.multipaz.faces

import androidx.compose.ui.graphics.ImageBitmap
import org.multipaz.compose.camera.CameraFrame

expect fun detectFaces(frameData: CameraFrame): List<FaceObject>?

expect fun detectFaces(image: ImageBitmap): List<FaceObject>?
