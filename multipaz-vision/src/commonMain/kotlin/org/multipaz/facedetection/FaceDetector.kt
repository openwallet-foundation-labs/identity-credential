package org.multipaz.facedetection

import androidx.compose.ui.graphics.ImageBitmap
import org.multipaz.compose.camera.CameraFrame

/**
 * Use MLKit to detect faces in the given [CameraFrame].
 *
 * @param frameData The camera frame data containing the original platform image bitmap reference and frame geometry
 *    data as taken on the device.
 *
 * @return A list of [DetectedFace] representing the detected faces and their features in common format, or null if no
 * faces were detected.
 */
expect fun detectFaces(frameData: CameraFrame): List<DetectedFace>?

/**
 * Use MLKit to detect faces in the given [ImageBitmap].
 *
 * @param image The [ImageBitmap] to detect faces in. The face should be relatively upright (45 deg max deviation from
 *     vertical in the image for MLKit detector to work reliably).
 *
 * @return A list of [DetectedFace] representing the detected faces, or null if no faces were detected.
 */
expect fun detectFaces(image: ImageBitmap): List<DetectedFace>?
