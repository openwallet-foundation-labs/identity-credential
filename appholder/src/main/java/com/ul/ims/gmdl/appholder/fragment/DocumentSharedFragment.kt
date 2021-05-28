package com.ul.ims.gmdl.appholder.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.ul.ims.gmdl.appholder.R
import com.ul.ims.gmdl.appholder.databinding.FragmentDocumentSharedBinding
import com.ul.ims.gmdl.appholder.databinding.FragmentShareDocumentBinding
import com.ul.ims.gmdl.appholder.fragment.DocumentSharedFragmentDirections.Companion.actionDocumentSharedFragmentToSelectDocumentFragment
import com.ul.ims.gmdl.appholder.transfer.TransferManager
import com.ul.ims.gmdl.appholder.viewmodel.ShareDocumentViewModel

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