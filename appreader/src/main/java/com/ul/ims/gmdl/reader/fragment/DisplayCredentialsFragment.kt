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

import android.nfc.NfcAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.ul.ims.gmdl.offlinetransfer.utils.Log
import com.ul.ims.gmdl.reader.databinding.FragmentDisplayCredentialsBinding
import com.ul.ims.gmdl.reader.dialog.CustomAlertDialog
import com.ul.ims.gmdl.reader.viewmodel.DisplayCredentialsViewModel

/**
 * A simple [Fragment] subclass.
 *
 */
class DisplayCredentialsFragment : Fragment() {

    companion object {
        private const val LOG_TAG = "DisplayCredentialsFragment"
        private const val READER_FLAGS = (NfcAdapter.FLAG_READER_NFC_A
                or NfcAdapter.FLAG_READER_NFC_B
                or NfcAdapter.FLAG_READER_NFC_F
                or NfcAdapter.FLAG_READER_NFC_V
                or NfcAdapter.FLAG_READER_NFC_BARCODE)
    }

    private var nfcAdapter: NfcAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val args: DisplayCredentialsFragmentArgs by navArgs()
        val binding = FragmentDisplayCredentialsBinding.inflate(inflater)

        subscribeUi(binding, args)

        nfcAdapter = NfcAdapter.getDefaultAdapter(requireContext())

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        // Enable as reader to not allow other apps to open when using NFC transfer
        nfcAdapter?.enableReaderMode(activity, null, READER_FLAGS, null)
    }

    override fun onPause() {
        super.onPause()

        nfcAdapter?.disableReaderMode(activity)
    }

    private fun subscribeUi(
        binding: FragmentDisplayCredentialsBinding,
        args: DisplayCredentialsFragmentArgs
    ) {
        val vm = ViewModelProvider(this).get(DisplayCredentialsViewModel::class.java)

        binding.fragment = this
        binding.vm = vm
        vm.getCredentialLiveData().observe(viewLifecycleOwner, {
            binding.user = it
        })

        vm.getProvisionErrorsLiveData().observe(viewLifecycleOwner, { err ->
            CustomAlertDialog(requireContext()) { navigateBack() }
                .showErrorDialog("error", err.localizedMessage)
        })

        // If there is a credential in the args it means that it's result of a BLE transfer and we should display it
        val credential = args.credential
        vm.provisionCredential(credential)

        Log.d("issuerDataAuthentication", credential?.issuerDataAuthentication.toString())
        Log.d("deviceSign", credential?.deviceSign.toString())
    }

    private fun navigateBack() {
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        nfcAdapter = null
        super.onDestroyView()
    }
}
