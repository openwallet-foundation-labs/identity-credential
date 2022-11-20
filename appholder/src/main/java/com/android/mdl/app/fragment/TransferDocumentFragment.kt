package com.android.mdl.app.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.android.mdl.app.R
import com.android.mdl.app.databinding.FragmentTransferDocumentBinding
import com.android.mdl.app.document.Document
import com.android.mdl.app.document.KeysAndCertificates
import com.android.mdl.app.readerauth.SimpleReaderTrustStore
import com.android.mdl.app.transfer.TransferManager
import com.android.mdl.app.util.TransferStatus
import com.android.mdl.app.viewmodel.TransferDocumentViewModel

class TransferDocumentFragment : Fragment() {

    companion object {
        private const val LOG_TAG = "TransferDocumentFragment"
    }

    private var _binding: FragmentTransferDocumentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransferDocumentViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransferDocumentBinding.inflate(inflater)
        binding.fragment = this
        binding.vm = viewModel
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            onBackPressedCallback
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.getTransferStatus().observe(viewLifecycleOwner) { transferStatus ->
            when (transferStatus) {
                TransferStatus.QR_ENGAGEMENT_READY -> Log.d(LOG_TAG, "Engagement Ready")
                TransferStatus.CONNECTED -> Log.d(LOG_TAG, "Connected")
                TransferStatus.REQUEST -> onTransferRequested()
                TransferStatus.REQUEST_SERVED -> Log.d(LOG_TAG, "Request Served")
                TransferStatus.DISCONNECTED -> onTransferDisconnected()
                TransferStatus.ERROR -> onTransferError()
                else -> {}
            }
        }
        viewModel.connectionClosedLiveData.observe(viewLifecycleOwner) {
            onCloseConnection(
                sendSessionTerminationMessage = true,
                useTransportSpecificSessionTermination = false
            )
        }
    }

    private fun onTransferRequested() {
        Log.d(LOG_TAG, "Request")

        try {
            val trustStore = SimpleReaderTrustStore(
                KeysAndCertificates.getTrustedReaderCertificates(requireContext())
            )
            val requestedDocuments = viewModel.getRequestedDocuments()
            var readerCommonName = ""
            var readerIsTrusted = false
            requestedDocuments.forEach { reqDoc ->
                val docs = viewModel.getDocuments().filter { reqDoc.docType == it.docType }
                if (!viewModel.getSelectedDocuments().any { reqDoc.docType == it.docType }) {
                    if (docs.isEmpty()) {
                        binding.txtDocuments.append("- No document found for ${reqDoc.docType}\n")
                        return@forEach
                    } else if (docs.size == 1) {
                        viewModel.getSelectedDocuments().add(docs[0])
                    } else {
                        showDocumentSelection(docs)
                        return
                    }
                }
                val doc = viewModel.getSelectedDocuments().first { reqDoc.docType == it.docType }
                if (reqDoc.readerAuth != null && reqDoc.readerAuthenticated) {
                    val readerChain = reqDoc.readerCertificateChain
                    val trustPath =
                        trustStore.createCertificationTrustPath(readerChain.toList())
                    // Look for the common name in the root certificate if it is trusted or not
                    val certChain = if (trustPath?.isNotEmpty() == true) {
                        trustPath
                    } else {
                        readerChain
                    }

                    certChain.first().subjectX500Principal.name.split(",")
                        .forEach { line ->
                            val (key, value) = line.split("=", limit = 2)
                            if (key == "CN") {
                                readerCommonName = value
                            }
                        }

                    // Add some information about the reader certificate used
                    if (trustStore.validateCertificationTrustPath(trustPath)) {
                        readerIsTrusted = true
                        binding.txtDocuments.append("- Trusted reader auth used: ($readerCommonName)\n")
                    } else {
                        readerIsTrusted = false
                        binding.txtDocuments.append("- Not trusted reader auth used: ($readerCommonName)\n")
                    }
                }
                binding.txtDocuments.append("- ${doc.userVisibleName} (${doc.docType})\n")
            }
            if (viewModel.getSelectedDocuments().isNotEmpty()) {
                viewModel.createSelectedItemsList()
                val direction = TransferDocumentFragmentDirections
                    .navigateToConfirmation(readerCommonName, readerIsTrusted)
                findNavController().navigate(direction)
            } else {
                // Send response with 0 documents
                viewModel.sendResponseForSelection()
            }
        } catch (e: Exception) {
            val message = "On request received error: ${e.message}"
            Log.e(LOG_TAG, message, e)
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            binding.txtConnectionStatus.append("\n$message")
        }
    }

    private fun showDocumentSelection(doc: List<Document>) {
        val alertDialogBuilder = AlertDialog.Builder(requireContext())
        alertDialogBuilder.setTitle("Select which document to share")
        val listItems = doc.map { it.userVisibleName }.toTypedArray()
        alertDialogBuilder.setSingleChoiceItems(listItems, -1) { dialogInterface, i ->
            viewModel.getSelectedDocuments().add(doc[i])
            onTransferRequested()
            dialogInterface.dismiss()
        }
        val mDialog = alertDialogBuilder.create()
        mDialog.show()
    }

    private fun onTransferDisconnected() {
        Log.d(LOG_TAG, "Disconnected")
        hideButtons()
        TransferManager.getInstance(requireContext()).disconnect()
    }

    private fun onTransferError() {
        Toast.makeText(requireContext(), "An error occurred.", Toast.LENGTH_SHORT).show()
        hideButtons()
        TransferManager.getInstance(requireContext()).disconnect()
    }

    private fun hideButtons() {
        binding.txtConnectionStatus.text = getString(R.string.connection_mdoc_closed)
        binding.btCloseConnection.visibility = View.GONE
        binding.btCloseTerminationMessage.visibility = View.GONE
        binding.btCloseTransportSpecific.visibility = View.GONE
        binding.btOk.visibility = View.VISIBLE
    }

    private var onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            onDone()
        }
    }

    fun onCloseConnection(
        sendSessionTerminationMessage: Boolean,
        useTransportSpecificSessionTermination: Boolean
    ) {
        viewModel.cancelPresentation(
            sendSessionTerminationMessage,
            useTransportSpecificSessionTermination
        )
        hideButtons()
    }

    fun onDone() {
        onCloseConnection(
            sendSessionTerminationMessage = true,
            useTransportSpecificSessionTermination = false
        )
        findNavController().navigateUp()
    }
}