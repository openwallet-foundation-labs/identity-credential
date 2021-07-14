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

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.ul.ims.gmdl.bleofflinetransfer.utils.BleUtils
import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement
import com.ul.ims.gmdl.cbordata.model.UserCredential
import com.ul.ims.gmdl.cbordata.request.DataElements
import com.ul.ims.gmdl.cbordata.response.BleTransferResponse
import com.ul.ims.gmdl.cbordata.security.mdlauthentication.Handover
import com.ul.ims.gmdl.offlinetransfer.config.BleServiceMode
import com.ul.ims.gmdl.offlinetransfer.transportLayer.TransferChannels
import com.ul.ims.gmdl.offlinetransfer.utils.Resource
import com.ul.ims.gmdl.reader.R
import com.ul.ims.gmdl.reader.databinding.FragmentOfflineTransferStatusBinding
import com.ul.ims.gmdl.reader.dialog.CustomAlertDialog
import com.ul.ims.gmdl.reader.viewmodel.OfflineTransferStatusViewModel
import java.util.*

/**
 * A simple [Fragment] subclass.
 *
 */
class OfflineTransferStatusFragment : Fragment() {

    companion object {
        private const val LOG_TAG = "OfflineTransferStatusFragment"
        private const val READER_FLAGS = (NfcAdapter.FLAG_READER_NFC_A
                or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
                or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS)
    }

    private var _binding: FragmentOfflineTransferStatusBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var vm: OfflineTransferStatusViewModel
    private var deviceEngagement: DeviceEngagement? = null
    private lateinit var handover: Handover
    private var requestItems: DataElements? = null
    private var transferMethod: TransferChannels? = null
    private var bleServiceMode: BleServiceMode? = null
    private var wifiPassphrase: String? = null
    private var apduCommandLength = 0

    private var nfcAdapter: NfcAdapter? = null

    private var callback: ReaderModeCallback? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val args: OfflineTransferStatusFragmentArgs by navArgs()
        deviceEngagement = args.deviceEngagement
        handover = args.handover
        requestItems = args.requestItems
        transferMethod = args.transferMethod
        bleServiceMode = args.bleServiceMode
        wifiPassphrase = args.wifiPassphrase
        apduCommandLength = args.apduCommandLength

        vm = ViewModelProvider(this).get(OfflineTransferStatusViewModel::class.java)

        _binding = FragmentOfflineTransferStatusBinding.inflate(inflater)
        binding.fragment = this

        nfcAdapter = NfcAdapter.getDefaultAdapter(requireContext())

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        nfcAdapter?.enableReaderMode(activity, callback, READER_FLAGS, null)

        binding.btnCancelTransfer.setOnClickListener { stopTransfer() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        when (transferMethod) {
            TransferChannels.BLE -> {
                // not sure if here is the best place to setup the verifier
                if (BleUtils.isBtEnabled(requireContext())) {
                    setupBleVerifier()
                } else {
                    requestToTurnOnBle()
                }
            }
            TransferChannels.WiFiAware -> {
                setupWiFiVerifier()
            }
            TransferChannels.NFC -> {
                // Wait for the tag discovered
                callback = ReaderModeCallback()
            }
        }
    }

    private inner class ReaderModeCallback : NfcAdapter.ReaderCallback {
        override fun onTagDiscovered(tag: Tag) {
            val techList = tag.techList

            Log.d(LOG_TAG, "Tag discovered: supported tech=" + Arrays.toString(techList))

            for (tech in techList) {
                if (IsoDep::class.java.name == tech) {
                    Log.d(LOG_TAG, "Tag found")
                    setupNfcVerifier(tag)
                    break
                }
            }
        }
    }

    private fun setupBleVerifier() {
        deviceEngagement?.let { de ->
            requestItems?.let { req ->
                bleServiceMode?.let { bsm ->
                    vm.setupBleVerifier(de, handover, req, bsm)
                } ?: kotlin.run {
                    Log.e(LOG_TAG, "BLE Service Mode is null")
                }
            } ?: kotlin.run {
                Log.e(LOG_TAG, "Data Items List is null")
            }
        } ?: kotlin.run {
            Log.e(LOG_TAG, "Device Engagement is null")
        }
    }

    private fun setupWiFiVerifier() {
        deviceEngagement?.let { de ->
            requestItems?.let { req ->
                vm.setupWiFiVerifier(de, handover, req, wifiPassphrase)
            } ?: kotlin.run {
                Log.e(LOG_TAG, "Data Items List is null")
            }
        } ?: kotlin.run {
            Log.e(LOG_TAG, "Device Engagement is null")
        }
    }

    private fun setupNfcVerifier(tag: Tag) {
        deviceEngagement?.let { de ->
            requestItems?.let { req ->
                vm.setupNfcVerifier(de, handover, req, tag, apduCommandLength)
            } ?: kotlin.run {
                Log.e(LOG_TAG, "Data Items List is null")
            }
        } ?: kotlin.run {
            Log.e(LOG_TAG, "Device Engagement is null")
        }
    }

    override fun onStart() {
        super.onStart()

        vm.getTransferData()?.observe(this, { res ->
            // Avoid reading the same tag
            nfcAdapter?.enableReaderMode(activity, null, READER_FLAGS, null)

            when (res?.status) {
                Resource.Status.CONNECTING -> {
                    binding.progressConnecting.visibility = View.VISIBLE
                    binding.imgConnStatus.visibility = View.INVISIBLE
                }
                Resource.Status.TRANSFERRING -> {
                    binding.imgConnStatus.setImageResource(R.drawable.ic_baseline_done)
                    binding.imgConnStatus.visibility = View.VISIBLE
                    binding.progressConnecting.visibility = View.INVISIBLE
                    binding.progressTransferringData.visibility = View.VISIBLE
                }

                Resource.Status.NO_DEVICE_FOUND -> {
                    onErrorUpdateUi()
                }

                Resource.Status.TRANSFER_SUCCESSFUL -> {
                    binding.imgConnStatus.setImageResource(R.drawable.ic_baseline_done)
                    binding.imgConnStatus.visibility = View.VISIBLE
                    binding.progressConnecting.visibility = View.INVISIBLE
                    binding.progressTransferringData.visibility = View.INVISIBLE
                    binding.imgTransferStatus.setImageResource(R.drawable.ic_baseline_done)
                    binding.imgTransferStatus.visibility = View.VISIBLE

                    val response = res.data as? BleTransferResponse
                    response?.let { r ->
                        if (r.responseStatus != 0 || r.isEmpty()) {
                            Log.d(LOG_TAG, "Response Error")
                            CustomAlertDialog(requireContext())
                            { findNavController().popBackStack() }.showErrorDialog(
                                getString(
                                    R.string.response_error
                                ),
                                getString(R.string.response_errors_details)
                            )
                        } else {
                            val credential = UserCredential.Builder()
                                .fromBleTransferResponse(r)
                                .setDeviceSign(r.deviceSignValidation)
                                .setIssuerDataAuthentication(r.issuerDataValidation)
                                .build()

                            val action =
                                OfflineTransferStatusFragmentDirections.actionOfflineTransferStatusFragmentToDisplayCredentialsFragment(
                                    credential
                                )

                            findNavController().navigate(action)
                        }
                    }
                }
                Resource.Status.TRANSFER_ERROR -> {
                    val errorMsg = res.message
                    CustomAlertDialog(requireContext())
                    { findNavController().popBackStack() }.showErrorDialog(
                        getString(
                            R.string.connection_setup_error
                        ), errorMsg
                    )
                    onErrorUpdateUi()
                }
                else -> Log.e(LOG_TAG, "Status transfer not mapped ${res?.status}")
            }
        })
    }

    override fun onStop() {
        super.onStop()

        vm.tearDown()
    }

    private fun stopTransfer() {
        findNavController().popBackStack()
    }

    private fun onErrorUpdateUi() {
        if (binding.imgConnStatus.visibility == View.INVISIBLE) {
            binding.imgConnStatus.setImageResource(R.drawable.ic_baseline_error_outline)
            binding.imgConnStatus.visibility = View.VISIBLE
            binding.progressConnecting.visibility = View.INVISIBLE
            binding.progressTransferringData.visibility = View.INVISIBLE
        } else {
            binding.progressTransferringData.visibility = View.INVISIBLE
            binding.imgTransferStatus.setImageResource(R.drawable.ic_baseline_error_outline)
            binding.imgTransferStatus.visibility = View.VISIBLE
        }
    }

    private var resultToTurnOnBle = registerForActivityResult(StartActivityForResult()) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                // Update UI
                setupWiFiVerifier()
            }
            Activity.RESULT_CANCELED -> {
                requestToTurnOnBle()
            }
            else -> {
                // Unknown resultCode
                Log.e(LOG_TAG, "Unknown resultCode ${result.resultCode}")
            }
        }
    }

    private fun requestToTurnOnBle() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        resultToTurnOnBle.launch(enableBtIntent)
    }
}