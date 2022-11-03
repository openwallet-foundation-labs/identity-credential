package com.android.mdl.app

import android.app.Application
import com.google.android.material.color.DynamicColors

class HolderApp: Application() {

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}