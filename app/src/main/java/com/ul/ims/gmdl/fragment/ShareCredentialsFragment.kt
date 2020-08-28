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
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
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
import com.ul.ims.gmdl.util.NfcTransferApduService
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
        private const val REQUEST_FINE_LOCATION = 123
        private const val REQUEST_ENABLE_BT = 456
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
    ): View? {
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
                requestToTurnOnBle(requireView())
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

        vm.getOfflineTransferStatusLd().observe(viewLifecycleOwner, Observer { myData ->
            when (myData.status) {
                Resource.Status.NO_DEVICE_FOUND -> {
                    CustomAlertDialog(requireContext()) { navigateBack() }
                        .showErrorDialog(
                            getString(R.string.err_no_device_found),
                            getString(R.string.ble_no_device_found)
                        )
                }

                Resource.Status.ASK_USER_CONSENT -> {
                    @Suppress("UNCHECKED_CAST")
                    val requestItems = myData.data as? List<String>
                    requestItems?.let {
                        ConsentDialog(it, { consent ->
                            this.consent = consent
                            canAuthenticate()
                        }, {
                            vm.onUserConsentCancel()
                        }).show(parentFragmentManager, "consentdialog")
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
            if (TransferChannels.NFC == transferMethod) {
                vm.onUserPreConsent(consent)
            } else {
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
        val intentTransfer = Intent(context, NfcTransferApduService::class.java)
        context?.stopService(intentTransfer)
    }

    private fun shouldRequestPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED
        ) {

            //Update UI
            vm.isPermissionGranted(false)

            if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_FINE_LOCATION)
            }

        } else {

            //Update UI
            vm.isPermissionGranted(true)

            setupHolder()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_FINE_LOCATION -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty()) {

                    when (grantResults[0]) {
                        PackageManager.PERMISSION_GRANTED -> {
                            //Update UI
                            vm.isPermissionGranted(true)
                            setupHolder()
                        }
                        PackageManager.PERMISSION_DENIED -> {
                            //Update UI
                            vm.isPermissionGranted(false)
                        }
                    }
                }
                return
            }
            else -> {
                // Ignore all other requests.
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_ENABLE_BT -> {
                when (resultCode) {
                    -1 -> {
                        // Update UI
                        vm.isBleEnabled(true)
                        shouldRequestPermission()
                    }
                    0 -> {
                        // Update UI
                        vm.isBleEnabled(false)
                    }
                    else -> {
                        // Unknown resultCode
                        Log.e(LOG_TAG, "Unknown resultCode $resultCode")
                    }
                }
            }
        }
    }

    fun requestToTurnOnBle(view : View) {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
    }

    fun requestPermission(view: View) {
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_FINE_LOCATION)
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
