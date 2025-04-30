package org.multipaz.face_detector

import androidx.compose.ui.graphics.ImageBitmap

expect suspend fun detectFace(bitmap: ImageBitmap): ImageBitmap
