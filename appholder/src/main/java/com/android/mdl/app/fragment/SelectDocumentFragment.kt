package com.android.mdl.app.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.mdl.app.adapter.DocumentAdapter
import com.android.mdl.app.databinding.FragmentSelectDocumentBinding
import com.android.mdl.app.document.DocumentManager
import com.android.mdl.app.transfer.TransferManager


class SelectDocumentFragment : Fragment() {
    companion object {
        private const val LOG_TAG = "SelectDocumentFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentSelectDocumentBinding.inflate(inflater)
        val adapter = DocumentAdapter()
        binding.fragment = this
        binding.documentList.adapter = adapter

        val documentManager = DocumentManager.getInstance(requireContext())
        // Call stop presentation to finish all presentation that could be running
        val transferManager = TransferManager.getInstance(requireContext())
        transferManager.stopPresentation()

        adapter.submitList(documentManager.getDocuments().toMutableList())

        // Location access is needed for BLE to work.
        //
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        return binding.root
    }

    fun onStartProvisioning() {
        findNavController().navigate(
            SelectDocumentFragmentDirections.actionSelectDocumentFragmentToProvisioningFragment()
        )
    }

    private val permissionsLauncher =
        registerForActivityResult(RequestPermission()) { permission ->
            Log.d(LOG_TAG, "permissionsLauncher $permission")

            if (!permission) {
                Toast.makeText(
                    activity,
                    "Need location permission to scan for BLE devices",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
}