package com.android.mdl.appreader.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.android.mdl.appreader.databinding.FragmentRequestOptionsBinding
import com.android.mdl.appreader.document.*
import com.android.mdl.appreader.transfer.TransferManager

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class RequestOptionsFragment : Fragment() {

    private val args: RequestOptionsFragmentArgs by navArgs()
    private var _binding: FragmentRequestOptionsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var keepConnection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keepConnection = args.keepConnection
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRequestOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!keepConnection) {
            // Always call to cancel any connection that could be on progress
            TransferManager.getInstance(requireContext()).stopVerification()
        }

        binding.cbRequestMdl.setOnClickListener {
            binding.cbRequestMdlOlder18.isEnabled = binding.cbRequestMdl.isChecked
            binding.cbRequestMdlOlder21.isEnabled = binding.cbRequestMdl.isChecked
            binding.cbRequestMdlMandatory.isEnabled = binding.cbRequestMdl.isChecked
            binding.cbRequestMdlFull.isEnabled = binding.cbRequestMdl.isChecked
            binding.cbRequestMdlCustom.isEnabled = binding.cbRequestMdl.isChecked
        }

        binding.cbRequestMdlOlder18.setOnClickListener {
            binding.cbRequestMdlOlder18.isChecked = true
            binding.cbRequestMdlOlder21.isChecked = false
            binding.cbRequestMdlMandatory.isChecked = false
            binding.cbRequestMdlFull.isChecked = false
            binding.cbRequestMdlCustom.isChecked = false
        }
        binding.cbRequestMdlOlder21.setOnClickListener {
            binding.cbRequestMdlOlder18.isChecked = false
            binding.cbRequestMdlOlder21.isChecked = true
            binding.cbRequestMdlMandatory.isChecked = false
            binding.cbRequestMdlFull.isChecked = false
            binding.cbRequestMdlCustom.isChecked = false
        }
        binding.cbRequestMdlMandatory.setOnClickListener {
            binding.cbRequestMdlOlder18.isChecked = false
            binding.cbRequestMdlOlder21.isChecked = false
            binding.cbRequestMdlMandatory.isChecked = true
            binding.cbRequestMdlFull.isChecked = false
            binding.cbRequestMdlCustom.isChecked = false
        }
        binding.cbRequestMdlFull.setOnClickListener {
            binding.cbRequestMdlOlder18.isChecked = false
            binding.cbRequestMdlOlder21.isChecked = false
            binding.cbRequestMdlMandatory.isChecked = false
            binding.cbRequestMdlFull.isChecked = true
            binding.cbRequestMdlCustom.isChecked = false

        }
        binding.cbRequestMdlCustom.setOnClickListener {
            binding.cbRequestMdlOlder18.isChecked = false
            binding.cbRequestMdlOlder21.isChecked = false
            binding.cbRequestMdlMandatory.isChecked = false
            binding.cbRequestMdlFull.isChecked = false
            binding.cbRequestMdlCustom.isChecked = true
        }

        // TODO: get intent to retain from user
        val intentToRetain = false
        val requestDocumentList = RequestDocumentList()

        binding.btNext.setOnClickListener {

            if (binding.cbRequestMdl.isChecked) {
                val mdl = RequestMdl
                when {
                    binding.cbRequestMdlOlder18.isChecked ->
                        mdl.setSelectedDataItems(getSelectRequestMdlOlder18(intentToRetain))
                    binding.cbRequestMdlOlder21.isChecked ->
                        mdl.setSelectedDataItems(getSelectRequestMdlOlder21(intentToRetain))
                    binding.cbRequestMdlMandatory.isChecked ->
                        mdl.setSelectedDataItems(getSelectRequestMdlMandatory(intentToRetain))
                    binding.cbRequestMdlFull.isChecked ->
                        mdl.setSelectedDataItems(getSelectRequestFull(mdl, intentToRetain))
                }
                requestDocumentList.addRequestDocument(mdl)
            }
            if (binding.cbRequestMvr.isChecked) {
                val doc = RequestMvr
                doc.setSelectedDataItems(getSelectRequestFull(doc, intentToRetain))
                requestDocumentList.addRequestDocument(doc)
            }
            if (binding.cbRequestMicov.isChecked) {
                val doc = RequestMicovAtt
                doc.setSelectedDataItems(getSelectRequestFull(doc, intentToRetain))
                requestDocumentList.addRequestDocument(doc)
                val doc2 = RequestMicovVtr
                doc2.setSelectedDataItems(getSelectRequestFull(doc2, intentToRetain))
                requestDocumentList.addRequestDocument(doc2)
            }

            if (binding.cbRequestMdl.isChecked && binding.cbRequestMdlCustom.isChecked) {
                findNavController().navigate(
                    RequestOptionsFragmentDirections.actionRequestOptionsToRequestCustom(
                        RequestMdl,
                        requestDocumentList,
                        keepConnection
                    )
                )
            } else {
                //Navigate direct to transfer screnn
                if (keepConnection) {
                    findNavController().navigate(
                        RequestOptionsFragmentDirections.actionRequestOptionsToTransfer(
                            requestDocumentList,
                            true
                        )
                    )
                } else {
                    findNavController().navigate(
                        RequestOptionsFragmentDirections.actionRequestOptionsToScanDeviceEngagement(
                            requestDocumentList
                        )
                    )
                }
            }
        }
    }

    private fun getSelectRequestMdlMandatory(intentToRetain: Boolean): Map<String, Boolean> {
        val map = mutableMapOf<String, Boolean>()
        map[RequestMdl.DataItems.FAMILY_NAME.identifier] = intentToRetain
        map[RequestMdl.DataItems.GIVEN_NAMES.identifier] = intentToRetain
        map[RequestMdl.DataItems.BIRTH_DATE.identifier] = intentToRetain
        map[RequestMdl.DataItems.ISSUE_DATE.identifier] = intentToRetain
        map[RequestMdl.DataItems.EXPIRY_DATE.identifier] = intentToRetain
        map[RequestMdl.DataItems.ISSUING_COUNTRY.identifier] = intentToRetain
        map[RequestMdl.DataItems.ISSUING_AUTHORITY.identifier] = intentToRetain
        map[RequestMdl.DataItems.DOCUMENT_NUMBER.identifier] = intentToRetain
        map[RequestMdl.DataItems.PORTRAIT.identifier] = intentToRetain
        map[RequestMdl.DataItems.DRIVING_PRIVILEGES.identifier] = intentToRetain
        map[RequestMdl.DataItems.UN_DISTINGUISHING_SIGN.identifier] = intentToRetain
        return map
    }

    private fun getSelectRequestMdlOlder21(intentToRetain: Boolean): Map<String, Boolean> {
        val map = mutableMapOf<String, Boolean>()
        map[RequestMdl.DataItems.PORTRAIT.identifier] = intentToRetain
        map[RequestMdl.DataItems.AGE_OVER_21.identifier] = intentToRetain
        return map
    }

    private fun getSelectRequestMdlOlder18(intentToRetain: Boolean): Map<String, Boolean> {
        val map = mutableMapOf<String, Boolean>()
        map[RequestMdl.DataItems.PORTRAIT.identifier] = intentToRetain
        map[RequestMdl.DataItems.AGE_OVER_18.identifier] = intentToRetain
        return map
    }

    private fun getSelectRequestFull(
        requestDocument: RequestDocument,
        intentToRetain: Boolean
    ): Map<String, Boolean> {
        val map = mutableMapOf<String, Boolean>()
        requestDocument.dataItems.forEach {
            map[it.identifier] = intentToRetain
        }
        return map
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}