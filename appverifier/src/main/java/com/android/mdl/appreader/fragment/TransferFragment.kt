package com.android.mdl.appreader.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.android.mdl.appreader.R
import com.android.mdl.appreader.databinding.FragmentTransferBinding
import com.android.mdl.appreader.document.RequestDocumentList
import com.android.mdl.appreader.util.TransferStatus
import com.android.mdl.appreader.util.logDebug
import com.android.mdl.appreader.util.logError
import com.android.mdl.appreader.viewModel.TransferViewModel

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class TransferFragment : Fragment() {
    private val args: TransferFragmentArgs by navArgs()
    private var _binding: FragmentTransferBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private val vm: TransferViewModel by viewModels()
    private var keepConnection = false

    private lateinit var requestDocumentList: RequestDocumentList

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestDocumentList = args.requestDocumentList
        keepConnection = args.keepConnection
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTransferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        try {
            if (keepConnection) {
                vm.sendNewRequest(requestDocumentList)
                binding.tvStatus.text = "New request sent..."
            } else {
                if (vm.isUsingReverseEngagement()) {
                    logDebug("Using reverse engagement")
                } else {
                    binding.tvStatus.text = "Trying to connect to mDoc app..."
                    vm.connect()
                }
            }
        } catch (e: RuntimeException) {
            logError("Error starting connection: ${e.message}", e)
            Toast.makeText(
                requireContext(),
                "Error starting connection: ${e.message}",
                Toast.LENGTH_SHORT,
            ).show()
        }

        vm.getTransferStatus().observe(viewLifecycleOwner) {
            when (it) {
                TransferStatus.ENGAGED -> {
                    binding.tvStatus.text = "Device engagement received..."
                }
                TransferStatus.MOVE_INTO_NFC_FIELD -> {
                    binding.tvStatus.text = "Move reader into NFC field..."
                }
                TransferStatus.CONNECTED -> {
                    binding.tvStatus.text = "Connected. Requesting mDoc..."
                    vm.sendRequest(requestDocumentList)
                }
                TransferStatus.RESPONSE -> {
                    logDebug("Navigating to results")
                    findNavController().navigate(R.id.action_Transfer_to_ShowDocument)
                }
                TransferStatus.DISCONNECTED -> {
                    binding.tvStatus.text = "Disconnected"
                }
                TransferStatus.ERROR -> {
                    // TODO: Pass and show the actual text of the exception here.
                    Toast.makeText(
                        requireContext(),
                        "Error connecting to holder",
                        Toast.LENGTH_SHORT,
                    ).show()
                    findNavController().navigate(R.id.action_Transfer_to_RequestOptions)
                }
                else -> {}
            }
        }

        binding.btCancel.setOnClickListener {
            findNavController().navigate(R.id.action_Transfer_to_RequestOptions)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
