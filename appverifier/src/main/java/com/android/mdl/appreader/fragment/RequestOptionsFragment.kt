package com.android.mdl.appreader.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.mdl.appreader.databinding.FragmentRequestOptionsBinding
import com.android.mdl.appreader.document.RequestMdl
import com.android.mdl.appreader.transfer.TransferManager

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class RequestOptionsFragment : Fragment() {

    private var _binding: FragmentRequestOptionsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    // TODO: get intent to retain from user
    private var mapSelectedDataItems = getSelectRequestMdlFull(false)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRequestOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Always call to cancel any connection that could be on progress
        TransferManager.getInstance(requireContext()).stopVerification()

        binding.cbRequestMdl.setOnClickListener {
            // Now we only have mDL option, it is always true
            binding.cbRequestMdl.isChecked = true
        }

        binding.cbRequestMdlOlder18.setOnClickListener {
            binding.cbRequestMdlOlder18.isChecked = true
            binding.cbRequestMdlOlder21.isChecked = false
            binding.cbRequestMdlFull.isChecked = false
            binding.cbRequestMdlCustom.isChecked = false
            // TODO: get intent to retain from user
            mapSelectedDataItems = getSelectRequestMdlOlder18(false)
        }
        binding.cbRequestMdlOlder21.setOnClickListener {
            binding.cbRequestMdlOlder18.isChecked = false
            binding.cbRequestMdlOlder21.isChecked = true
            binding.cbRequestMdlFull.isChecked = false
            binding.cbRequestMdlCustom.isChecked = false
            // TODO: get intent to retain from user
            mapSelectedDataItems = getSelectRequestMdlOlder21(false)
        }
        binding.cbRequestMdlFull.setOnClickListener {
            binding.cbRequestMdlOlder18.isChecked = false
            binding.cbRequestMdlOlder21.isChecked = false
            binding.cbRequestMdlFull.isChecked = true
            binding.cbRequestMdlCustom.isChecked = false
            // TODO: get intent to retain from user
            mapSelectedDataItems = getSelectRequestMdlFull(false)

        }
        binding.cbRequestMdlCustom.setOnClickListener {
            binding.cbRequestMdlOlder18.isChecked = false
            binding.cbRequestMdlOlder21.isChecked = false
            binding.cbRequestMdlFull.isChecked = false
            binding.cbRequestMdlCustom.isChecked = true
        }

        binding.btNext.setOnClickListener {
            if (binding.cbRequestMdlCustom.isChecked) {
                findNavController().navigate(
                    RequestOptionsFragmentDirections.actionRequestOptionsToRequestCustom(
                        RequestMdl
                    )
                )
            } else {
                val requestDocument = RequestMdl
                requestDocument.setSelectedDataItems(mapSelectedDataItems)
                findNavController().navigate(
                    RequestOptionsFragmentDirections.actionRequestOptionsToScanDeviceEngagement(
                        requestDocument
                    )
                )
            }
        }
    }

    private fun getSelectRequestMdlFull(intentToRetain: Boolean): Map<String, Boolean> {
        val map = mutableMapOf<String, Boolean>()
        RequestMdl.dataItems.forEach {
            map[it.identifier] = intentToRetain
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}