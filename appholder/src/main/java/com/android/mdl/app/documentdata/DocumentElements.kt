package com.android.mdl.app.documentdata

import android.graphics.Bitmap

data class DocumentElements(
    val text: String = "",
    val portrait: Bitmap? = null,
    val signature: Bitmap? = null,
    val requestUserAuthorization: Boolean = false
)