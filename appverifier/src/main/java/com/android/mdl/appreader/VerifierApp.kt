package com.android.mdl.appreader

import android.app.Application
import com.android.identity.android.util.AndroidLogPrinter
import com.android.identity.util.Logger
import com.google.android.material.color.DynamicColors

class VerifierApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Logger.setLogPrinter(AndroidLogPrinter())
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}