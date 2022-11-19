package com.android.mdl.app.util

import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

fun Context.mainExecutor(): Executor {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        mainExecutor
    } else {
        ContextCompat.getMainExecutor(applicationContext)
    }
}