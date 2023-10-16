package com.android.identity.wallet.util

import android.content.Context
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class PeriodicKeysRefreshWorkRequest(context: Context) {

    private val workManager = WorkManager.getInstance(context)

    fun schedulePeriodicKeysRefreshing() {
        val workRequest = PeriodicWorkRequestBuilder<RefreshKeysWorker>(1, TimeUnit.DAYS)
            .build()
        workManager.enqueue(workRequest)
    }
}