package com.android.mdl.app.wallet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.android.mdl.app.R
import com.android.mdl.app.adapter.DocumentAdapter
import com.android.mdl.app.databinding.FragmentSelectDocumentBinding
import com.android.mdl.app.document.Document
import com.android.mdl.app.document.DocumentManager
import com.android.mdl.app.util.TransferStatus
import com.android.mdl.app.viewmodel.ShareDocumentViewModel
import com.google.android.material.tabs.TabLayoutMediator

class SelectDocumentFragment : Fragment() {
    companion object {
        private const val LOG_TAG = "SelectDocumentFragment"
    }

    private var _binding: FragmentSelectDocumentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ShareDocumentViewModel by activityViewModels()
    private val timeInterval = 2000 // # milliseconds passed between two back presses
    private var mBackPressed: Long = 0

    private val appPermissions: Array<String> =
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
                        Toast.makeText(
                            requireContext(),
                            R.string.toast_press_back_twice,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    mBackPressed = System.currentTimeMillis()
                }
            })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSelectDocumentBinding.inflate(inflater)
        val adapter = DocumentAdapter()
        binding.vpDocuments.adapter = adapter
        binding.fragment = this
        setupDocumentsPager(binding)

        val documentManager = DocumentManager.getInstance(requireContext())
        setupScreen(binding, adapter, documentManager.getDocuments().toMutableList())

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.getTransferStatus().observe(viewLifecycleOwner) {
            when (it) {
                TransferStatus.CONNECTED -> {
                    openTransferScreen()
                }

                TransferStatus.ERROR -> {
                    binding.tvNfcLabel.text = "Error on presentation!"
                }
                //Shall we update the top label of the screen for each state?
                else -> {}
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupDocumentsPager(binding: FragmentSelectDocumentBinding) {
        TabLayoutMediator(binding.tlPageIndicator, binding.vpDocuments) { _, _ -> }.attach()
        binding.vpDocuments.offscreenPageLimit = 1
        binding.vpDocuments.setPageTransformer(DocumentPageTransformer(requireContext()))
        val itemDecoration = DocumentPagerItemDecoration(
            requireContext(),
            R.dimen.viewpager_current_item_horizontal_margin
        )
        binding.vpDocuments.addItemDecoration(itemDecoration)
    }

    private fun setupScreen(
        binding: FragmentSelectDocumentBinding,
        adapter: DocumentAdapter,
        documentsList: MutableList<Document>
    ) {
        if (documentsList.isEmpty()) {
            showEmptyView(binding)
        } else {
            adapter.submitList(documentsList)
            showDocumentsPager(binding)
        }
    }

    private fun openTransferScreen() {
        val destination = SelectDocumentFragmentDirections.toTransferDocument()
        findNavController().navigate(destination)
    }

    private fun showEmptyView(binding: FragmentSelectDocumentBinding) {
        binding.vpDocuments.visibility = View.GONE
        binding.cvEmptyView.visibility = View.VISIBLE
        binding.btShowQr.visibility = View.GONE
        binding.btAddDocument.setOnClickListener { openAddDocument() }
    }

    private fun showDocumentsPager(binding: FragmentSelectDocumentBinding) {
        binding.vpDocuments.visibility = View.VISIBLE
        binding.cvEmptyView.visibility = View.GONE
        binding.btShowQr.visibility = View.VISIBLE
        binding.btShowQr.setOnClickListener { displayQRCode() }
    }

    private fun displayQRCode() {
        val destination = SelectDocumentFragmentDirections.toShowQR()
        findNavController().navigate(destination)
    }

    private fun openAddDocument() {
        val destination = SelectDocumentFragmentDirections.toAddSelfSigned()
        findNavController().navigate(destination)
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