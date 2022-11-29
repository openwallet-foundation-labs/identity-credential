package com.android.mdl.appreader

import android.app.Application
import com.google.android.material.color.DynamicColors

class VerifierApp : Application() {

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}