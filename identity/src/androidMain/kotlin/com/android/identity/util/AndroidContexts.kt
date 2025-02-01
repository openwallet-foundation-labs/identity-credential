package com.android.identity.util

import android.content.Context
import androidx.fragment.app.FragmentActivity

/**
 * An object used to keep track of Android specific objects.
 *
 * This provides access to [Context] and [FragmentActivity] objects which are needed by certain
 * parts of the library, for example for BLE, NFC, or BiometricPrompt functionality.
 *
 * Applications using certain parts of this library stack should call [setCurrentActivity]
 * from `onResume()` (with a non-null [FragmentActivity]) and `onPause()` (with `null`) in their
 * activities to convey which activity is currently running. If using the library from a background service
 * the application should call [setApplicationContext].
 */
object AndroidContexts {
    private const val TAG = "AndroidContexts"

    /**
     * The [Context] for the application.
     */
    val applicationContext: Context
        get() {
            return _applicationContext
                ?: throw IllegalStateException("Application must use AndroidContexts to set context")
        }
    private var _applicationContext: Context? = null

    /**
     * The currently running activity or `null` if no activity from the application is running.
     */
    val currentActivity: FragmentActivity?
        get() = _currentActivity
    private var _currentActivity: FragmentActivity? = null

    /**
     * Inform the library about the [Context] of the application.
     *
     * @param applicationContext a [Context].
     */
    fun setApplicationContext(applicationContext: Context) {
        this._applicationContext = applicationContext
    }

    /**
     * Inform the library about which [FragmentActivity] is currently running.
     *
     * @param activity a [FragmentActivity] for the running activity or `null` when no activity is no longer running.
     */
    fun setCurrentActivity(activity: FragmentActivity?) {
        this._currentActivity = activity
        activity?.let {
            this._applicationContext = it.applicationContext
        }
    }
}
