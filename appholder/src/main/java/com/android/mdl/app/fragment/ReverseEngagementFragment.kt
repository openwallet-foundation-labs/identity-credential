package com.android.mdl.app.fragment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.android.identity.OriginInfo
import com.android.identity.OriginInfoQr
import com.android.mdl.app.databinding.FragmentReverseEngagementBinding
import com.android.mdl.app.viewmodel.ShareDocumentViewModel
import com.budiyev.android.codescanner.CodeScanner
import com.budiyev.android.codescanner.DecodeCallback


class ReverseEngagementFragment : Fragment() {
    companion object {
        private const val LOG_TAG = "ReverseEngagementFragment"
    }

    private var _binding: FragmentReverseEngagementBinding? = null
    private lateinit var vm: ShareDocumentViewModel

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    private var codeScanner: CodeScanner? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReverseEngagementBinding.inflate(inflater)
        vm = ViewModelProvider(this).get(ShareDocumentViewModel::class.java)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btCancel.setOnClickListener {
            findNavController().navigate(
                ReverseEngagementFragmentDirections.actionReverseEngagementFragmentToSelectDocumentFragment()
            )
        }

        codeScanner = CodeScanner(requireContext(), binding.csScanner)
        codeScanner?.decodeCallback = DecodeCallback { result ->
            requireActivity().runOnUiThread {
                val qrText = result.text
                Log.d(LOG_TAG, "qrText: $qrText")
                val uri = Uri.parse(qrText)
                if (uri.scheme.equals("mdoc")) {
                    val originInfos = ArrayList<OriginInfo>()
                    originInfos.add(OriginInfoQr(1))
                    vm.startPresentationReverseEngagement(qrText, originInfos)
                    findNavController().navigate(
                        ReverseEngagementFragmentDirections.actionReverseEngagementFragmentToTransferDocumentFragment()
                    )
                } else {
                    Log.w(LOG_TAG, "Ignoring QR code with scheme " + uri.scheme)
                }
            }
        }
        binding.csScanner.setOnClickListener { codeScanner?.startPreview() }
    }

    override fun onResume() {
        super.onResume()
        enableReader()
    }

    override fun onPause() {
        super.onPause()
        disableReader()
    }

    private fun enableReader() {
        if (isAllPermissionsGranted()) {
            codeScanner?.startPreview()
        } else {
            shouldRequestPermission()
        }
    }

    private fun disableReader() {
        codeScanner?.releaseResources()
    }

    private val appPermissions:List<String> get() {
        var permissions = mutableListOf(
            Manifest.permission.CAMERA,
        )
        return permissions
    }

    private fun shouldRequestPermission() {
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
    }

    private fun isAllPermissionsGranted(): Boolean {
        // If any permission is not granted return false
        return appPermissions.none { permission ->
            ContextCompat.checkSelfPermission(
                requireContext(),
                permission
            ) != PackageManager.PERMISSION_GRANTED
        }
    }

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Log.d(LOG_TAG, "permissionsLauncher ${it.key} = ${it.value}")

                // Open settings if user denied any required permission
                if (!it.value && !shouldShowRequestPermissionRationale(it.key)) {
                    openSettings()
                    return@registerForActivityResult
                }
            }
        }

    private fun openSettings() {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        intent.data = Uri.fromParts("package", requireContext().packageName, null)
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}