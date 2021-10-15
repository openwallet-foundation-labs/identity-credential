package com.android.mdl.app.fragment

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.android.mdl.app.R
import com.android.mdl.app.document.DocumentManager
import com.android.mdl.app.util.PreferencesHelper.BLE_DATA_RETRIEVAL
import com.android.mdl.app.util.PreferencesHelper.BLE_OPTION
import com.android.mdl.app.util.PreferencesHelper.NFC_DATA_RETRIEVAL
import com.android.mdl.app.util.PreferencesHelper.USE_READER_AUTH
import com.android.mdl.app.util.PreferencesHelper.WIFI_DATA_RETRIEVAL

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        showBleOption()
    }

    private fun showBleOption() {
        // Show BLE option when BLE retrieval is enable
        preferenceManager.findPreference<ListPreference>(BLE_OPTION)?.isVisible =
            preferenceManager.sharedPreferences.getBoolean(BLE_DATA_RETRIEVAL, false)
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        return when (preference?.key) {
            BLE_DATA_RETRIEVAL, WIFI_DATA_RETRIEVAL, NFC_DATA_RETRIEVAL -> {
                val pref = preference as SwitchPreference
                if (!pref.isChecked) {
                    pref.isChecked = !hasDataRetrieval()
                }
                showBleOption()
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
                preferenceManager.sharedPreferences.getBoolean(WIFI_DATA_RETRIEVAL, false) ||
                preferenceManager.sharedPreferences.getBoolean(NFC_DATA_RETRIEVAL, false)

    }
}