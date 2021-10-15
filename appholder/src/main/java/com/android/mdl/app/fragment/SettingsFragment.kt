package com.android.mdl.app.fragment

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.android.mdl.app.R
import com.android.mdl.app.document.DocumentManager
import com.android.mdl.app.util.PreferencesHelper.BLE_DATA_RETRIEVAL
import com.android.mdl.app.util.PreferencesHelper.BLE_DATA_RETRIEVAL_PERIPHERAL_MODE
import com.android.mdl.app.util.PreferencesHelper.NFC_DATA_RETRIEVAL
import com.android.mdl.app.util.PreferencesHelper.USE_READER_AUTH
import com.android.mdl.app.util.PreferencesHelper.WIFI_DATA_RETRIEVAL

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        return when (preference?.key) {
            BLE_DATA_RETRIEVAL, BLE_DATA_RETRIEVAL_PERIPHERAL_MODE, WIFI_DATA_RETRIEVAL, NFC_DATA_RETRIEVAL -> {
                val pref = preference as SwitchPreference
                if (!pref.isChecked) {
                    pref.isChecked = !hasDataRetrieval()
                }
                true
            }
            USE_READER_AUTH -> {
                val documentManager = DocumentManager.getInstance(requireContext())
                documentManager.provisionMdlDocument()
                documentManager.provisionMvrDocument()
                documentManager.provisionMicovDocument()
                true
            }
            else -> super.onPreferenceTreeClick(preference)
        }

    }

    // Check if there is at least one preference selected
    private fun hasDataRetrieval(): Boolean {
        return preferenceManager.sharedPreferences.getBoolean(BLE_DATA_RETRIEVAL, false) ||
          preferenceManager.sharedPreferences.getBoolean(BLE_DATA_RETRIEVAL_PERIPHERAL_MODE, false) ||
          preferenceManager.sharedPreferences.getBoolean(WIFI_DATA_RETRIEVAL, false) ||
          preferenceManager.sharedPreferences.getBoolean(NFC_DATA_RETRIEVAL, false)

    }
}