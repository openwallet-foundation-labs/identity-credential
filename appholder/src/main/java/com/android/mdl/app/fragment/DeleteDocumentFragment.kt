package com.android.mdl.app.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.android.mdl.app.databinding.FragmentDeleteDocumentBinding
import com.android.mdl.app.document.Document
import com.android.mdl.app.document.DocumentManager
import com.android.mdl.app.provisioning.DeleteFlow
import com.android.mdl.app.util.FormatUtil
import com.android.mdl.app.util.log

class DeleteDocumentFragment : Fragment() {

    private val args: UpdateCheckFragmentArgs by navArgs()
    private var serverUrl: String? = null
    private lateinit var document: Document

    private var _binding: FragmentDeleteDocumentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        document = args.document
        serverUrl = document.serverUrl
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeleteDocumentBinding.inflate(inflater)
        binding.fragment = this

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startRefreshAuthKeyFlow()
    }

    fun onDone() {
        findNavController().navigate(
            DeleteDocumentFragmentDirections.actionDeleteDocumentFragmentToSelectDocumentFragment()
        )
    }

    private fun startRefreshAuthKeyFlow() {
        val documentManager = DocumentManager.getInstance(requireContext())
        val credential = documentManager.getCredential(document)

        if (credential == null) {
            showError("\n- Error on retrieving a document for ${document.userVisibleName}\n")
            return
        }

        // If server URL is null
        if (serverUrl?.isNotEmpty() != true) {
            showError("\n- Error invalid server information\n")
            return
        }

        // Start refresh auth key flow
        val deleteFlow = DeleteFlow.getInstance(requireContext(), serverUrl)

        deleteFlow.setListener(object : DeleteFlow.Listener {
            override fun onMessageSessionEnd(reason: String) {
                binding.tvStatusDelete.append("\n- onMessageSessionEnd: $reason\n")

                //Check if provisioning was successful
                if (reason == "Success") {
                    binding.btDone.visibility = View.VISIBLE
                }
                binding.loadingProgress.visibility = View.GONE
            }

            override fun sendMessageRequestEnd(reason: String) {
                binding.tvStatusDelete.append("\n- sendMessageRequestEnd: $reason\n")
                binding.loadingProgress.visibility = View.GONE
            }

            override fun onError(error: String) {
                binding.tvStatusDelete.append("\n- onError: $error\n")
                binding.loadingProgress.visibility = View.GONE
            }

            override fun onMessageProveOwnership(challenge: ByteArray) {
                binding.tvStatusDelete.append(
                    "\n- onMessageProveOwnership: ${FormatUtil.encodeToString(challenge)}\n"
                )
                val proveOwnership = credential.proveOwnership(challenge)

                deleteFlow.sendMessageProveOwnership(proveOwnership)
            }

            override fun onMessageReadyForDeletion(challenge: ByteArray) {
                binding.tvStatusDelete.append(
                    "\n- onMessageReadyForDeletion ${FormatUtil.encodeToString(challenge)}\n"
                )
                val proofOfDeletion = documentManager.deleteCredential(document, credential)

                // For now send an empty byte array if poofOfDeletion is null
                deleteFlow.sendMessageDeleted(proofOfDeletion)
            }
        })

        log("Delete document: ${document.userVisibleName}")
        binding.tvStatusDelete.append("\n- Delete document: ${document.userVisibleName}")
        val credentialCertificateChain = credential.credentialKeyCertificateChain
        // returns the Cose_Sign1 Obj with the MSO in the payload
        credentialCertificateChain.first()?.publicKey?.let { publicKey ->
            val cborCoseKey = FormatUtil.cborBuildCoseKey(publicKey)
            deleteFlow.sendMessageDelete(FormatUtil.cborEncode(cborCoseKey))
        }
    }

    private fun showError(message: String) {
        binding.tvStatusDelete.append(message)
        binding.btDone.visibility = View.VISIBLE
        binding.loadingProgress.visibility = View.GONE
    }
}