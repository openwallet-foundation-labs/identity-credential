package com.android.mdl.app.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.android.mdl.app.databinding.FragmentDocumentDetailBinding
import com.android.mdl.app.document.Document

class DocumentDetailFragment : Fragment() {
    companion object {
        private const val LOG_TAG = "DocumentDetailFragment"
    }

    private var _binding: FragmentDocumentDetailBinding? = null
    private val args: DocumentDetailFragmentArgs by navArgs()

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var document: Document

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        document = args.document
        // Always return to main fragment
        requireActivity().onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    findNavController().navigate(
                        DocumentDetailFragmentDirections.actionDocumentDetailFragmentToSelectDocumentFragment()
                    )
                }
            })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDocumentDetailBinding.inflate(inflater)
        binding.fragment = this
        binding.document = document

        return binding.root
    }

    fun onCheckUpdate() {
        if (document.serverUrl == null) {
            // If server URL is null
            Toast.makeText(
                requireContext(), "Document doesn't have server information",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        findNavController().navigate(
            DocumentDetailFragmentDirections.actionDocumentDetailFragmentToUpdateCheckFragment(
                document
            )
        )
    }

    fun onDelete() {
        if (document.serverUrl == null) {
            // If server URL is null
            Toast.makeText(
                requireContext(), "Document doesn't have server information",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        findNavController().navigate(
            DocumentDetailFragmentDirections.actionDocumentDetailFragmentToDeleteDocumentFragment(
                document
            )
        )
    }


}