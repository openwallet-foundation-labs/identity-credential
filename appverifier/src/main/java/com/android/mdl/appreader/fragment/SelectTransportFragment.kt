package com.android.mdl.appreader.fragment

import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.Number
import com.android.mdl.appreader.R
import com.android.mdl.appreader.databinding.FragmentSelectTransportBinding
import com.android.mdl.appreader.transfer.TransferManager
import com.android.mdl.appreader.util.FormatUtil


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class SelectTransportFragment : Fragment() {


    companion object {
        private const val LOG_TAG = "SelectTransportFragment"
    }

    private val args: SelectTransportFragmentArgs by navArgs()
    private var _binding: FragmentSelectTransportBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentSelectTransportBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val transferManager = TransferManager.getInstance(requireContext())

        transferManager.availableMdocAddresses?.forEach {
            // For now just use the raw textual representation.
            //
            val buttonText = it.toString()

            val button = RadioButton(requireContext())
            button.text = Html.fromHtml(buttonText, Html.FROM_HTML_MODE_COMPACT)
            button.id = View.generateViewId()
            val encodedDeviceRetrievalMethod = it
            button.isChecked = (it.toString() == transferManager.mdocAddress.toString())
            button.setOnClickListener {
                Log.d(LOG_TAG, "optionsText '$buttonText' was selected")
                transferManager.setMdocAddress(encodedDeviceRetrievalMethod)
            }
            binding.rgTransferMethod.addView(button)
        }

        binding.btNext.setOnClickListener {
            findNavController().navigate(
                SelectTransportFragmentDirections.actionSelectTransportToTransfer(
                    args.requestDocumentList
                )
            )
        }
        binding.btCancel.setOnClickListener {
            findNavController().navigate(R.id.action_SelectTransport_to_RequestOptions)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}