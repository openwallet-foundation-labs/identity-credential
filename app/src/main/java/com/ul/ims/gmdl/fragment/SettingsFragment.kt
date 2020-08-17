/*
 * Copyright (C) 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package com.ul.ims.gmdl.fragment

import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.ul.ims.gmdl.R
import com.ul.ims.gmdl.cbordata.utils.Log


class SettingsFragment : PreferenceFragmentCompat() {

    private var nfcAdapter: NfcAdapter? = null

    companion object {
        const val LOG_TAG = "SettingsFragment"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        nfcAdapter = NfcAdapter.getDefaultAdapter(requireContext())

        // Transfer Methods
        val transferMethods = ArrayList<CheckBoxPreference>()
        val transferMethodsListener = Preference.OnPreferenceClickListener {
            for (preference in transferMethods) {
                if (preference.key != it.key && preference.isChecked) {
                    preference.isChecked = false
                } else if (preference.key == it.key && !preference.isChecked) {
                    if (!preference.isChecked) {
                        preference.isChecked = true
                    }
                }
            }
            false
        }

        // Engagement Methods
        val engagementMethods = ArrayList<CheckBoxPreference>()
        val engagementMethodsListener = Preference.OnPreferenceClickListener {
            for (preference in engagementMethods) {
                if (preference.key != it.key && preference.isChecked) {
                    preference.isChecked = false
                } else if (preference.key == it.key && !preference.isChecked) {
                    if (!preference.isChecked) {
                        preference.isChecked = true
                    }
                }
            }
            false
        }

        // BLE
        val bleTransferMethod = preferenceManager.findPreference<Preference>("transfer_method_ble")
                as? CheckBoxPreference
        bleTransferMethod?.let {
            it.onPreferenceClickListener = transferMethodsListener
            transferMethods.add(bleTransferMethod)
        }

        // Wi-fi Aware
        val wifiAwareTransferMethod =
            preferenceManager.findPreference<Preference>("transfer_method_wifi")
                    as? CheckBoxPreference

        // NFC
        val nfcTransferMethod = preferenceManager.findPreference<Preference>("transfer_method_nfc")
                as? CheckBoxPreference
        nfcTransferMethod?.let {
            it.onPreferenceClickListener = transferMethodsListener
            transferMethods.add(nfcTransferMethod)
        }
        // The libraries used in this project do not support API level lower then 29
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            // Check whether the device supports Wi-Fi Aware
            if (requireContext().packageManager.hasSystemFeature(
                    PackageManager.FEATURE_WIFI_AWARE
                )
            ) {
                wifiAwareTransferMethod?.let {
                    it.onPreferenceClickListener = transferMethodsListener
                    transferMethods.add(wifiAwareTransferMethod)
                }
            } else {
                // Disable Wi-Fi Aware Selection
                wifiAwareTransferMethod?.apply {
                    this.isChecked = false
                    this.isEnabled = false
                }

                Log.d(LOG_TAG, "Wi-Fi Aware is not supported by the device")
            }
        } else {
            // Disable Wi-Fi Aware Selection
            wifiAwareTransferMethod?.apply {
                this.isChecked = false
                this.isEnabled = false
            }

            Log.d(LOG_TAG, "Wi-Fi Aware is not supported by the OS")
        }

        // NFC
        val nfcEngagementMethod = preferenceManager.
            findPreference<Preference>("engagement_method_nfc")
                    as? CheckBoxPreference

        if (nfcAdapter != null) {
            nfcEngagementMethod?.let {
                it.onPreferenceClickListener = engagementMethodsListener
                engagementMethods.add(it)
            }
        } else {
            // Disable NFC Selection
            nfcEngagementMethod?.apply {
                this.isEnabled = false
                this.isChecked = false
            }
            Log.d(LOG_TAG, "NFC is not available")
        }

        // QRCode
        val qrEngageMethod =
            preferenceManager.findPreference<Preference>("engagement_method_qr")
                    as CheckBoxPreference?

        qrEngageMethod?.let {
            it.onPreferenceClickListener = engagementMethodsListener
            engagementMethods.add(it)
        }
    }
}
