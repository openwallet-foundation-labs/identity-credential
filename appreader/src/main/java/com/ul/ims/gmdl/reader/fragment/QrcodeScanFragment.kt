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
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.budiyev.android.codescanner.*
import com.ul.ims.gmdl.cbordata.MdlDataIdentifiers
import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement
import com.ul.ims.gmdl.cbordata.namespace.MdlNamespace
import com.ul.ims.gmdl.offlinetransfer.config.BleServiceMode
import com.ul.ims.gmdl.offlinetransfer.transportLayer.TransferChannels
import com.ul.ims.gmdl.reader.R
import com.ul.ims.gmdl.reader.databinding.FragmentQrcodeScanBinding
import com.ul.ims.gmdl.reader.dialog.CustomAlertDialog
import com.ul.ims.gmdl.reader.dialog.VerifierRequestDialog
import com.ul.ims.gmdl.reader.viewmodel.QrcodeScanViewModel
import org.jetbrains.anko.support.v4.runOnUiThread

/**
 * A simple [Fragment] subclass.
 *
 */
class QrcodeScanFragment : Fragment() {

    private lateinit var codeScanner: CodeScanner
    private lateinit var vm: QrcodeScanViewModel
    private val appPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    private var requestDialog: DialogFragment? = null
    private var requestItems: List<String>? = null
    private var runOnce = false

    companion object {
        const val LOG_TAG = "QrcodeScanFragment"
        const val PORTRAIT_KEY = "portrait"
        const val REQUEST_DIALOG_TAG = "requestItems"
        private const val REQUEST_PERMISSIONS_CODE = 123
    }

    private val decodeCallback = DecodeCallback {
        val engagement = vm.validateQrCode(it.text)

        if (engagement?.isValid() == true) {
            onValidEngagement(engagement)
        } else {
            onInvalidEngagement()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentQrcodeScanBinding.inflate(inflater)
        binding.fragment = this

        vm = ViewModelProvider(this).get(QrcodeScanViewModel::class.java)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val scannerView = view.findViewById<CodeScannerView>(R.id.qrcode_scan_view)

        codeScanner = CodeScanner(requireContext(), scannerView)
        codeScanner.isAutoFocusEnabled = true
        codeScanner.autoFocusMode = AutoFocusMode.SAFE
        codeScanner.scanMode = ScanMode.SINGLE
        codeScanner.decodeCallback = decodeCallback

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

    override fun onPause() {
        super.onPause()
        runOnce = false
        codeScanner.releaseResources()
    }

    private fun onValidEngagement(deviceEngagement: DeviceEngagement) {
        runOnUiThread {
            requestItems?.let {
                when {
                    deviceEngagement.getBLETransferMethod() != null -> {
                        val action = QrcodeScanFragmentDirections
                            .actionQrcodeScanFragmentToOfflineTransferStatusFragment(
                                deviceEngagement,
                                it.toTypedArray(),
                                TransferChannels.BLE,
                                BleServiceMode.UNKNOWN,
                                null
                            )
                        findNavController().navigate(action)
                    }
                    deviceEngagement.getWiFiAwareTransferMethod() != null -> {
                        val action = QrcodeScanFragmentDirections
                            .actionQrcodeScanFragmentToOfflineTransferStatusFragment(
                                deviceEngagement,
                                it.toTypedArray(),
                                TransferChannels.WiFiAware,
                                BleServiceMode.UNKNOWN,
                                null
                            )
                        findNavController().navigate(action)
                    }
                    else -> onInvalidEngagement()
                }
            }
        }
    }

    private fun onInvalidEngagement() {
        runOnUiThread {
            CustomAlertDialog(
                requireContext()
            ) {
                shouldRequestPermission(requireView())
            }.showErrorDialog(
                getString(R.string.invalid_de_error_title),
                getString(R.string.invalid_de_error_msg)
            )
        }
    }

    fun shouldRequestPermission(view: View) {
        val permissionsNeeded = mutableListOf<String>()

        appPermissions.forEach { permission ->
            if (checkSelfPermission(requireContext(), permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(permission)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            requestPermissions(permissionsNeeded.toTypedArray(), REQUEST_PERMISSIONS_CODE)
        } else {
            startScanner()
        }
    }

    private fun startScanner() {
        showPermissionGranted()

        val requestItems = MdlNamespace.items.keys.filter {
            it != MdlDataIdentifiers.PORTRAIT_OF_HOLDER.identifier
        }.toList()

        if (requestDialog == null) {
            requestDialog = VerifierRequestDialog(requestItems, {
                codeScanner.startPreview()
                onGatherRequestItems(it)
                runOnce = true
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

    /**
     * Callback for the VerifierRequestDialog with the data items that the Verifier would
     * like to request from the mDL Holder
     * **/
    private fun onGatherRequestItems(request: List<String>) {
        // requestItems variable will be passed along when the app navigates
        // to OfflineTransferStatusFragment
        requestItems = request
    }

    private fun isAllPermissionsGranted(): Boolean {
        val permissionsNeeded = mutableListOf<String>()

        appPermissions.forEach { permission ->
            if (checkSelfPermission(requireContext(), permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(permission)
            }
        }

        return permissionsNeeded.isEmpty()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_PERMISSIONS_CODE -> {
                val permissionsDenied = mutableListOf<String>()

                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty()) {
                    grantResults.forEachIndexed { index, i ->
                        if (i == PackageManager.PERMISSION_DENIED) {
                            permissionsDenied.add(permissions[index])
                        }
                    }

                    if (permissionsDenied.isEmpty()) {
                        startScanner()
                    } else {
                        permissionsDenied.forEach {
                            if (!shouldShowRequestPermissionRationale(it)) {
                                openSettings()
                            }
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

    private fun openSettings() {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        intent.data = Uri.fromParts("package", requireContext().packageName, null)
        startActivity(intent)

        requireContext().startActivity(intent)
    }

    private fun showPermissionDenied() {
        val qrcodeReader = view?.findViewById<CodeScannerView>(R.id.qrcode_scan_view)
        val permissionDeniedText =
            view?.findViewById<TextView>(R.id.txt_explanation_camera_permission)
        val permissionDeniedButton = view?.findViewById<Button>(R.id.btn_req_enable_ble)

        qrcodeReader?.visibility = View.GONE
        permissionDeniedButton?.visibility = View.VISIBLE
        permissionDeniedText?.visibility = View.VISIBLE
    }

    private fun showPermissionGranted() {
        val qrcodeReader = view?.findViewById<CodeScannerView>(R.id.qrcode_scan_view)
        val permissionDeniedText =
            view?.findViewById<TextView>(R.id.txt_explanation_camera_permission)
        val permissionDeniedButton = view?.findViewById<Button>(R.id.btn_req_enable_ble)

        qrcodeReader?.visibility = View.VISIBLE
        permissionDeniedButton?.visibility = View.GONE
        permissionDeniedText?.visibility = View.GONE
    }
}
