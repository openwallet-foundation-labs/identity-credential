package com.android.mdl.appreader.fragment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import co.nstant.`in`.cbor.model.SimpleValue
import com.android.mdl.appreader.R
import com.android.mdl.appreader.databinding.FragmentDeviceEngagementBinding
import com.android.mdl.appreader.transfer.TransferManager
import com.android.mdl.appreader.util.FormatUtil
import com.budiyev.android.codescanner.CodeScanner
import com.budiyev.android.codescanner.DecodeCallback


/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class DeviceEngagementFragment : Fragment() {

    companion object {
        private const val LOG_TAG = "DeviceEngagementFragment"
    }

    private val args: DeviceEngagementFragmentArgs by navArgs()

    private val appPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    private var _binding: FragmentDeviceEngagementBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var mCodeScanner: CodeScanner? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentDeviceEngagementBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // QR Code Engagement
        mCodeScanner = CodeScanner(requireContext(), binding.csScanner)
        mCodeScanner?.decodeCallback = DecodeCallback { result ->
            requireActivity().runOnUiThread {
                val qrText = result.text
                Log.d(LOG_TAG, "qrText: $qrText")
                val uri = Uri.parse(qrText)
                if (uri?.scheme != null && uri.scheme.equals("mdoc")) {
                    val encodedDeviceEngagement = Base64.decode(
                        uri.encodedSchemeSpecificPart,
                        Base64.URL_SAFE or Base64.NO_PADDING
                    )
                    if (encodedDeviceEngagement != null) {
                        val encodedHandover = FormatUtil.cborEncode(SimpleValue.NULL)
                        val availableTransferMethods = FormatUtil.extractDeviceRetrievalMethods(
                            encodedDeviceEngagement
                        )

                        onDeviceEngagementReceived(
                            encodedDeviceEngagement, encodedHandover, availableTransferMethods
                        )
                    }
                }
            }
        }

        binding.csScanner.setOnClickListener { mCodeScanner?.startPreview() }

        if (isAllPermissionsGranted()) {
            mCodeScanner?.startPreview()
        } else {
            shouldRequestPermission()
        }

        binding.btCancel.setOnClickListener {
            findNavController().navigate(R.id.action_ScanDeviceEngagement_to_RequestOptions)
        }
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

    private fun onDeviceEngagementReceived(
        encodedDeviceEngagement: ByteArray,
        encodedHandover: ByteArray,
        availableTransferMethods: Collection<ByteArray>
    ) {
        Log.d(LOG_TAG, "DE: ${FormatUtil.encodeToString(encodedDeviceEngagement)}")
        val transferManager = TransferManager.getInstance(requireContext())
        transferManager.startEngagement(encodedDeviceEngagement, encodedHandover)
        transferManager.setAvailableTransferMethods(availableTransferMethods)

        if (availableTransferMethods.size == 1) {
            findNavController().navigate(
                DeviceEngagementFragmentDirections.actionScanDeviceEngagementToTransfer(
                    args.requestDocument
                )
            )
        } else {
            findNavController().navigate(
                DeviceEngagementFragmentDirections.actionScanDeviceEngagementToSelectTransport(
                    args.requestDocument
                )
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}