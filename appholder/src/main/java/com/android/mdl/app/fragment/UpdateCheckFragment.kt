package com.android.mdl.app.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.android.identity.PersonalizationData
import com.android.mdl.app.databinding.FragmentUpdateCheckBinding
import com.android.mdl.app.document.Document
import com.android.mdl.app.document.DocumentManager
import com.android.mdl.app.provisioning.UpdateCheckFlow
import com.android.mdl.app.util.FormatUtil
import com.android.mdl.app.util.log
import java.util.*

class UpdateCheckFragment : Fragment() {

    private val args: UpdateCheckFragmentArgs by navArgs()
    private var serverUrl: String? = null
    private lateinit var document: Document

    private var _binding: FragmentUpdateCheckBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        document = args.document
        serverUrl = document.serverUrl
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUpdateCheckBinding.inflate(inflater)
        binding.fragment = this

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startRefreshAuthKeyFlow()
    }

    fun onDone() {
        findNavController().navigate(
            UpdateCheckFragmentDirections.actionUpdateCheckFragmentToDocumentDetailFragment(document)
        )
    }

    private fun startRefreshAuthKeyFlow() {
        val documentManager = DocumentManager.getInstance(requireContext())
        val credential = documentManager.getCredential(document)

        if (credential == null) {
            binding.tvStatusUpdating.append("\n- Error on retrieving a document for ${document.userVisibleName}\n")
            return
        }

        // If server URL is null
        if (serverUrl?.isNotEmpty() != true) {
            binding.tvStatusUpdating.append("\n- Error invalid server information\n")
            return
        }

        // Start refresh auth key flow
        val updateCheckFlow = UpdateCheckFlow.getInstance(requireContext(), serverUrl)

        updateCheckFlow.setListener(object : UpdateCheckFlow.Listener {
            override fun onMessageSessionEnd(reason: String) {
                binding.tvStatusUpdating.append("\n- onMessageSessionEnd: $reason\n")

                //Check if provisioning was successful
                if (reason == "Success") {
                    binding.btDone.visibility = View.VISIBLE
                }
                binding.loadingProgress.visibility = View.GONE
            }

            override fun sendMessageRequestEnd(reason: String) {
                binding.tvStatusUpdating.append("\n- sendMessageRequestEnd: $reason\n")
                binding.loadingProgress.visibility = View.GONE
            }

            override fun onError(error: String) {
                binding.tvStatusUpdating.append("\n- onError: $error\n")
                binding.loadingProgress.visibility = View.GONE
            }

            override fun onMessageProveOwnership(challenge: ByteArray) {
                binding.tvStatusUpdating.append(
                    "\n- onMessageProveOwnership: ${FormatUtil.encodeToString(challenge)}\n"
                )
                val proveOwnership = credential.proveOwnership(challenge)

                updateCheckFlow.sendMessageProveOwnership(proveOwnership)
            }

            override fun onMessageUpdateCredentialResponse(updateCredentialResult: String) {
                binding.tvStatusUpdating.append("\n- onMessageUpdateCredentialResponse $updateCredentialResult")
                when (updateCredentialResult) {
                    "update" ->
                        updateCheckFlow.sendMessageGetUpdatedData()

                    "delete" -> {
                        findNavController().navigate(
                            UpdateCheckFragmentDirections.actionUpdateCheckFragmentToDeleteDocumentFragment(
                                document
                            )
                        )
                    }
                    else -> {
                        document.dateCheckForUpdate = Calendar.getInstance()
                        documentManager.updateDocument(document)

                        updateCheckFlow.sendMessageRequestEndSession()
                    }
                }
            }

            override fun onMessageDataToUpdate(
                visibleName: String,
                personalizationData: PersonalizationData
            ) {
                binding.tvStatusUpdating.append("\n- onMessageDataToUpdate: $visibleName\n")
                //mUserVisibleName = visibleName

                try {
                    val proofOfProvisioning = credential.update(personalizationData)
                    if (document.userVisibleName != visibleName) {
                        document.userVisibleName = visibleName
                        document.dateCheckForUpdate = Calendar.getInstance()
                        documentManager.updateDocument(document)
                    }
                    log("proofOfProvisioning:")
                    proofOfProvisioning.let {
                        FormatUtil.debugPrintEncodeToString(it)
                    }
                    updateCheckFlow.sendMessageProofOfProvisioning(proofOfProvisioning)
                } catch (e: RuntimeException) {
                    binding.tvStatusUpdating.append("\n- Error provisioning: ${e.cause}\n")
                }
            }
        })

        log("Checking update for ${document.userVisibleName}")
        binding.tvStatusUpdating.append("\n- Checking update for ${document.userVisibleName}")
        val credentialCertificateChain = credential.credentialKeyCertificateChain
        // returns the Cose_Sign1 Obj with the MSO in the payload
        credentialCertificateChain.first()?.publicKey?.let { publicKey ->
            val cborCoseKey = FormatUtil.cborBuildCoseKey(publicKey)
            updateCheckFlow.sendMessageUpdateCheck(FormatUtil.cborEncode(cborCoseKey))
        }
    }
}