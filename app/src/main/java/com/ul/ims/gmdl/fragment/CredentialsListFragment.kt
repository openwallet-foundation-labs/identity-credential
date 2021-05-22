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
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import com.ul.ims.gmdl.R
import com.ul.ims.gmdl.cbordata.utils.Log
import com.ul.ims.gmdl.databinding.FragmentCredentialsListBinding
import com.ul.ims.gmdl.dialog.CustomAlertDialog
import com.ul.ims.gmdl.offlinetransfer.transportLayer.TransferChannels
import com.ul.ims.gmdl.util.SettingsUtils
import com.ul.ims.gmdl.util.SettingsUtils.getTransferMethod
import com.ul.ims.gmdl.util.SettingsUtils.setTransferMethod
import com.ul.ims.gmdl.viewmodel.CredentialsListViewModel
import org.jetbrains.anko.support.v4.toast

class CredentialsListFragment : Fragment() {

    private var _binding: FragmentCredentialsListBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCredentialsListBinding.inflate(inflater)

        subscribeUi()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.swtAgeAttestation.isChecked =
            SettingsUtils.getAgeAttestationPreApproval(requireContext())

        binding.swtAgeAttestation.setOnCheckedChangeListener { _, checked ->
            SettingsUtils.setAgeAttestationPreApproval(requireContext(), checked)
        }
        binding.swtAuthorizedReaders.setOnCheckedChangeListener { _, _ ->
            toast(getString(R.string.toast_not_implemented_text))
        }

        // Setup Transfer methods spinner
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.arr_transfer_method,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            binding.spnTransferMethod.adapter = adapter
        }

        // Restore selection from settings
        when (getTransferMethod(requireContext())) {
            TransferChannels.BLE -> binding.spnTransferMethod.setSelection(0, false)
            TransferChannels.WiFiAware -> binding.spnTransferMethod.setSelection(1, false)
            TransferChannels.NFC -> binding.spnTransferMethod.setSelection(2, false)
        }

        binding.spnTransferMethod.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    pos: Int,
                    id: Long
                ) {
                    binding.btnShareMdl.isEnabled = false
                    when (pos) {
                        0 -> {
                            setTransferMethod(requireContext(), TransferChannels.BLE)
                            binding.btnShareMdl.isEnabled = true
                        }
                        1 -> {
                            // The libraries used in this project do not support API level lower then 29
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                                // Check whether the device supports Wi-Fi Aware
                                if (requireContext().packageManager.hasSystemFeature(
                                        PackageManager.FEATURE_WIFI_AWARE
                                    )
                                ) {
                                    setTransferMethod(requireContext(), TransferChannels.WiFiAware)
                                    binding.btnShareMdl.isEnabled = true
                                } else {
                                    Log.d(
                                        LOG_TAG,
                                        getString(R.string.toast_wifi_not_supported_device)
                                    )
                                    toast(getString(R.string.toast_wifi_not_supported_device))
                                }
                            } else {
                                Log.d(LOG_TAG, getString(R.string.toast_wifi_not_supported_os))
                                toast(getString(R.string.toast_wifi_not_supported_os))
                            }
                        }
                        2 -> {
                            setTransferMethod(requireContext(), TransferChannels.NFC)
                            binding.btnShareMdl.isEnabled = true
                        }
                    }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                binding.btnShareMdl.isEnabled = false
            }
        }
    }

    private fun subscribeUi() {
        val vm = ViewModelProvider(this).get(CredentialsListViewModel::class.java)

        binding.vm = vm
        binding.fragment = this

        vm.credentialLoadStatus().observe(viewLifecycleOwner, { isSuccess ->
            if (!isSuccess) {
                CustomAlertDialog(requireContext()) {}.showErrorDialog(
                    getString(R.string.load_credential_error_title),
                    getString(R.string.load_credential_error)
                )
            }
        })
        vm.provisionCredential()

    }

    fun onShare(view: View) {
        if (view.findNavController().currentDestination?.id == R.id.credentialsListFragment) {
            view.findNavController()
                .navigate(R.id.action_credentialsListFragment_to_shareCredentialsFragment)
        }
    }

    companion object {
        const val LOG_TAG = "CredentialsListFragment"
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}