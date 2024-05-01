package com.android.identity.wallet.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.android.identity.wallet.databinding.FragmentShareDocumentBinding
import com.android.identity.wallet.util.TransferStatus
import com.android.identity.wallet.viewmodel.ShareDocumentViewModel

class ShareDocumentFragment : Fragment() {
    private val viewModel: ShareDocumentViewModel by viewModels()

    private var _binding: FragmentShareDocumentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentShareDocumentBinding.inflate(inflater)
        binding.vm = viewModel
        binding.fragment = this
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackCallback)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.message.set("Scan QR code with mdoc verifier device")
        viewModel.getTransferStatus().observe(viewLifecycleOwner) {
            when (it) {
                TransferStatus.CONNECTED -> {
                    viewModel.message.set("Connected!")
                    val destination = ShareDocumentFragmentDirections.toTransferDocumentFragment()
                    findNavController().navigate(destination)
                }

                TransferStatus.REQUEST -> {
                    viewModel.message.set("Request received!")
                }

                TransferStatus.DISCONNECTED -> {
                    viewModel.message.set("Disconnected!")
                    findNavController().navigateUp()
                }

                TransferStatus.ERROR -> {
                    viewModel.message.set("Error on presentation!")
                }

                TransferStatus.ENGAGEMENT_DETECTED -> {
                    viewModel.message.set("Engagement detected!")
                }

                TransferStatus.CONNECTING -> {
                    viewModel.message.set("Connecting...")
                }

                else -> {}
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.triggerQrEngagement()
        viewModel.showQrCode()
    }

    private val onBackCallback =
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onCancel()
            }
        }

    fun onCancel() {
        viewModel.cancelPresentation()
        findNavController().navigateUp()
    }
}
