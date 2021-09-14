package com.android.mdl.appreader.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.security.identity.IdentityCredentialVerification
import com.android.mdl.appreader.R
import com.android.mdl.appreader.databinding.FragmentTransferBinding
import com.android.mdl.appreader.util.TransferStatus
import com.android.mdl.appreader.viewModel.TransferViewModel


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class TransferFragment : Fragment() {

    companion object {
        private const val LOG_TAG = "TransferFragment"
        private const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        private const val MDL_NAMESPACE = "org.iso.18013.5.1"
        private const val AAMVA_NAMESPACE = "org.aamva.18013.5.1"
    }

    private var _binding: FragmentTransferBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var vm: TransferViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentTransferBinding.inflate(inflater, container, false)
        vm = ViewModelProvider(this).get(TransferViewModel::class.java)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vm.getTransferStatus().observe(viewLifecycleOwner, {
            when (it) {
                TransferStatus.CONNECTED -> {
                    binding.tvStatus.text = "Connected. Requesting mDoc..."
                    vm.sendRequest(createRequest())
                }
                TransferStatus.RESPONSE -> {
                    Log.d(LOG_TAG, "Navigating to results")
                    findNavController().navigate(R.id.action_Transfer_to_ShowDocument)
                }
                TransferStatus.DISCONNECTED -> {
                    Toast.makeText(
                        requireContext(), "Error: Disconnected",
                        Toast.LENGTH_SHORT
                    ).show()
                    findNavController().navigate(R.id.action_Transfer_to_RequestOptions)
                }
                TransferStatus.ERROR -> {
                    Toast.makeText(
                        requireContext(), "Error connecting to holder",
                        Toast.LENGTH_SHORT
                    ).show()
                    findNavController().navigate(R.id.action_Transfer_to_RequestOptions)
                }
            }
        })

        try {
            binding.tvStatus.text = "Trying to connect to mDoc app..."
            vm.connect()
        } catch (e: RuntimeException) {
            Log.e(LOG_TAG, "Error starting connection: ${e.message}", e)
            Toast.makeText(
                requireContext(), "Error starting connection: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btCancel.setOnClickListener {
            findNavController().navigate(R.id.action_Transfer_to_RequestOptions)
        }
    }


    private fun createRequest(): IdentityCredentialVerification.DeviceRequest {
        val documentRequestBuilder = IdentityCredentialVerification.DocumentRequest.Builder(
            MDL_DOCTYPE
        )
        documentRequestBuilder.addRequestNamespace(
            MDL_NAMESPACE, mapOf<String, Boolean>(
                Pair("family_name", true),
                Pair("portrait", false)
            )
        )
        documentRequestBuilder.addRequestNamespace(
            AAMVA_NAMESPACE, mapOf<String, Boolean>(
                Pair("real_id", false)
            )
        )
        val deviceRequestBuilder = IdentityCredentialVerification.DeviceRequest.Builder()
        deviceRequestBuilder.addDocumentRequest(documentRequestBuilder.build())
        return deviceRequestBuilder.build()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}