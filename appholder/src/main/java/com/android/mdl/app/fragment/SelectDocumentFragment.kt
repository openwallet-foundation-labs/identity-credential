package com.android.mdl.app.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.mdl.app.R
import com.android.mdl.app.adapter.DocumentAdapter
import com.android.mdl.app.databinding.FragmentSelectDocumentBinding
import com.android.mdl.app.document.DocumentManager
import com.android.mdl.app.transfer.TransferManager

class SelectDocumentFragment : Fragment() {
    companion object {
        private const val LOG_TAG = "SelectDocumentFragment"
    }

    private val timeInterval = 2000 // # milliseconds passed between two back presses
    private var mBackPressed: Long = 0

    private val appPermissions:Array<String> =
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }

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
                        Toast.makeText(requireContext(), R.string.toast_press_back_twice, Toast.LENGTH_SHORT).show()
                    }
                    mBackPressed = System.currentTimeMillis()
                }
            })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setHasOptionsMenu(true)

        val binding = FragmentSelectDocumentBinding.inflate(inflater)
        val adapter = DocumentAdapter()
        binding.fragment = this
        binding.documentList.adapter = adapter

        val documentManager = DocumentManager.getInstance(requireContext())
        // Call stop presentation to finish all presentation that could be running
        val transferManager = TransferManager.getInstance(requireContext())
        transferManager.stopPresentation(
            sendSessionTerminationMessage = true,
            useTransportSpecificSessionTermination = false
        )

        adapter.submitList(documentManager.getDocuments().toMutableList())

        val permissionsNeeded = appPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(
                requireContext(),
                permission
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNeeded.isNotEmpty()) {
            permissionsLauncher.launch(
                permissionsNeeded.toTypedArray()
            )
        }

        return binding.root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.main_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.item_settings -> {
                findNavController().navigate(R.id.action_selectDocumentFragment_to_settingsFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun onStartProvisioning() {
        findNavController().navigate(
            SelectDocumentFragmentDirections.actionSelectDocumentFragmentToProvisioningFragment()
        )
    }

    fun onProvisioningSelfSigned() {
        findNavController().navigate(
            SelectDocumentFragmentDirections.actionSelectDocumentFragmentToAddSelfSigned()
        )
    }

    fun onPresentDocuments() {
        findNavController().navigate(
            SelectDocumentFragmentDirections.actionSelectDocumentFragmentToShareDocumentFragment()
        )
    }

    fun onPresentDocumentsReverseEngagement() {
        findNavController().navigate(
            SelectDocumentFragmentDirections.actionSelectDocumentFragmentToReverseEngagementFragment()
        )
    }

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Log.d(LOG_TAG, "permissionsLauncher ${it.key} = ${it.value}")

                if (!it.value) {
                    Toast.makeText(
                        activity,
                        "The ${it.key} permission is required for BLE",
                        Toast.LENGTH_LONG
                    ).show()
                    return@registerForActivityResult
                }
            }
        }
}