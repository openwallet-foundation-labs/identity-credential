package com.android.mdl.appreader.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.mdl.appreader.R
import com.android.mdl.appreader.databinding.FragmentRequestOptionsBinding
import com.android.mdl.appreader.transfer.TransferManager

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class RequestOptionsFragment : Fragment() {

    private var _binding: FragmentRequestOptionsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

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
        }
        binding.cbRequestMdlOlder21.setOnClickListener {
            binding.cbRequestMdlOlder18.isChecked = false
            binding.cbRequestMdlOlder21.isChecked = true
            binding.cbRequestMdlFull.isChecked = false
            binding.cbRequestMdlCustom.isChecked = false
        }
        binding.cbRequestMdlFull.setOnClickListener {
            binding.cbRequestMdlOlder18.isChecked = false
            binding.cbRequestMdlOlder21.isChecked = false
            binding.cbRequestMdlFull.isChecked = true
            binding.cbRequestMdlCustom.isChecked = false
        }
        binding.cbRequestMdlCustom.setOnClickListener {
            binding.cbRequestMdlOlder18.isChecked = false
            binding.cbRequestMdlOlder21.isChecked = false
            binding.cbRequestMdlFull.isChecked = false
            binding.cbRequestMdlCustom.isChecked = true
        }

        binding.btNext.setOnClickListener {
            if (binding.cbRequestMdlCustom.isChecked) {
                findNavController().navigate(R.id.action_RequestOptions_to_RequestCustom)
            } else {
                findNavController().navigate(R.id.action_RequestOptions_to_ScanDeviceEngagement)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}