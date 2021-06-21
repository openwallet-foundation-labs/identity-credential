package com.android.mdl.app.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.android.mdl.app.databinding.FragmentUserConsentBinding
import com.android.mdl.app.fragment.UserConsentFragmentDirections.Companion.actionUserConsentFragmentToSelectDocumentFragment
import com.android.mdl.app.util.TransferStatus
import com.android.mdl.app.viewmodel.UserConsentViewModel


class UserConsentFragment : Fragment() {

    companion object {
        private const val LOG_TAG = "UserConsentFragment"
    }

    private val args: ShareDocumentFragmentArgs by navArgs()
    private lateinit var docType: String
    private lateinit var identityCredentialName: String
    private lateinit var userVisibleName: String
    private var hardwareBacked = false

    private var _binding: FragmentUserConsentBinding? = null
    private lateinit var vm: UserConsentViewModel

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        docType = args.docType
        identityCredentialName = args.identityCredentialName
        hardwareBacked = args.hardwareBacked
        userVisibleName = args.userVisibleName

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserConsentBinding.inflate(inflater)
        vm = ViewModelProvider(this).get(UserConsentViewModel::class.java)

        binding.fragment = this

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm.getTransferStatus().observe(viewLifecycleOwner, {
            when (it) {
                TransferStatus.ENGAGEMENT_READY -> {
                    Log.d(LOG_TAG, "Engagement Ready")
                }
                TransferStatus.CONNECTED -> {
                    Log.d(LOG_TAG, "Connected")
                }
                TransferStatus.REQUEST -> {
                    Log.d(LOG_TAG, "Request")
                }
                TransferStatus.DISCONNECTED -> {
                    Toast.makeText(
                        requireContext(), "Device disconnected",
                        Toast.LENGTH_SHORT
                    ).show()
                    findNavController().navigate(
                        actionUserConsentFragmentToSelectDocumentFragment()
                    )
                }
                TransferStatus.ERROR -> {
                    Toast.makeText(
                        requireContext(), "An error occurred.",
                        Toast.LENGTH_SHORT
                    ).show()
                    findNavController().navigate(
                        actionUserConsentFragmentToSelectDocumentFragment()
                    )
                }
            }
        })
    }

    fun onApprove() {
        findNavController().navigate(
            UserConsentFragmentDirections.actionUserConsentFragmentToTransferDocumentFragment(
                docType, identityCredentialName, userVisibleName, hardwareBacked
            )
        )
    }

    // This callback will only be called when MyFragment is at least Started.
    var callback = object : OnBackPressedCallback(true /* enabled by default */) {
        override fun handleOnBackPressed() {
            onCancel()
        }
    }

    fun onCancel() {
        vm.cancelPresentation()
        findNavController().navigate(
            actionUserConsentFragmentToSelectDocumentFragment()
        )
    }
}