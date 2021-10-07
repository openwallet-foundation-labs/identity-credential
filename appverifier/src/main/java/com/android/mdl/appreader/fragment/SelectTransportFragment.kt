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

        transferManager.availableTransferMethods?.forEach {
            val transferMethod = FormatUtil.cborDecode(it)
            if (transferMethod !is Array) {
                Log.d(LOG_TAG, "entry in DeviceRetrievalMethods array is not an array")
                return@forEach
            }
            val items: List<DataItem> = transferMethod.dataItems
            if (items.size < 3) {
                Log.d(LOG_TAG, "DeviceRetrievalMethod - array less than three items")
                return@forEach
            }
            if (!(items[0] is Number && items[1] is Number && items[2] is Map)) {
                Log.d(LOG_TAG, "DeviceRetrievalMethod - wrong types for elements")
                return@forEach
            }
            val type = (items[0] as Number).value.toInt()
            val version = (items[1] as Number).value.toInt()
            val options = items[2] as Map

            // TODO: make the RadioButton unselectable if we don't support the transport type

            // TODO: use prettier buttonText for support transports, e.g. "TCP 192.168.1.42:1234"
            //   and similar for BLE, Wifi Aware, NFC, etc.
            val optionsText = FormatUtil.cborPrettyPrint(options)
            val dataRetrievalName = when (type) {
                1 -> "<h3>NFC Data Retrieval</h3>"
                2 -> "<h3>BLE Data Retrieval</h3>"
                3 -> "<h3>Wifi Aware Data Retrieval</h3>"
                else -> "<h3>Type: $type Data Retrieval</h3>"
            }
            val buttonText =
                "$dataRetrievalName<p>type <b>$type</b> ver <b>$version</b> ($optionsText)</p>"

            val button = RadioButton(requireContext())
            button.text = Html.fromHtml(buttonText, Html.FROM_HTML_MODE_COMPACT)
            button.id = View.generateViewId()
            val encodedDeviceRetrievalMethod = it
            button.isChecked = it.contentEquals(transferManager.deviceRetrievalMethod)
            button.setOnClickListener {
                Log.d(LOG_TAG, "optionsText '$buttonText' was selected")
                transferManager.setDeviceRetrievalMethod(encodedDeviceRetrievalMethod)
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