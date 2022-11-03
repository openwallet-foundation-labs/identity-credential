package com.android.mdl.app.present

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.mdl.app.R
import com.android.mdl.app.databinding.FragmentPresentDocumentBinding

class PresentDocumentFragment : Fragment(R.layout.fragment_present_document) {

    private var _binding: FragmentPresentDocumentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPresentDocumentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btPresentDocuments.setOnClickListener { openPresentDocuments() }
        binding.btPresentDocumentsReverseEngagement.setOnClickListener { openReverseEngagement() }
    }

    private fun openPresentDocuments() {
        val destination = PresentDocumentFragmentDirections.toShareDocument()
        findNavController().navigate(destination)
    }

    private fun openReverseEngagement() {
        val destination = PresentDocumentFragmentDirections.toReverseEngagement()
        findNavController().navigate(destination)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}