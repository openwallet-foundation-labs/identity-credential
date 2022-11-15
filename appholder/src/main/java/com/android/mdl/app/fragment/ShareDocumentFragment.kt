package com.android.mdl.app.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.android.mdl.app.databinding.FragmentShareDocumentBinding
import com.android.mdl.app.util.TransferStatus
import com.android.mdl.app.viewmodel.ShareDocumentViewModel
import java.lang.IllegalStateException

class ShareDocumentFragment : Fragment() {

    private val viewModel: ShareDocumentViewModel by viewModels()

    private var _binding: FragmentShareDocumentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShareDocumentBinding.inflate(inflater)
        binding.vm = viewModel
        binding.fragment = this
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.message.set("NFC tap with mdoc verifier device")
        viewModel.getTransferStatus().observe(viewLifecycleOwner) {
            when (it) {
                TransferStatus.QR_ENGAGEMENT_READY -> {
                    viewModel.message.set("Scan QR code with mdoc verifier device")
                    viewModel.showQrCode()
                }

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
        try {
            viewModel.startPresentation()
        } catch (nullPointer: NullPointerException) {
            //Session was terminated
            findNavController().navigateUp()
        } catch (transferStarted: IllegalStateException) {
            //The transfer is started, proceed
        }
        viewModel.triggerQrEngagement()
    }

    // This callback will only be called when MyFragment is at least Started.
    var callback = object : OnBackPressedCallback(true /* enabled by default */) {
        override fun handleOnBackPressed() {
            onCancel()
        }
    }

    fun onCancel() {
        viewModel.cancelPresentation()
        findNavController().navigateUp()
    }
}