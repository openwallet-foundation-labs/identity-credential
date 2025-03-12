package org.multipaz.compose.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

@Composable
expect fun Camera(
    cameraSelector: CameraSelector,
    //onImageReceived: (bitmap: ImageBitmap) -> Unit,
    modifier: Modifier = Modifier,
)
