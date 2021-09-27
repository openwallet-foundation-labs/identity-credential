package com.android.mdl.app.util

import android.content.Context
import androidx.preference.PreferenceManager

object PreferencesHelper {
    const val HARDWARE_BACKED_PREFERENCE = "com.android.mdl.app.HARDWARE_BACKED"

    fun setHardwareBacked(context: Context, isHardwareBacked: Boolean) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (!hasHardwareBackedPreference(context)) {
            sharedPreferences.edit().putBoolean(
                HARDWARE_BACKED_PREFERENCE,
                isHardwareBacked
            ).apply()
        }
    }

    fun hasHardwareBackedPreference(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context).contains(HARDWARE_BACKED_PREFERENCE)

    fun isHardwareBacked(context: Context): Boolean {
        if (!hasHardwareBackedPreference(context)) {
            throw IllegalStateException("No preference set for used implementation.")
        }
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            HARDWARE_BACKED_PREFERENCE, false
        )
    }
}