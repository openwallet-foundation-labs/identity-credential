package com.android.mdl.appreader.fragment

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.mdl.appreader.R
import com.android.mdl.appreader.databinding.FragmentShowDocumentBinding
import com.android.mdl.appreader.transfer.TransferManager
import com.android.mdl.appreader.util.FormatUtil


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class ShowDocumentFragment : Fragment() {

    companion object {
        private const val LOG_TAG = "ShowDocumentFragment"
        private const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        private const val MDL_NAMESPACE = "org.iso.18013.5.1"
    }

    private var _binding: FragmentShowDocumentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentShowDocumentBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val transferManager = TransferManager.getInstance(requireContext())

        var portraitBytes: ByteArray? = null

        val documents = transferManager.response?.documents
        binding.tvResults.text = ""
        binding.tvResults.append(
            """
                Number of documents returned: ${documents?.size ?: 0}
                
                """.trimIndent()
        )
        binding.tvResults.append("\n")
        if (documents != null) {
            for (doc in documents) {
                binding.tvResults.append(
                    """
                Doctype: ${doc.docType}
                
                """.trimIndent()
                )
                for (ns in doc.issuerNamespaces) {
                    binding.tvResults.append("  Namespace: $ns\n")
                    for (elem in doc.getIssuerEntryNames(ns)) {
                        val value: ByteArray = doc.getIssuerEntryData(ns, elem)
                        var valueStr: String
                        if (doc.docType == MDL_DOCTYPE && ns == MDL_NAMESPACE && elem == "portrait"
                        ) {
                            valueStr = String.format("(%d bytes, shown above)", value.size)
                            portraitBytes = doc.getIssuerEntryByteString(ns, elem)
                        } else if (doc.docType == MDL_DOCTYPE
                            && ns == MDL_NAMESPACE && elem == "extra"
                        ) {
                            valueStr = String.format("%d bytes extra data", value.size)
                        } else {
                            valueStr = FormatUtil.cborPrettyPrint(value)
                        }
                        binding.tvResults.append("    $elem -> $valueStr\n")
                    }
                    binding.tvResults.append("\n")
                }
            }
        }

        if (portraitBytes != null) {
            Log.d(LOG_TAG, "Showing portrait " + portraitBytes.size + " bytes")
            binding.ivPortrait.setImageBitmap(
                BitmapFactory.decodeByteArray(portraitBytes, 0, portraitBytes.size)
            )
        }

        TransferManager.getInstance(requireContext()).stopVerification()

        binding.btOk.setOnClickListener {
            findNavController().navigate(R.id.action_ShowDocument_to_RequestOptions)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}