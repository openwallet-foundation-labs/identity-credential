package com.android.identity.android.util

import android.nfc.cardemulation.HostApduService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel


/**
 * HostApduServiceScoped is a wrapper for the service class HostApduService that provides a
 * service-bound coroutine scope to run coroutines from its children. It also ensures to cancel any
 * actively running coroutines when this service's [onDestroy] gets called.
 *
 * Extending service classes get access to [serviceScope] to pass to its child objects and never have
 * to worry about lingering coroutines beyond the Service's lifecycle.
 */
abstract class HostApduServiceScoped : HostApduService() {
    // supervisor job is not affected by raised exceptions of a child coroutines
    private val serviceJob = SupervisorJob()

    // scope that child objects can use to launch coroutines
    val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    /**
     * Ensure we cancel the SupervisorJob so it propagates cancellation to all sub-coroutines.
     */
    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel("Service onDestroy() was called!")
    }
}