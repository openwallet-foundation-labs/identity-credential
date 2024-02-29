package com.android.identity_credential.wallet.util

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity

/**
 * Attempt to get a ComponentActivity or Activity from a context
 */
fun Context.getActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}