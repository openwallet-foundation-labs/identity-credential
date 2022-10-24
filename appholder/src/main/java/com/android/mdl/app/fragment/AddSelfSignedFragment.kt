package com.android.mdl.app.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.mdl.app.R
import com.android.mdl.app.databinding.FragmentAddSelfSignedBinding
import com.android.mdl.app.util.DocumentData.MDL_DOCTYPE
import com.android.mdl.app.util.DocumentData.MICOV_DOCTYPE
import com.android.mdl.app.util.DocumentData.MVR_DOCTYPE
import com.android.mdl.app.util.ProvisionInfo

class AddSelfSignedFragment : Fragment() {


    private var _binding: FragmentAddSelfSignedBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddSelfSignedBinding.inflate(inflater)
        binding.fragment = this

        bindUI()

        return binding.root
    }

    private fun bindUI() {
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.document_type,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            binding.spDocumentType.adapter = adapter
        }
    }

    fun onNext() {
        val provisionInfo = ProvisionInfo(
            getSelectedDocType(),
            binding.scUserAuthentication.isChecked,
            binding.totalMso.text.toString().toInt(),
            binding.totalUse.text.toString().toInt()

        )
        findNavController().navigate(
            AddSelfSignedFragmentDirections.actionAddSelfSignedToSelfSignedDetails(provisionInfo)
        )
    }

    fun onMinusMso() {
        changeValue(binding.totalMso, false)
    }

    fun onPlusMso() {
        changeValue(binding.totalMso, true)
    }

    fun onMinusUse() {
        changeValue(binding.totalUse, false)
    }

    fun onPlusUse() {
        changeValue(binding.totalUse, true)
    }

    private fun changeValue(textView: TextView, positive: Boolean) {
        var num: Int = textView.text.toString().toInt()
        if (positive) {
            num++
            textView.text = num.toString()
        } else {
            num--
            if (num > 0) {
                textView.text = num.toString()
            }
        }
    }

    private fun getSelectedDocType(): String {
        // 0 mDL
        // 1 mVR
        // 2 micov
        return when (binding.spDocumentType.selectedItem) {
            resources.getStringArray(R.array.document_type)[0] -> {
                MDL_DOCTYPE
            }
            resources.getStringArray(R.array.document_type)[1] -> {
                MVR_DOCTYPE
            }
            resources.getStringArray(R.array.document_type)[2] -> {
                MICOV_DOCTYPE
            }
            else -> {
                MDL_DOCTYPE
            }
        }
    }
}