package com.android.identity.android

import android.os.Build

object TestUtil {
    val isRunningOnEmulator: Boolean by lazy {
        Build.PRODUCT.startsWith("sdk")
    }
}