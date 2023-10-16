package com.android.identity.wallet

import android.app.Application
import com.android.identity.android.util.AndroidLogPrinter
import com.android.identity.util.Logger
import com.android.identity.wallet.util.PeriodicKeysRefreshWorkRequest
import com.android.identity.wallet.util.PreferencesHelper
import com.google.android.material.color.DynamicColors

class HolderApp: Application() {

    override fun onCreate() {
        super.onCreate()
        Logger.setLogPrinter(AndroidLogPrinter())
        DynamicColors.applyToActivitiesIfAvailable(this)
        PreferencesHelper.initialize(this)
        PeriodicKeysRefreshWorkRequest(this).schedulePeriodicKeysRefreshing()
    }
}