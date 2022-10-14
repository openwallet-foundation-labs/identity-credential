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
import com.android.identity.IdentityCredential
import com.android.mdl.app.databinding.FragmentDocumentDetailBinding
import com.android.mdl.app.document.Document
import com.android.mdl.app.document.DocumentManager

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
    private var identityCredential: IdentityCredential? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        document = args.document

        val documentManager = DocumentManager.getInstance(requireContext())
        identityCredential = documentManager.getCredential(document)

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
        bindingUI()

        return binding.root
    }

    private fun bindingUI() {
        binding.fragment = this
        binding.document = document
        binding.tvSelfSigned.text = if (document.selfSigned) "Yes" else "No"
        binding.tvUserAuth.text = if (document.userAuthentication) "Yes" else "No"
        binding.tvHardwareBacked.text = if (document.hardwareBacked) "Yes" else "No"
        binding.tvMsoUsageCount.text =
            identityCredential?.authenticationDataUsageCount.contentToString()
        binding.btCheckUpdates.isEnabled = !document.selfSigned
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

    fun onRefreshAuthKeys() {
        val serverUrl = document.serverUrl
        if (serverUrl == null) {
            // If server URL is null
            Toast.makeText(
                requireContext(), "Document doesn't have server information",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        findNavController().navigate(
            DocumentDetailFragmentDirections.actionDocumentDetailFragmentToRefreshAuthKeyFragment(
                serverUrl,
                document
            )
        )
    }

    fun onShowData() {
        val direction = DocumentDetailFragmentDirections.navigateToDocumentData(document)
        findNavController().navigate(direction)
    }

    fun onDelete() {
        if (document.selfSigned) {
            val documentManager = DocumentManager.getInstance(requireContext())
            documentManager.deleteCredentialByName(document.identityCredentialName)
            Toast.makeText(
                requireContext(), "Document deleted!",
                Toast.LENGTH_SHORT
            ).show()
            findNavController().navigate(
                DocumentDetailFragmentDirections.actionDocumentDetailFragmentToSelectDocumentFragment()
            )
            return
        } else if (document.serverUrl == null) {
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