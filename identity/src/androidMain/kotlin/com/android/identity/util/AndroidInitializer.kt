package com.android.identity.util

import android.app.Activity
import android.content.Context
import androidx.fragment.app.FragmentActivity

class AndroidInitializer {
    companion object {
        lateinit var applicationContext: Context

        private lateinit var getCurrentActivity: () -> FragmentActivity?

        val currentActivity: FragmentActivity?
            get() = getCurrentActivity()

        fun initialize(
            applicationContext: Context,
            getCurrentActivity: () -> FragmentActivity?
        ) {
            AndroidInitializer.applicationContext = applicationContext
            AndroidInitializer.getCurrentActivity = getCurrentActivity
        }
    }
}
