package org.multipaz.compose.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun Camera(
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA,
    showPreview: Boolean = true,
    modifier: Modifier = Modifier,
)
