package com.android.mdl.appreader.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.mdl.appreader.R
import com.android.mdl.appreader.databinding.FragmentRequestCustomBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class RequestCustomFragment : Fragment() {

    private var _binding: FragmentRequestCustomBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentRequestCustomBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btNext.setOnClickListener {
            findNavController().navigate(R.id.action_RequestCustom_to_ScanDeviceEngagement)
        }
        binding.btCancel.setOnClickListener {
            findNavController().navigate(R.id.action_RequestCustom_to_RequestOptions)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}