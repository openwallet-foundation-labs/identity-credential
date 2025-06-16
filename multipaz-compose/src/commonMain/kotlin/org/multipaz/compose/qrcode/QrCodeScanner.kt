package org.multipaz.compose.qrcode

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.multipaz.compose.camera.CameraCaptureResolution
import org.multipaz.compose.camera.CameraSelection

/**
 * A composable for scanning QR codes.
 *
 * On iOS this is implemented using the platform Vision API and on Android it's using the
 * [ZXing](https://github.com/zxing/zxing) library.
 *
 * This requires camera permission, see [org.multipaz.compose.permissions.rememberCameraPermissionState].
 *
 * @param modifier The composition modifier to apply to the composable.
 * @param cameraSelection The desired hardware camera to use.
 * @param captureResolution The desired resolution to use for the camera capture.
 * @param showCameraPreview Whether to show the preview of the captured frame within the composable.
 * @param onCodeScanned a callback which will be called when a QR code has been detected (in which
 *   case the contents are passed) and when a QR code is no longer detected (in which case `null`
 *   is passed). Will never be called with `null` multiple times in a row.
 */
@Composable
expect fun QrCodeScanner(
    modifier: Modifier = Modifier,
    cameraSelection: CameraSelection,
    captureResolution: CameraCaptureResolution,
    showCameraPreview: Boolean,
    onCodeScanned: (qrCode: String?) -> Unit
)
