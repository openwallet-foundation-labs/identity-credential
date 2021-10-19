package com.android.mdl.appreader.fragment

import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.security.identity.DeviceResponseParser
import com.android.mdl.appreader.R
import com.android.mdl.appreader.databinding.FragmentShowDocumentBinding
import com.android.mdl.appreader.transfer.TransferManager
import com.android.mdl.appreader.util.FormatUtil
import com.android.mdl.appreader.util.TransferStatus


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class ShowDocumentFragment : Fragment() {

    companion object {
        private const val LOG_TAG = "ShowDocumentFragment"
        private const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        private const val MICOV_DOCTYPE = "micov.1"
        private const val MDL_NAMESPACE = "org.iso.18013.5.1"
        private const val MICOV_ATT_NAMESPACE = "org.micov.attestation.1"
    }

    private var _binding: FragmentShowDocumentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private var portraitBytes: ByteArray? = null
    private lateinit var transferManager: TransferManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentShowDocumentBinding.inflate(inflater, container, false)
        transferManager = TransferManager.getInstance(requireContext())
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val documents = transferManager.getDeviceResponse().documents
        binding.tvResults.text =
            Html.fromHtml(formatTextResult(documents), Html.FROM_HTML_MODE_COMPACT)

        portraitBytes?.let { pb ->
            Log.d(LOG_TAG, "Showing portrait " + pb.size + " bytes")
            binding.ivPortrait.setImageBitmap(
                BitmapFactory.decodeByteArray(portraitBytes, 0, pb.size)
            )
            binding.ivPortrait.visibility = View.VISIBLE
        }


        binding.btOk.setOnClickListener {
            findNavController().navigate(R.id.action_ShowDocument_to_RequestOptions)
        }
        binding.btCloseConnection.setOnClickListener {
            transferManager.stopVerification(
                sendSessionTerminationMessage = false,
                useTransportSpecificSessionTermination = false
            )
            hideButtons()
        }
        binding.btCloseTransportSpecific.setOnClickListener {
            transferManager.stopVerification(
                sendSessionTerminationMessage = true,
                useTransportSpecificSessionTermination = true
            )
            hideButtons()
        }
        binding.btCloseTerminationMessage.setOnClickListener {
            transferManager.stopVerification(
                sendSessionTerminationMessage = true,
                useTransportSpecificSessionTermination = false
            )
            hideButtons()
        }
        binding.btNewRequest.setOnClickListener {
            findNavController().navigate(
                ShowDocumentFragmentDirections.actionShowDocumentToRequestOptions(true)
            )
        }
        transferManager.getTransferStatus().observe(viewLifecycleOwner, {
            when (it) {
                TransferStatus.ENGAGED -> {
                    Log.d(LOG_TAG, "Device engagement received.")
                }
                TransferStatus.CONNECTED -> {
                    Log.d(LOG_TAG, "Device connected received.")
                }
                TransferStatus.RESPONSE -> {
                    Log.d(LOG_TAG, "Device response received.")
                }
                TransferStatus.DISCONNECTED -> {
                    Log.d(LOG_TAG, "Device disconnected received.")
                    transferManager.stopVerification(
                        sendSessionTerminationMessage = false,
                        useTransportSpecificSessionTermination = false
                    )
                    hideButtons()
                }
                TransferStatus.ERROR -> {
                    Toast.makeText(
                        requireContext(), "Error with the connection.",
                        Toast.LENGTH_SHORT
                    ).show()
                    transferManager.stopVerification(
                        sendSessionTerminationMessage = false,
                        useTransportSpecificSessionTermination = false
                    )
                    hideButtons()
                }
            }
        })
    }

    private fun hideButtons() {
        binding.btOk.visibility = View.VISIBLE
        binding.btCloseConnection.visibility = View.GONE
        binding.btCloseTransportSpecific.visibility = View.GONE
        binding.btCloseTerminationMessage.visibility = View.GONE
        binding.btNewRequest.visibility = View.GONE
    }

    private fun formatTextResult(documents: Collection<DeviceResponseParser.Document>): String {
        val sb = StringBuffer()
        sb.append("Number of documents returned: <b>${documents.size}</b>")
        sb.append("<br><br>")
        for (doc in documents) {
            sb.append("<h3>Doctype: <font color=blue>${doc.docType}</font></h3>")
            sb.append("Issuer Signed Authenticated: ${getFormattedCheck(doc.issuerSignedAuthenticated)}<br>")
            sb.append("Device Signed Authenticated: ${getFormattedCheck(doc.deviceSignedAuthenticated)}<br>")
            for (ns in doc.issuerNamespaces) {
                sb.append("<br>")
                sb.append("<h5>Namespace: $ns</h5>")
                sb.append("<p>")
                for (elem in doc.getIssuerEntryNames(ns)) {
                    val value: ByteArray = doc.getIssuerEntryData(ns, elem)
                    var valueStr: String
                    if (doc.docType == MDL_DOCTYPE && ns == MDL_NAMESPACE && elem == "portrait") {
                        valueStr = String.format("(%d bytes, shown above)", value.size)
                        portraitBytes = doc.getIssuerEntryByteString(ns, elem)
                    } else if (doc.docType == MICOV_DOCTYPE && ns == MICOV_ATT_NAMESPACE && elem == "fac") {
                        valueStr = String.format("(%d bytes, shown above)", value.size)
                        portraitBytes = doc.getIssuerEntryByteString(ns, elem)
                    } else if (doc.docType == MDL_DOCTYPE
                        && ns == MDL_NAMESPACE && elem == "extra"
                    ) {
                        valueStr = String.format("%d bytes extra data", value.size)
                    } else {
                        valueStr = FormatUtil.cborPrettyPrint(value)
                    }
                    sb.append(
                        "<b>$elem</b> ${
                            getFormattedCheck(
                                doc.getIssuerEntryDigestMatch(
                                    ns,
                                    elem
                                )
                            )
                        } -> $valueStr<br>"
                    )
                }
                sb.append("</p><br>")
            }
        }
        return sb.toString()
    }

    private fun getFormattedCheck(authenticated: Boolean) = if (authenticated) {
        "<font color=green>&#10003;</font>"
    } else {
        "<font color=red>&#x26A0;</font>"
    }

    private var callback = object : OnBackPressedCallback(true /* enabled by default */) {
        override fun handleOnBackPressed() {
            TransferManager.getInstance(requireContext()).stopVerification(
                sendSessionTerminationMessage = true,
                useTransportSpecificSessionTermination = true
            )
            findNavController().navigate(R.id.action_ShowDocument_to_RequestOptions)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}