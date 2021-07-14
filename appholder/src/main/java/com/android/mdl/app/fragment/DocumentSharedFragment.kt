package com.android.mdl.app.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.mdl.app.databinding.FragmentDocumentSharedBinding
import com.android.mdl.app.fragment.DocumentSharedFragmentDirections.Companion.actionDocumentSharedFragmentToSelectDocumentFragment
import com.android.mdl.app.transfer.TransferManager

class DocumentSharedFragment : Fragment() {

    private var _binding: FragmentDocumentSharedBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDocumentSharedBinding.inflate(inflater)

        binding.fragment = this

        // Transfer is finished, stop presentation
        val transferManager = TransferManager.getInstance(requireContext())
        transferManager.stopPresentation()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        return binding.root
    }

    // This callback will only be called when MyFragment is at least Started.
    var callback = object : OnBackPressedCallback(true /* enabled by default */) {
        override fun handleOnBackPressed() {
            onDone()
        }
    }

    fun onDone() {
        findNavController().navigate(
            actionDocumentSharedFragmentToSelectDocumentFragment()
        )
    }
}