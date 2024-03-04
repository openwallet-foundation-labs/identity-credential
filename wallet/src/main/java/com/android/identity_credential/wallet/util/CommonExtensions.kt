package com.android.identity_credential.wallet.util

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream


/**
 * Convert the given context to a ComponentActivity
 */
fun Context.getActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}


/**
 * Convert a Drawable fetched from resources to bytes.
 */
fun Drawable?.toByteArray(): ByteArray? = (this as BitmapDrawable).bitmap.let { bitmap ->
    ByteArrayOutputStream().run {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, this)
        toByteArray()
    }
}

/**
 * Convert a ByteArray to ImageBitmap.
 */
fun ByteArray.toImageBitmap(): ImageBitmap =
    BitmapFactory.decodeByteArray(this, 0, size).asImageBitmap()