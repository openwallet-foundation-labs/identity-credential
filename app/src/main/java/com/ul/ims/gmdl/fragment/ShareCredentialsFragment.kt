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

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.ul.ims.gmdl.R
import com.ul.ims.gmdl.bleofflinetransfer.utils.BleUtils
import com.ul.ims.gmdl.cbordata.MdlDataIdentifiers
import com.ul.ims.gmdl.cbordata.namespace.MdlNamespace
import com.ul.ims.gmdl.databinding.FragmentShareCredentialsBinding
import com.ul.ims.gmdl.dialog.ConsentDialog
import com.ul.ims.gmdl.dialog.CustomAlertDialog
import com.ul.ims.gmdl.nfcengagement.NfcHandler
import com.ul.ims.gmdl.nfcengagement.NfcHandler.Companion.ACTION_NFC_DEVICE_ENGAGEMENT_CALLBACK
import com.ul.ims.gmdl.offlinetransfer.transportLayer.TransferChannels
import com.ul.ims.gmdl.offlinetransfer.utils.BiometricUtils
import com.ul.ims.gmdl.offlinetransfer.utils.Resource
import com.ul.ims.gmdl.util.SettingsUtils
import com.ul.ims.gmdl.util.SharedPreferenceUtils
import com.ul.ims.gmdl.viewmodel.ShareCredentialsViewModel
import com.ul.ims.gmdl.wifiofflinetransfer.utils.WifiUtils
import org.jetbrains.anko.support.v4.runOnUiThread
import java.util.concurrent.Executor

/**
 * A simple [Fragment] subclass.
 *
 */
class ShareCredentialsFragment : Fragment() {


    companion object {
        private val LOG_TAG = ShareCredentialsFragment::class.java.simpleName
    }

    private lateinit var vm: ShareCredentialsViewModel
    private lateinit var transferMethod: TransferChannels
    private lateinit var sharedPref: SharedPreferenceUtils

    private val executor = Executor {
        if (Looper.myLooper() == null) {
            Looper.prepare()
        }

        it.run()
    }
    private lateinit var consent: Map<String, Boolean>

    private val nfcDeviceEngagementReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(LOG_TAG, "onReceive $intent")
            vm.setupHolderNfc(transferMethod)
        }

    }

    private val biometricAuthCallback = object : BiometricPrompt.AuthenticationCallback() {

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            // reached max attempts to authenticate the user, or authentication dialog was cancelled

            Log.d(LOG_TAG, "Attempt to authenticate the user has failed")

            runOnUiThread {
                Toast.makeText(requireContext(), getString(R.string.bio_auth_failed),
                    Toast.LENGTH_SHORT).show()
            }
            vm.tearDownTransfer()
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)

            onUserConsent()
        }

        override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()

            Log.d(LOG_TAG, "Attempt to authenticate the user has failed")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentShareCredentialsBinding.inflate(inflater)
        vm = ViewModelProvider(this).get(ShareCredentialsViewModel::class.java)
        vm.setUp()

        binding.vm = vm
        binding.fragment = this

        try {
            transferMethod = SettingsUtils.getTransferMethod(requireContext())
        } catch (e: IllegalStateException) {
            // Set BLE as default Transfer Method
            transferMethod = TransferChannels.BLE

            Log.e(LOG_TAG, "${getString(R.string.settings_error_message)} ${e.message}")
            CustomAlertDialog(requireContext()) {
                findNavController().popBackStack()
            }
                .showErrorDialog(
                    getString(R.string.settings_error_title),
                    "${getString(R.string.settings_error_message)} ${e.message}"
                )
        }

        binding.btnReqEnableBle.setOnClickListener { requestToTurnOnBle() }

        binding.btnReqPermission.setOnClickListener { shouldRequestPermission() }

        sharedPref = SharedPreferenceUtils(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (TransferChannels.BLE == transferMethod) {
            if (BleUtils.isBtEnabled(requireContext())) {
                shouldRequestPermission()
            } else {
                // Update UI
                vm.isBleEnabled(false)

                // Request user to enable BLE
                requestToTurnOnBle()
            }
        } else if (TransferChannels.WiFiAware == transferMethod) {
            if (WifiUtils.isWifiEnabled(requireContext())) {
                shouldRequestPermission()
            } else {
                // Update UI
                vm.isWifiEnabled(false)
            }
        } else if (TransferChannels.NFC == transferMethod) {
            shouldRequestPermission()
        } else {
            throw UnsupportedOperationException("Unsupported transfer method")
        }
    }

    private fun setupHolder() {
        // Biometric Authentication
        if (sharedPref.isBiometricAuthRequired()) {
            if (!BiometricUtils.isBiometricEnrolled(requireContext())) {
                CustomAlertDialog(requireContext()) {
                    findNavController().popBackStack()
                }.showErrorDialog(
                    getString(R.string.fingerprint_removed_error),
                    getString(R.string.fingerprint_removed_error_desc)
                )
                return
            }
        }

        // Create the QRCode and start NFC tag service
        vm.createHolderDe(transferMethod)

        val filter = IntentFilter(ACTION_NFC_DEVICE_ENGAGEMENT_CALLBACK)
        context?.registerReceiver(nfcDeviceEngagementReceiver, filter)

        vm.getOfflineTransferStatusLd().observe(viewLifecycleOwner, { myData ->
            when (myData.status) {
                Resource.Status.NO_DEVICE_FOUND -> {
                    CustomAlertDialog(requireContext()) { navigateBack() }
                        .showErrorDialog(
                            getString(R.string.err_no_device_found),
                            getString(R.string.ble_no_device_found)
                        )
                }

                Resource.Status.ASK_USER_CONSENT -> {
                    if (TransferChannels.NFC == transferMethod && ::consent.isInitialized) {
                        vm.onUserConsent(consent)
                    } else {
                        val preApproved =
                            SettingsUtils.getAgeAttestationPreApproval(requireContext())

                        @Suppress("UNCHECKED_CAST")
                        val requestItems = myData.data as? Map<String, Boolean>
                        requestItems?.let {
                            val requestList = it.keys.toMutableList()
                            // Only if pre approved and the age attestation is set as not intent to
                            // retain by the reader we don't ask for the user consent
                            this.consent = mapOf()
                            if (preApproved) {
                                if (it[MdlDataIdentifiers.AGE_OVER_18.identifier] == false) {
                                    requestList.remove(MdlDataIdentifiers.AGE_OVER_18.identifier)
                                    this.consent =
                                        mapOf(Pair(MdlDataIdentifiers.AGE_OVER_18.identifier, true))
                                }
                                if (it[MdlDataIdentifiers.AGE_OVER_21.identifier] == false) {
                                    requestList.remove(MdlDataIdentifiers.AGE_OVER_21.identifier)
                                    this.consent =
                                        mapOf(Pair(MdlDataIdentifiers.AGE_OVER_21.identifier, true))
                                }
                            }
                            if (requestList.isNotEmpty()) {
                                ConsentDialog(requestList, { consent ->
                                    this.consent = consent.plus(this.consent)
                                    canAuthenticate()
                                }, {
                                    vm.onUserConsentCancel()
                                }).show(parentFragmentManager, "consentdialog")
                            } else {
                                onUserConsent()
                            }
                        }
                    }
                }
                Resource.Status.TRANSFER_SUCCESSFUL -> {
                    CustomAlertDialog(requireContext()) { navigateBack() }
                        .showErrorDialog(
                            getString(R.string.alert_transfer_complete_title),
                            getString(R.string.alert_transfer_complete_text)
                        )
                }
                else -> Log.d(LOG_TAG, "Status: ${myData.status} message: ${myData.message}")
            }
        })

        // Only for NFC transfer is asked to the user to consent with the sharing information prior
        if (TransferChannels.NFC == transferMethod) {
            val requestItems = MdlNamespace.items.keys.filter {
                it != MdlDataIdentifiers.PORTRAIT_OF_HOLDER.identifier
            }.toList()

            ConsentDialog(requestItems, { consent ->
                this.consent = consent
                canAuthenticate()
            }, {
                if (TransferChannels.NFC == transferMethod) {
                    navigateBack()
                } else {
                    vm.onUserConsentCancel()
                }
            }).show(parentFragmentManager, "consentdialog")
        }
    }

    // TODO: Right now we show the biometric auth dialog on every presentation. This is a bug,
    //  it should only be shown if IdentityCredential.getEntries() returns the status code
    //  STATUS_USER_AUTHENTICATION_FAILED for one or more of the returned entries.
    //
    private fun canAuthenticate() {
        if (sharedPref.isBiometricAuthRequired()) {

            if (BiometricUtils.isBiometricEnrolled(requireContext())) {
                try {
                    vm.getCryptoObject()?.let { co ->
                        showBiometricPrompt(co)
                    }
                } catch (ex: RuntimeException) {
                    Log.e(LOG_TAG, ex.message, ex)
                }
            }

        } else {
            onUserConsent()
        }
    }

    private fun onUserConsent() {
        if (::consent.isInitialized) {
            if (TransferChannels.NFC != transferMethod) {
                vm.onUserConsent(consent)
            }
        }
    }

    private fun navigateBack() {
        if (findNavController().currentDestination?.id == R.id.shareCredentialsFragment) {
            findNavController().popBackStack()
        }
    }

    override fun onStop() {
        super.onStop()

        vm.tearDownTransfer()
        try {
            context?.unregisterReceiver(nfcDeviceEngagementReceiver)
        } catch (e: IllegalArgumentException) {
            // Ignore error when trying to unregister receiver
            Log.e(LOG_TAG, "Ignored error: ${e.message}")
        }

        val intent = Intent(context, NfcHandler::class.java)
        context?.stopService(intent)
    }

    private fun shouldRequestPermission() {
        if (checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            vm.isPermissionGranted(true)
            setupHolder()
        }
    }

    private val permissionsLauncher =
        registerForActivityResult(RequestPermission()) { permission ->
            Log.d(LOG_TAG, "permissionsLauncher $permission")

            if (permission) {
                //Update UI
                vm.isPermissionGranted(true)
                setupHolder()
            } else {
                //Update UI
                vm.isPermissionGranted(false)
                // Open settings if user denied any required permission
                if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    openSettings()
                }
            }
        }

    private var resultToTurnOnBle =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_OK -> {
                    // Update UI
                    vm.isBleEnabled(true)
                    shouldRequestPermission()
                }
                Activity.RESULT_CANCELED -> {
                    // Update UI
                    vm.isBleEnabled(false)
                }
                else -> {
                    // Unknown resultCode
                    Log.e(LOG_TAG, "Unknown resultCode ${result.resultCode}")
                }
            }
        }

    private fun openSettings() {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        intent.data = Uri.fromParts("package", requireContext().packageName, null)
        startActivity(intent)
    }

    private fun requestToTurnOnBle() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        resultToTurnOnBle.launch(enableBtIntent)
    }

    private fun showBiometricPrompt(cryptoObject: BiometricPrompt.CryptoObject) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.bio_auth_title))
            .setSubtitle(getString(R.string.bio_auth_subtitle))
            .setNegativeButtonText(getString(R.string.bio_auth_cancel))
            .build()

        val biometricPrompt = BiometricPrompt(this, executor, biometricAuthCallback)

        // Displays the "log in" prompt.
        biometricPrompt.authenticate(promptInfo, cryptoObject)
    }
}
