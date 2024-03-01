package com.android.identity_credential.wallet.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Convert to Bitmap the specified non-null ByteArray
 */
fun byteArrayToBitmap(bytes: ByteArray?): Bitmap? =
    bytes?.let {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
