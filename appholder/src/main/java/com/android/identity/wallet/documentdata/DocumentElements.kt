package com.android.identity.wallet.documentdata

import android.graphics.Bitmap

data class DocumentElements(
    val text: String = "",
    val portrait: Bitmap? = null,
    val signature: Bitmap? = null,
    val fingerprint: Bitmap? = null,
    val requestUserAuthorization: Boolean = false,
    val passphrase: String = "",
)
