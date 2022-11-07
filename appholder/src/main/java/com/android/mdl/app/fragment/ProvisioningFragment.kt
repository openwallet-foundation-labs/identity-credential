package com.android.mdl.app.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.identity.CipherSuiteNotSupportedException
import com.android.identity.PersonalizationData
import com.android.identity.WritableIdentityCredential
import com.android.mdl.app.databinding.FragmentProvisioningBinding
import com.android.mdl.app.document.Document
import com.android.mdl.app.document.DocumentManager
import com.android.mdl.app.provisioning.ProvisioningFlow
import com.android.mdl.app.util.FormatUtil

class ProvisioningFragment : Fragment() {

    companion object {
        private const val LOG_TAG = "ProvisioningFragment"
    }

    private var _binding: FragmentProvisioningBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private var serverUrl = ""
    private var provisioningCode = ""
    private var document: Document? = null
    private var proofOfProvisioning: ByteArray? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProvisioningBinding.inflate(inflater)
        binding.fragment = this

        return binding.root
    }

    fun onNext() {
        serverUrl = binding.etServerUrl.text.toString()
        provisioningCode = binding.etProvisioningCode.text.toString()

        // Check if the parameters are valid
        if (serverUrl.isBlank() || provisioningCode.isBlank()) {
            Log.i(
                LOG_TAG,
                "ServerUrl: '$serverUrl' or ProvisioningCode: '$provisioningCode' is invalid"
            )
            Toast.makeText(
                requireContext(), "Server URL and provisioning code cannot be blank.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        binding.loadingProgress.visibility = View.VISIBLE
        val documentManager = DocumentManager.getInstance(requireContext())

        val credentialName = "credentialName$provisioningCode"
        var mDocType = ""
        var mUserVisibleName = ""
        documentManager.deleteCredentialByName(credentialName)
        var wc: WritableIdentityCredential? = null

        // Start provisioning flow
        val provisioningFlow = ProvisioningFlow()
        provisioningFlow.setListener(object : ProvisioningFlow.Listener {
            override fun onMessageSessionEnd(reason: String) {
                binding.tvStatusProvisioning.append("\n- onMessageSessionEnd: $reason\n")

                //Check if provisioning was successful
                if (reason == "Success") {
                    // Add document to the list of provisioned documents
                    document =
                        documentManager.addDocument(
                            mDocType,
                            credentialName,
                            mUserVisibleName,
                            serverUrl,
                            provisioningCode
                        )
                    binding.btSuccess.visibility = View.VISIBLE
                }

                binding.loadingProgress.visibility = View.GONE
            }

            override fun sendMessageRequestEnd(reason: String) {
                binding.tvStatusProvisioning.append("\n- sendMessageRequestEnd: $reason\n")
                binding.loadingProgress.visibility = View.GONE
            }

            override fun onError(error: String) {
                binding.tvStatusProvisioning.append("\n- onError: $error\n")
                binding.loadingProgress.visibility = View.GONE
            }

            override fun onMessageReadyToProvision() {
                binding.tvStatusProvisioning.append("\n- onMessageReadyToProvision $provisioningCode\n")
                provisioningFlow.sendMessageStartIdentityCredentialProvision()
            }

            override fun onMessageProvisioningResponse(docType: String, challenge: ByteArray) {
                Log.d(LOG_TAG, "onMessageProvisioningResponse")
                binding.tvStatusProvisioning.append(
                    "\n- onMessageProvisioningResponse: $${FormatUtil.encodeToString(challenge)}\n"
                )
                mDocType = docType
                try {
                    wc = documentManager.createCredential(document, credentialName, docType)
                    val certificateChain =
                        wc?.getCredentialKeyCertificateChain(challenge)
                    Log.d(LOG_TAG, "sendMessageSetCertificateChain")
                    var certificateChainEncoded = byteArrayOf()
                    certificateChain?.forEach { cer ->
                        certificateChainEncoded += cer.encoded
                    }
                    FormatUtil.debugPrintEncodeToString(LOG_TAG, certificateChainEncoded)

                    provisioningFlow.sendMessageSetCertificateChain(certificateChainEncoded)
                } catch (ex: CipherSuiteNotSupportedException) {
                    Log.e(LOG_TAG, ex.message, ex)
                }
            }

            override fun onMessageDataToProvision(
                visibleName: String,
                personalizationData: PersonalizationData
            ) {
                binding.tvStatusProvisioning.append("\n- onMessageDataToProvision: $visibleName\n")
                mUserVisibleName = visibleName
                try {
                    proofOfProvisioning = wc?.personalize(personalizationData)
                    Log.d(LOG_TAG, "proofOfProvisioning:")
                    proofOfProvisioning?.let {
                        FormatUtil.debugPrintEncodeToString(LOG_TAG, it)
                    }
                    provisioningFlow.sendMessageProofOfProvisioning(proofOfProvisioning)
                } catch (e: RuntimeException) {
                    binding.tvStatusProvisioning.append("\n- Error provisioning: ${e.cause}\n")
                }
            }

        }, requireContext())

        provisioningFlow.sendMessageStartProvisioning(serverUrl, provisioningCode)
    }

    fun onSuccess() {
        if (document == null) {
            Toast.makeText(
                requireContext(),
                "It was not possible to get the provisioned document. Please try again!",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        document?.let { doc ->
            findNavController().navigate(
                ProvisioningFragmentDirections.actionProvisioningFragmentToRefreshAuthKeyFragment(
                    serverUrl,
                    doc
                )
            )
        }
    }

    fun onCancel() {
        findNavController().navigate(
            ProvisioningFragmentDirections.actionProvisioningFragmentToSelectDocumentFragment()
        )
    }
}