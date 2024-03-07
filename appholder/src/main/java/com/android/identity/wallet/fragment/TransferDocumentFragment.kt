package com.android.identity.wallet.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.android.identity.crypto.javaX509Certificate
import com.android.identity.wallet.HolderApp
import com.android.identity.wallet.R
import com.android.identity.wallet.databinding.FragmentTransferDocumentBinding
import com.android.identity.wallet.document.DocumentInformation
import com.android.identity.wallet.transfer.TransferManager
import com.android.identity.wallet.trustmanagement.CustomValidators
import com.android.identity.wallet.trustmanagement.getCommonName
import com.android.identity.wallet.util.PreferencesHelper
import com.android.identity.wallet.util.TransferStatus
import com.android.identity.wallet.util.log
import com.android.identity.wallet.viewmodel.TransferDocumentViewModel
import java.security.cert.X509Certificate

class TransferDocumentFragment : Fragment() {
    private var _binding: FragmentTransferDocumentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransferDocumentViewModel by activityViewModels()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onDone()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransferDocumentBinding.inflate(inflater)
        binding.fragment = this
        binding.vm = viewModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.getTransferStatus().observe(viewLifecycleOwner) { transferStatus ->
            when (transferStatus) {
                TransferStatus.CONNECTED -> log("Connected")
                TransferStatus.REQUEST -> onTransferRequested()
                TransferStatus.REQUEST_SERVED -> onRequestServed()
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
        viewModel.authConfirmationState.observe(viewLifecycleOwner) { cancelled ->
            if (cancelled == true) {
                viewModel.onAuthenticationCancellationConsumed()
                onDone()
                findNavController().navigateUp()
            }
        }
    }

    private fun onRequestServed() {
        log("Request Served")
    }

    private fun onTransferRequested() {
        log("Request")
        var commonName = ""
        var trusted = false
        try {
            val requestedDocuments = viewModel.getRequestedDocuments()
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
                    var certChain: List<X509Certificate> =
                        reqDoc.readerCertificateChain!!.certificates.map { it.javaX509Certificate }
                            .toList()
                    val customValidators = CustomValidators.getByDocType(doc.docType)
                    val result = HolderApp.trustManagerInstance.verify(
                        chain = certChain,
                        customValidators = customValidators
                    )
                    trusted = result.isTrusted
                    if (result.trustChain.any()) {
                        certChain = result.trustChain
                    }
                    commonName = certChain.last().issuerX500Principal.getCommonName("")

                    // Add some information about the reader certificate used
                    if (result.isTrusted) {
                        binding.txtDocuments.append("- Trusted reader auth used: ($commonName)\n")
                    } else {
                        binding.txtDocuments.append("- Not trusted reader auth used: ($commonName)\n")
                        if (result.error != null) {
                            binding.txtDocuments.append("- TrustManager Error: (${result.error})\n")
                        }
                    }
                }
                binding.txtDocuments.append("- ${doc.userVisibleName} (${doc.docType})\n")
            }
            if (viewModel.getSelectedDocuments().isNotEmpty()) {
                viewModel.createSelectedItemsList()
                val direction = TransferDocumentFragmentDirections
                    .navigateToConfirmation(commonName, trusted)
                findNavController().navigate(direction)
            } else {
                // Send response with 0 documents
                viewModel.sendResponseForSelection(
                    onResultReady = {
                    }
                )
            }
            // TODO: this is kind of a hack but we really need to move the sending of the
            //  message to here instead of in the auth confirmation dialog
            if (PreferencesHelper.isConnectionAutoCloseEnabled()) {
                hideButtons()
            }
        } catch (e: Exception) {
            val message = "On request received error: ${e.message}"
            log(message, e)
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            binding.txtConnectionStatus.append("\n$message")
        }
    }

    private fun showDocumentSelection(doc: List<DocumentInformation>) {
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
        log("Disconnected")
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