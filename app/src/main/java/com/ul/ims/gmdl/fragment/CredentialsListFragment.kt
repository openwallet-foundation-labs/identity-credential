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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.ul.ims.gmdl.R
import com.ul.ims.gmdl.databinding.FragmentCredentialsListBinding
import com.ul.ims.gmdl.dialog.CustomAlertDialog
import com.ul.ims.gmdl.offlinetransfer.transportLayer.EngagementChannels
import com.ul.ims.gmdl.util.SettingsUtils
import com.ul.ims.gmdl.viewmodel.CredentialsListViewModel

class CredentialsListFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentCredentialsListBinding.inflate(inflater)

        subscribeUi(binding)

        return binding.root
    }

    private fun subscribeUi(binding: FragmentCredentialsListBinding) {
        val vm= ViewModelProvider(this).
            get(CredentialsListViewModel::class.java)

        binding.vm = vm
        binding.fragment = this

        vm.credentialLoadStatus().observe(viewLifecycleOwner, Observer {isSuccess ->
            if (!isSuccess) {
                CustomAlertDialog(requireContext()) {navigateBack()}.showErrorDialog(
                    getString(R.string.load_credential_error_title), getString(R.string.load_credential_error)
                )
            }
        })
        vm.provisionCredential()

    }

    fun onShare(view: View) {
        when (SettingsUtils.getEngagementMethod(requireContext())) {
            EngagementChannels.QR ->
                view.findNavController()
                    .navigate(R.id.action_credentialsListFragment_to_shareCredentialsFragment)
            EngagementChannels.NFC ->
                view.findNavController()
                    .navigate(R.id.action_credentialsListFragment_to_shareCredentialsNfcFragment)
        }
    }

    private fun navigateBack() {
        findNavController().popBackStack()
    }

    companion object {
        const val LOG_TAG = "CredentialsListFragment"
    }
}