package com.android.mdl.app.adddocument

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.mdl.app.R
import com.android.mdl.app.databinding.FragmentAddDocumentBinding

class AddDocumentFragment : Fragment(R.layout.fragment_add_document) {

    private var _binding: FragmentAddDocumentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddDocumentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btAddDocument.setOnClickListener { openAddDocument() }
        binding.btAddSelfSignedDocument.setOnClickListener { openAddSelfSignedDocument() }
    }

    private fun openAddDocument() {
        val destination = AddDocumentFragmentDirections.toAddDocument()
        findNavController().navigate(destination)
    }

    private fun openAddSelfSignedDocument() {
        val destination = AddDocumentFragmentDirections.toAddSelfSigned()
        findNavController().navigate(destination)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
