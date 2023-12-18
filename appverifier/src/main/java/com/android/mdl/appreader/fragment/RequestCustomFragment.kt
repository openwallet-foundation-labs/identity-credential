package com.android.mdl.appreader.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.LayoutDirection.RTL
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.android.mdl.appreader.R
import com.android.mdl.appreader.VerifierApp
import com.android.mdl.appreader.databinding.FragmentRequestCustomBinding
import com.android.mdl.appreader.document.RequestDocument
import com.android.mdl.appreader.document.RequestDocumentList
import com.android.mdl.appreader.viewModel.RequestCustomViewModel

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class RequestCustomFragment : Fragment() {

    private val args: RequestCustomFragmentArgs by navArgs()
    private var _binding: FragmentRequestCustomBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var vm: RequestCustomViewModel
    private lateinit var requestDocument: RequestDocument
    private lateinit var requestDocumentList: RequestDocumentList

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestDocument = args.requestDocument
        requestDocumentList = args.requestDocumentList
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentRequestCustomBinding.inflate(inflater, container, false)
        vm = ViewModelProvider(this).get(RequestCustomViewModel::class.java)

        vm.init(requestDocument)

        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Dynamically creates the list of checkbox
        requestDocument.itemsToRequest.forEach { ns ->
            ns.value.keys.forEach { el ->
                binding.llRequestItems.addView(
                    newCheckBox(
                        ns.key,
                        el,
                        VerifierApp.credentialTypeRepositoryInstance.getMdocCredentialType(
                            requestDocument.docType
                        )?.namespaces?.get(ns.key)?.dataElements?.get(el)?.attribute?.displayName
                            ?: el,
                        vm.isSelectedDataItem(ns.key, el)
                    )
                )
            }
        }

        binding.btNext.setOnClickListener {
            // TODO: get intent to retain from user
            //requestDocument.setSelectedDataItems(vm.getSelectedDataItems(false))
            requestDocumentList.getAll()
                .find { it.docType == requestDocument.docType }
                .also {
                    it?.itemsToRequest = (vm.getSelectedDataItems(false))
                }
            findNavController().navigate(
                RequestCustomFragmentDirections.actionRequestCustomToScanDeviceEngagement(
                    requestDocumentList
                )
            )
        }
        binding.btCancel.setOnClickListener {
            findNavController().navigate(R.id.action_RequestCustom_to_RequestOptions)
        }
    }

    @SuppressLint("WrongConstant")
    private fun newCheckBox(
        namespace: String,
        identifier: String,
        text: String,
        isSelected: Boolean
    ): CheckBox {
        val checkBox = CheckBox(requireContext())
        checkBox.let {
            it.text = text
            it.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            it.isChecked = isSelected
            it.setOnClickListener {
                vm.dataItemSelected(namespace, identifier)
            }
            it.layoutDirection = RTL
        }
        return checkBox
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
