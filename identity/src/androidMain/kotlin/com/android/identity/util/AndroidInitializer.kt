package com.android.identity.util

import android.content.Context

class AndroidInitializer {
    companion object {
        lateinit var applicationContext: Context

        fun initialize(applicationContext: Context) {
            AndroidInitializer.applicationContext = applicationContext
        }
    }
}
