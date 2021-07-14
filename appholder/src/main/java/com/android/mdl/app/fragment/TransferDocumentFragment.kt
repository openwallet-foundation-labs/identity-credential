package com.android.mdl.app.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.security.identity.InvalidRequestMessageException
import com.android.mdl.app.databinding.FragmentTransferDocumentBinding
import com.android.mdl.app.document.Document
import com.android.mdl.app.fragment.TransferDocumentFragmentDirections.Companion.actionTransferDocumentFragmentToDocumentSharedFragment
import com.android.mdl.app.fragment.TransferDocumentFragmentDirections.Companion.actionTransferDocumentFragmentToSelectDocumentFragment
import com.android.mdl.app.util.TransferStatus
import com.android.mdl.app.viewmodel.TransferDocumentViewModel


class TransferDocumentFragment : Fragment() {

    companion object {
        private const val LOG_TAG = "TransferDocumentFragment"
    }

    private val args: ShareDocumentFragmentArgs by navArgs()
    private lateinit var document: Document

    private var _binding: FragmentTransferDocumentBinding? = null
    private lateinit var vm: TransferDocumentViewModel

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        document = args.document

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransferDocumentBinding.inflate(inflater)
        vm = ViewModelProvider(this).get(TransferDocumentViewModel::class.java)

        binding.fragment = this

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vm.getTransferStatus().observe(viewLifecycleOwner, {
            when (it) {
                TransferStatus.ENGAGEMENT_READY -> {
                    Log.d(LOG_TAG, "Engagement Ready")
                }
                TransferStatus.CONNECTED -> {
                    Log.d(LOG_TAG, "Connected")
                }
                TransferStatus.REQUEST -> {
                    Log.d(LOG_TAG, "Request")
                }
                TransferStatus.DISCONNECTED -> {
                    findNavController().navigate(
                        actionTransferDocumentFragmentToDocumentSharedFragment()
                    )
                }
                TransferStatus.ERROR -> {
                    Toast.makeText(
                        requireContext(), "An error occurred.",
                        Toast.LENGTH_SHORT
                    ).show()
                    findNavController().navigate(
                        actionTransferDocumentFragmentToSelectDocumentFragment()
                    )
                }
            }
        }
        )

        try {
            vm.sendResponse()
        } catch (e: InvalidRequestMessageException) {
            Log.e(LOG_TAG, "Send response error: ${e.message}")
            Toast.makeText(
                requireContext(), "Send response error: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }

    }

    var callback = object : OnBackPressedCallback(true /* enabled by default */) {
        override fun handleOnBackPressed() {
            onDone()
        }
    }

    fun onDone() {
        vm.cancelPresentation()
        findNavController().navigate(
            actionTransferDocumentFragmentToSelectDocumentFragment()
        )
    }
}