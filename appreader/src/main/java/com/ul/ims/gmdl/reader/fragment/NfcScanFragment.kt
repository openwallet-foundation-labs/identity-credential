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

package com.ul.ims.gmdl.reader.fragment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.ul.ims.gmdl.cbordata.MdlDataIdentifiers
import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement
import com.ul.ims.gmdl.cbordata.namespace.MdlNamespace
import com.ul.ims.gmdl.offlinetransfer.config.BleServiceMode
import com.ul.ims.gmdl.offlinetransfer.transportLayer.TransferChannels
import com.ul.ims.gmdl.reader.R
import com.ul.ims.gmdl.reader.activity.NfcEngagementActivity
import com.ul.ims.gmdl.reader.databinding.FragmentNfcScanBinding
import com.ul.ims.gmdl.reader.dialog.CustomAlertDialog
import com.ul.ims.gmdl.reader.dialog.VerifierRequestDialog
import com.ul.ims.gmdl.reader.viewmodel.NfcScanViewModel
import org.jetbrains.anko.support.v4.runOnUiThread

/**
 * A simple [Fragment] subclass.
 *
 */
class NfcScanFragment : Fragment() {
    private lateinit var vm: NfcScanViewModel
    private var requestDialog: DialogFragment? = null
    private var requestItems: List<String>? = null
    private val appPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    private var runOnce = false

    companion object {
        const val LOG_TAG = "NfcScanFragment"
        const val REQUEST_DIALOG_TAG = "requestItems"
        private const val REQUEST_PERMISSIONS_CODE = 123
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentNfcScanBinding.inflate(inflater)

        vm = ViewModelProvider(this).get(NfcScanViewModel::class.java)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val requestPermissionBtn = view.findViewById<Button>(R.id.btn_req_enable_ble)
        requestPermissionBtn.setOnClickListener {
            shouldRequestPermission(it)
        }
    }

    override fun onResume() {
        super.onResume()

        if (isAllPermissionsGranted()) {
            shouldRequestPermission(requireView())
        } else {
            showPermissionDenied()
        }
    }

    private fun shouldRequestPermission(view: View) {
        val permissionsNeeded = mutableListOf<String>()

        appPermissions.forEach { permission ->
            if (ContextCompat.checkSelfPermission(requireContext(), permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(permission)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            requestPermissions(permissionsNeeded.toTypedArray(), REQUEST_PERMISSIONS_CODE)
        } else {
            showRequestItemsDialog()
        }
    }

    private fun onValidEngagement(
        deviceEngagement: DeviceEngagement,
        transferMethod: TransferChannels,
        bleServiceMode: BleServiceMode,
        wifiPassphrase: String?,
        apduCommandLength: Int?
    ) {
        runOnUiThread {
            requestItems?.let {
                val action =
                    NfcScanFragmentDirections.actionNfcScanFragmentToOfflineTransferStatusFragment(
                        deviceEngagement,
                        it.toTypedArray(),
                        transferMethod,
                        bleServiceMode,
                        wifiPassphrase
                    )
                apduCommandLength?.let { maxLength ->
                    action.setApduCommandLength(maxLength)
                }
                findNavController().navigate(action)
            }
        }
    }

    private fun onInvalidEngagement(message: String?) {
        runOnUiThread {
            CustomAlertDialog(
                requireContext()
            ) {
                findNavController().popBackStack()
            }.showErrorDialog(
                getString(R.string.invalid_de_nfc_error_title),
                message ?: getString(R.string.invalid_de_nfc_error_msg)
            )
        }
    }

    private fun showRequestItemsDialog() {
        val requestItems = MdlNamespace.items.keys.filter {
            it != MdlDataIdentifiers.PORTRAIT_OF_HOLDER.identifier
        }.toList()

        if (requestDialog == null) {

            requestDialog = VerifierRequestDialog(requestItems, {
                runOnce = true
                onGatherRequestItems(it)
            }, {
                findNavController().popBackStack()
            })
        }

        requestDialog?.let { rd ->
            // avoid to display the dialog twice as we call this function from onResume and
            // at the first execution it will be called by the permission request routine also.
            if (!rd.isAdded && !runOnce) {
                rd.show(parentFragmentManager, REQUEST_DIALOG_TAG)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        data?.let { intent ->
            val engagement = vm.validateNfcEngagement(
                intent.getByteArrayExtra(NfcEngagementActivity.DEVICE_ENGAGEMENT_RESULT)
            )
            when {
                engagement.isValid() -> {
                    val transferMethod = intent.getSerializableExtra(
                        NfcEngagementActivity.EXTRA_TRANSFER_METHOD
                    ) as TransferChannels

                    val bleServiceMode = if (transferMethod == TransferChannels.BLE) {
                        intent.getSerializableExtra(
                            NfcEngagementActivity.EXTRA_BLE_ROLE
                        ) as BleServiceMode
                    } else {
                        BleServiceMode.UNKNOWN
                    }

                    val wifiPassphrase = if (transferMethod == TransferChannels.WiFiAware) {
                        intent.getStringExtra(NfcEngagementActivity.EXTRA_WIFI_PASSPHRASE)
                    } else null

                    val apduCommandLength = if (transferMethod == TransferChannels.NFC) {
                        intent.getIntExtra(NfcEngagementActivity.EXTRA_APDU_COM_LEN, 0)
                    } else null

                    onValidEngagement(
                        engagement,
                        transferMethod,
                        bleServiceMode,
                        wifiPassphrase,
                        apduCommandLength
                    )
                }
                else -> onInvalidEngagement(
                    intent.getStringExtra(NfcEngagementActivity.DEVICE_ENGAGEMENT_ERROR)
                )
            }
        } ?: onInvalidEngagement(null)
    }

    /**
     * Callback for the VerifierRequestDialog with the data items that the Verifier would
     * like to request from the mDL Holder
     * **/
    private fun onGatherRequestItems(request: List<String>) {
        showPermissionGranted()

        // requestItems variable will be passed along when the app navigates
        // to OfflineTransferStatusFragment
        requestItems = request

        val startNfcEngagementIntent = Intent(context, NfcEngagementActivity::class.java)
        startActivityForResult(startNfcEngagementIntent, 0)
    }

    private fun isAllPermissionsGranted(): Boolean {
        val permissionsNeeded = mutableListOf<String>()

        appPermissions.forEach { permission ->
            if (ContextCompat.checkSelfPermission(requireContext(), permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(permission)
            }
        }

        return permissionsNeeded.isEmpty()
    }

    private fun showPermissionDenied() {
        val scanText = view?.findViewById<TextView>(R.id.txt_scan_nfc_label)
        val permissionDeniedText =
            view?.findViewById<TextView>(R.id.txt_explanation_fine_location_permission)
        val permissionDeniedButton = view?.findViewById<Button>(R.id.btn_req_enable_ble)

        scanText?.visibility = View.GONE
        permissionDeniedButton?.visibility = View.VISIBLE
        permissionDeniedText?.visibility = View.VISIBLE
    }

    private fun showPermissionGranted() {
        val scanText = view?.findViewById<TextView>(R.id.txt_scan_nfc_label)
        val permissionDeniedText =
            view?.findViewById<TextView>(R.id.txt_explanation_fine_location_permission)
        val permissionDeniedButton = view?.findViewById<Button>(R.id.btn_req_enable_ble)

        scanText?.visibility = View.VISIBLE
        permissionDeniedButton?.visibility = View.GONE
        permissionDeniedText?.visibility = View.GONE
    }
}
