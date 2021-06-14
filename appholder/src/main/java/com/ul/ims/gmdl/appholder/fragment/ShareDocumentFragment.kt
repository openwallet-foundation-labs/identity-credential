package com.ul.ims.gmdl.appholder.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.ul.ims.gmdl.appholder.databinding.FragmentShareDocumentBinding
import com.ul.ims.gmdl.appholder.fragment.ShareDocumentFragmentDirections.Companion.actionShareDocumentFragmentToSelectDocumentFragment
import com.ul.ims.gmdl.appholder.fragment.ShareDocumentFragmentDirections.Companion.actionShareDocumentFragmentToUserConsentFragment
import com.ul.ims.gmdl.appholder.util.TransferStatus
import com.ul.ims.gmdl.appholder.viewmodel.ShareDocumentViewModel


class ShareDocumentFragment : Fragment() {
    companion object {
        private const val LOG_TAG = "ShareDocumentFragment"
    }

    private val args: ShareDocumentFragmentArgs by navArgs()
    private lateinit var docType: String
    private lateinit var identityCredentialName: String
    private lateinit var userVisibleName: String
    private var hardwareBacked = false

    private var _binding: FragmentShareDocumentBinding? = null
    private lateinit var vm: ShareDocumentViewModel

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
        _binding = FragmentShareDocumentBinding.inflate(inflater)
        vm = ViewModelProvider(this).get(ShareDocumentViewModel::class.java)

        binding.vm = vm
        binding.fragment = this

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm.startPresentation()

        vm.getTransferStatus().observe(viewLifecycleOwner, {
            when (it) {
                TransferStatus.ENGAGEMENT_READY -> {
                    vm.message.set("Engagement Ready!")
                    vm.setDeviceEngagement()
                }
                TransferStatus.CONNECTED -> {
                    vm.message.set("Connected!")
                }
                TransferStatus.REQUEST -> {
                    vm.message.set("Request received!")
                    findNavController().navigate(
                        actionShareDocumentFragmentToUserConsentFragment(
                            docType, identityCredentialName, userVisibleName, hardwareBacked
                        )
                    )
                }
                TransferStatus.DISCONNECTED -> {
                    vm.message.set("Disconnected!")
                    findNavController().navigate(
                        actionShareDocumentFragmentToSelectDocumentFragment()
                    )
                }
                TransferStatus.ERROR -> {
                    vm.message.set("Error on presentation!")
                }
                TransferStatus.ENGAGEMENT_DETECTED -> {
                    vm.message.set("Engagement detected!")
                }
                TransferStatus.CONNECTING -> {
                    vm.message.set("Connecting...")
                }
            }
        }
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
            actionShareDocumentFragmentToSelectDocumentFragment()
        )
    }
}