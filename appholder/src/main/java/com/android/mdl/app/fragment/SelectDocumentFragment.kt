package com.android.mdl.app.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.mdl.app.R
import com.android.mdl.app.adapter.DocumentAdapter
import com.android.mdl.app.databinding.FragmentSelectDocumentBinding
import com.android.mdl.app.document.DocumentManager
import com.android.mdl.app.transfer.TransferManager
import org.jetbrains.anko.support.v4.toast


class SelectDocumentFragment : Fragment() {
    companion object {
        private const val LOG_TAG = "SelectDocumentFragment"
    }

    private val timeInterval = 2000 // # milliseconds passed between two back presses
    private var mBackPressed: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ask to press twice before leave the app
        requireActivity().onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (mBackPressed + timeInterval > System.currentTimeMillis()) {
                        requireActivity().finish()
                        return
                    } else {
                        toast(getString(R.string.toast_press_back_twice))
                    }
                    mBackPressed = System.currentTimeMillis()
                }
            })
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

    fun onPresentDocuments() {
        findNavController().navigate(
            SelectDocumentFragmentDirections.actionSelectDocumentFragmentToShareDocumentFragment()
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