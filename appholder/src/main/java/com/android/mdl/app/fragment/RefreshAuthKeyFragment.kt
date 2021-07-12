package com.android.mdl.app.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.security.identity.IdentityCredentialStore
import co.nstant.`in`.cbor.CborBuilder
import com.android.mdl.app.databinding.FragmentRefreshAuthKeyBinding
import com.android.mdl.app.document.Document
import com.android.mdl.app.provisioning.RefreshAuthenticationKeyFlow
import com.android.mdl.app.util.FormatUtil


class RefreshAuthKeyFragment : Fragment() {
    companion object {
        private const val LOG_TAG = "RefreshAuthKeyFragment"
    }

    private val args: RefreshAuthKeyFragmentArgs by navArgs()
    private lateinit var serverUrl: String
    private lateinit var document: Document

    private var _binding: FragmentRefreshAuthKeyBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        serverUrl = args.serverUrl
        document = args.document
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRefreshAuthKeyBinding.inflate(inflater)
        binding.fragment = this

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        val store = if (document.hardwareBacked)
            IdentityCredentialStore.getHardwareInstance(requireContext())
                ?: IdentityCredentialStore.getSoftwareInstance(requireContext())
        else
            IdentityCredentialStore.getSoftwareInstance(requireContext())

        val credential = store.getCredentialByName(
            document.identityCredentialName,
            IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256
        )
        credential?.setAvailableAuthenticationKeys(1, 1)
        val dynAuthKeyCerts = credential?.authKeysNeedingCertification
        val credentialCertificateChain = credential?.credentialKeyCertificateChain

        // Start refresh auth key flow
        val refreshAuthKeyFlow =
            RefreshAuthenticationKeyFlow.getInstance(requireContext(), serverUrl)
        refreshAuthKeyFlow.setListener(object : RefreshAuthenticationKeyFlow.Listener {
            override fun onMessageSessionEnd(reason: String) {
                binding.tvStatusRefreshing.append("\n- onMessageSessionEnd: $reason\n")

                //Check if provisioning was successful
                if (reason == "Success") {
                    binding.btDone.visibility = View.VISIBLE
                }
                binding.loadingProgress.visibility = View.GONE
            }

            override fun sendMessageRequestEnd(reason: String) {
                binding.tvStatusRefreshing.append("\n- sendMessageRequestEnd: $reason\n")
                binding.loadingProgress.visibility = View.GONE
            }

            override fun onError(error: String) {
                binding.tvStatusRefreshing.append("\n- onError: $error\n")
                binding.loadingProgress.visibility = View.GONE
            }

            override fun onMessageProveOwnership(challenge: ByteArray) {
                binding.tvStatusRefreshing.append(
                    "\n- onMessageProveOwnership: ${FormatUtil.encodeToString(challenge)}\n"
                )
                val proveOwnership = credential?.proveOwnership(challenge)

                refreshAuthKeyFlow.sendMessageProveOwnership(proveOwnership)
            }

            override fun onMessageCertifyAuthKeysReady() {
                binding.tvStatusRefreshing.append("\n- onMessageCertifyAuthKeysReady")
                val builderArray = CborBuilder()
                    .addArray()
                dynAuthKeyCerts?.forEach { cert ->
                    builderArray.add(cert.encoded)
                }
                val authKeyCerts = FormatUtil.cborEncode(builderArray.end().build()[0])
                refreshAuthKeyFlow.sendMessageAuthKeyNeedingCertification(authKeyCerts)
            }

            override fun onMessageStaticAuthData(staticAuthDataList: MutableList<ByteArray>) {
                binding.tvStatusRefreshing.append("\n- onMessageStaticAuthData ${staticAuthDataList.size} ")

                dynAuthKeyCerts?.forEachIndexed { i, cert ->
                    Log.d(
                        LOG_TAG,
                        "Provisioned Isser Auth ${FormatUtil.encodeToString(staticAuthDataList[i])} " +
                                "for Device Key ${FormatUtil.encodeToString(cert.publicKey.encoded)}"
                    )
                    credential.storeStaticAuthenticationData(cert, staticAuthDataList[i])
                }
                refreshAuthKeyFlow.sendMessageRequestEndSession()

            }
        })

        if (dynAuthKeyCerts?.isNotEmpty() == true) {
            Log.d(LOG_TAG, "Device Keys needing certification ${dynAuthKeyCerts.size}")
            binding.tvStatusRefreshing.append("\n- Device Keys needing certification ${dynAuthKeyCerts.size}")
            // returns the Cose_Sign1 Obj with the MSO in the payload
            credentialCertificateChain?.first()?.publicKey?.let { publicKey ->
                val cborCoseKey = FormatUtil.cborBuildCoseKey(publicKey)
                refreshAuthKeyFlow.sendMessageCertifyAuthKeys(FormatUtil.cborEncode(cborCoseKey))
            }
        } else {
            Log.d(LOG_TAG, "No Device Keys Needing Certification for now")

            binding.tvStatusRefreshing.append("\n- No Device Keys Needing Certification for now")
        }
    }

    fun onDone() {
        findNavController().navigate(
            RefreshAuthKeyFragmentDirections.actionRefreshAuthKeyFragmentToSelectDocumentFragment()
        )
    }
}