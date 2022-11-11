package com.android.mdl.app.fragment

import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.identity.IdentityCredentialStore.IMPLEMENTATION_TYPE_HARDWARE
import com.android.identity.IdentityCredentialStore.IMPLEMENTATION_TYPE_KEYSTORE
import com.android.mdl.app.R
import com.android.mdl.app.adapter.ColorAdapter
import com.android.mdl.app.databinding.FragmentAddSelfSignedBinding
import com.android.mdl.app.util.DocumentData.MDL_DOCTYPE
import com.android.mdl.app.util.DocumentData.MICOV_DOCTYPE
import com.android.mdl.app.util.DocumentData.MVR_DOCTYPE
import com.android.mdl.app.util.ProvisionInfo

class AddSelfSignedFragment : Fragment() {

    private var _binding: FragmentAddSelfSignedBinding? = null
    private val binding get() = _binding!!
    private lateinit var colorAdapter: ColorAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddSelfSignedBinding.inflate(inflater)
        binding.fragment = this
        colorAdapter = ColorAdapter(requireContext())
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
        binding.spDocumentColor.adapter = colorAdapter
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.storage_implementation,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            binding.spStorageImplementation.adapter = adapter
        }
        binding.spDocumentType.onItemSelectedListener = object :
            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                setDocumentNameByType(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {

            }

        }
    }

    private fun setDocumentNameByType(position: Int) {
        val value = when (position) {
            0 -> {
                "Driving License"
            }

            1 -> {
                "Vehicle Registration"
            }

            2 -> {
                "Vaccination Document"
            }

            else -> {
                "New Document"
            }
        }
        binding.edDocumentName.text = Editable.Factory.getInstance().newEditable(value)
    }

    fun onNext() {
        if (binding.edDocumentName.text.isBlank()) {
            setDocumentNameByType(-1)
        }
        val provisionInfo = ProvisionInfo(
            getSelectedDocType(),
            binding.edDocumentName.text.toString(),
            binding.spDocumentColor.selectedItemPosition,
            getStorageImplementation(),
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

    private fun getStorageImplementation(): String {
        // 0 Keystore-backed
        // 1 Hardware-backed
        return when (binding.spStorageImplementation.selectedItem) {
            resources.getStringArray(R.array.storage_implementation)[0] -> {
                IMPLEMENTATION_TYPE_KEYSTORE
            }

            resources.getStringArray(R.array.storage_implementation)[1] -> {
                IMPLEMENTATION_TYPE_HARDWARE
            }

            else -> {
                IMPLEMENTATION_TYPE_KEYSTORE
            }
        }
    }
}