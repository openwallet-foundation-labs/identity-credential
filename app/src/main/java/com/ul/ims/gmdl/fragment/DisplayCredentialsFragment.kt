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
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.ul.ims.gmdl.databinding.FragmentDisplayCredentialsBinding
import com.ul.ims.gmdl.dialog.CustomAlertDialog
import com.ul.ims.gmdl.offlinetransfer.utils.Log
import com.ul.ims.gmdl.viewmodel.DisplayCredentialsViewModel

/**
 * A simple [Fragment] subclass.
 *
 */
class DisplayCredentialsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val args : DisplayCredentialsFragmentArgs by navArgs()
        val binding = FragmentDisplayCredentialsBinding.inflate(inflater)

        subscribeUi(binding, args)

        return binding.root
    }

    fun subscribeUi(binding : FragmentDisplayCredentialsBinding, args: DisplayCredentialsFragmentArgs) {
        val vm = ViewModelProviders.of(this).get(DisplayCredentialsViewModel::class.java)

        binding.fragment = this
        binding.vm = vm
        vm.getCredentialLiveData().observe(this, Observer {
            binding.user = it
        })

        vm.getProvisionErrorsLiveData().observe(this, Observer {err->
            CustomAlertDialog(requireContext()) { navigateBack() }
                .showErrorDialog("error", err.localizedMessage)
        })

        // If there is a credential in the args it means that it's result of a BLE transfer and we should display it
        val credential = args.credential
        vm.provisionCredential(credential)

        Log.d("issuerDataAuthentication", credential?.issuerDataAuthentication.toString())
        Log.d("deviceSign", credential?.deviceSign.toString())
    }

    fun onShare(view: View) {
//        view.findNavController().navigate(R.id.action_displayCredentialsFragment_to_shareCredentialsFragment)
    }

    fun onShareNFC(view: View) {
//        view.findNavController().navigate(R.id.action_displayCredentialsFragment_to_shareCredentialsNfcFragment)
    }

    private fun navigateBack() {
        findNavController().popBackStack()
    }
}
