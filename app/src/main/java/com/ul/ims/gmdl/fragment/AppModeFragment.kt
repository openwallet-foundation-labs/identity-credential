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
import android.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.findNavController
import com.ul.ims.gmdl.R
import com.ul.ims.gmdl.databinding.FragmentAppModeBinding

class AppModeFragment : Fragment() {

    private lateinit var navController: NavController

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentAppModeBinding.inflate(inflater)
        binding.fragment = this

        navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment)

        setHasOptionsMenu(true)

        return binding.root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu, menu)
        return super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        val id = item.itemId

        if (id == R.id.app_settings) {
            navController.navigate(R.id.action_appModeFragment_to_SettingsFragment)
        }

        return super.onOptionsItemSelected(item)
    }


    fun onHolderSelected(view: View) {
        // If we set the credentials to null, DisplayCredentialsFragment will know that it should retrieve
        // the user credentials from Google Identity Credential Framework otherwise it will expect that
        // a credential was received by a offline mechanism and must be passed as argument to the fragment to
        // be displayed
        val action = AppModeFragmentDirections.actionAppModeFragmentToCredentialsListFragment()

        view.findNavController().navigate(action)

    }
}
