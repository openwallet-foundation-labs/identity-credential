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
import android.nfc.FormatException
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.AsyncTask
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.budiyev.android.codescanner.*
import com.ul.ims.gmdl.cbordata.MdlDataIdentifiers
import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement
import com.ul.ims.gmdl.cbordata.namespace.MdlNamespace
import com.ul.ims.gmdl.nfcengagement.HandoverSelectMessage
import com.ul.ims.gmdl.offlinetransfer.config.BleServiceMode
import com.ul.ims.gmdl.offlinetransfer.transportLayer.TransferChannels
import com.ul.ims.gmdl.reader.R
import com.ul.ims.gmdl.reader.databinding.FragmentScanDeviceEngagementBinding
import com.ul.ims.gmdl.reader.dialog.CustomAlertDialog
import com.ul.ims.gmdl.reader.dialog.VerifierRequestDialog
import com.ul.ims.gmdl.reader.viewmodel.ScanDeviceEngagementViewModel
import org.jetbrains.anko.support.v4.runOnUiThread
import org.jetbrains.anko.support.v4.toast
import java.util.*

/**
 * A simple [Fragment] subclass.
 *
 */
class ScanDeviceEngagementFragment : Fragment() {

    private lateinit var codeScanner: CodeScanner
    private lateinit var vm: ScanDeviceEngagementViewModel
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
        private const val READER_FLAGS = (NfcAdapter.FLAG_READER_NFC_A
                or NfcAdapter.FLAG_READER_NFC_B
                or NfcAdapter.FLAG_READER_NFC_F
                or NfcAdapter.FLAG_READER_NFC_V
                or NfcAdapter.FLAG_READER_NFC_BARCODE)
    }

    private var nfcAdapter: NfcAdapter? = null

    private var ndefReaderTask: NdefReaderTask? = null

    private val decodeCallback = DecodeCallback {
        val engagement = vm.validateQrCode(it.text)

        if (engagement?.isValid() == true) {
            val transferMethod = when {
                engagement.getBLETransferMethod() != null ->
                    TransferChannels.BLE
                engagement.getWiFiAwareTransferMethod() != null ->
                    TransferChannels.WiFiAware
                engagement.getNfcTransferMethod() != null ->
                    TransferChannels.NFC
                else -> TransferChannels.BLE // Default
            }
            onValidEngagement(engagement, transferMethod, BleServiceMode.UNKNOWN, null, null)
        } else {
            onInvalidEngagement()
        }
    }

    private val nfcCallback =
        NfcAdapter.ReaderCallback { tag -> // Tell the adapter to ignore this tag for any consecutive reads. debounceMs needs to be
            //  greater than the polling interval of the adapter
            nfcAdapter?.ignore(tag, 1000, null, null)

            val techList = tag.techList

            Log.d(LOG_TAG, "Tag discovered: supported tech=" + Arrays.toString(techList))

            for (tech in techList) {
                if (Ndef::class.java.name == tech) {
                    ndefReaderTask = NdefReaderTask()
                    ndefReaderTask?.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, tag)
                    break
                }
            }
        }

    private inner class NdefReaderTask : AsyncTask<Tag, Void, Void>() {

        override fun doInBackground(vararg params: Tag): Void? {
            val tag = params[0]

            val ndef = Ndef.get(tag)
                ?: return null // NDEF is not supported by this Tag.

            val ndefMessage = ndef.cachedNdefMessage

            val handoverSelectMessage: HandoverSelectMessage
            try {
                handoverSelectMessage =
                    HandoverSelectMessage(
                        ndefMessage,
                        ndef
                    )

                handoverSelectMessage.deviceEngagementBytes?.let { deviceEngagementBytes ->

                    val deviceEngagement =
                        DeviceEngagement.Builder().decode(deviceEngagementBytes).build()

                    val transferMethod =
                        handoverSelectMessage.transferMethod ?: TransferChannels.NFC

                    val bleServiceMode =
                        handoverSelectMessage.bleServiceMode ?: BleServiceMode.UNKNOWN

                    onValidEngagement(
                        deviceEngagement,
                        transferMethod,
                        bleServiceMode,
                        handoverSelectMessage.wifiPassphrase,
                        handoverSelectMessage.apduCommandLength
                    )
                    return null
                }
            } catch (e: FormatException) {
                onInvalidEngagement()
                return null
            }

            onInvalidEngagement()
            return null
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentScanDeviceEngagementBinding.inflate(inflater)
        binding.fragment = this
        nfcAdapter = NfcAdapter.getDefaultAdapter(requireContext())

        if (nfcAdapter?.isEnabled != true) {
            toast(getString(R.string.toast_nfc_adapter_not_present))
            nfcAdapter = null
        }
        vm = ViewModelProvider(this).get(ScanDeviceEngagementViewModel::class.java)
        binding.vm = vm
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val swrIntentRetain = view.findViewById<Switch>(R.id.swt_intent_retain)
        val swtReaderAuthentication = view.findViewById<Switch>(R.id.swt_reader_authentication)

        swrIntentRetain.setOnCheckedChangeListener { _, _ ->
            toast(getString(R.string.toast_not_implemented_text))
        }
        swtReaderAuthentication.setOnCheckedChangeListener { _, _ ->
            toast(getString(R.string.toast_not_implemented_text))
        }

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

        val spnDataRequest = view.findViewById<Spinner>(R.id.spn_data_request)

        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.spn_data_request_items,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            spnDataRequest.adapter = adapter
        }

        spnDataRequest.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                disableReader()
                when (pos) {
                    0 -> toast(getString(R.string.toast_not_implemented_text))
                    1 -> requestAll()
                    2 -> showDialog()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                disableReader()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (isAllPermissionsGranted()) {
            shouldRequestPermission(requireView())
        } else {
            vm.showPermissionDenied()
        }
    }

    override fun onPause() {
        super.onPause()
        runOnce = false
        disableReader()
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
                    ScanDeviceEngagementFragmentDirections.actionScanDeviceEngagementFragmentToOfflineTransferStatusFragment(
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
            vm.showPermissionGranted()
        }
    }

    private fun requestAll() {
        vm.showPermissionGranted()

        requestItems = MdlNamespace.items.keys.toList()
        enableReader()
    }

    private fun showDialog() {
        vm.showPermissionGranted()

        val requestItems = MdlNamespace.items.keys.filter {
            it != MdlDataIdentifiers.PORTRAIT_OF_HOLDER.identifier
        }.toList()

        if (requestDialog == null) {
            requestDialog = VerifierRequestDialog(requestItems, {
                enableReader()
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

    private fun enableReader() {
        codeScanner.startPreview()
        nfcAdapter?.enableReaderMode(requireActivity(), nfcCallback, READER_FLAGS, null)
    }

    private fun disableReader() {
        codeScanner.releaseResources()
        nfcAdapter?.disableReaderMode(requireActivity())
        ndefReaderTask?.cancel(true)
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

                    if (!permissionsDenied.isEmpty()) {
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
}
