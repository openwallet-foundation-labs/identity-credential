package com.android.mdl.appreader.fragment

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.android.mdl.appreader.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }
}