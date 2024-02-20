package com.android.identity_credential.wallet

import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.identity.credential.Credential
import com.android.identity.credential.CredentialStore
import com.android.identity.issuance.CredentialExtensions.credentialConfiguration
import com.android.identity.issuance.CredentialExtensions.credentialIdentifier
import com.android.identity.issuance.CredentialExtensions.issuingAuthorityIdentifier
import com.android.identity.issuance.CredentialExtensions.refreshState
import com.android.identity.issuance.CredentialRegistrationResponse
import com.android.identity.issuance.IssuingAuthority
import com.android.identity.issuance.IssuingAuthorityRepository
import com.android.identity.issuance.ProofingFlow
import com.android.identity.issuance.evidence.EvidenceRequest
import com.android.identity.issuance.evidence.EvidenceRequestIcaoNfcTunnel
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseIcaoNfcTunnel
import com.android.identity.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ProvisioningViewModel : ViewModel() {

    companion object {
        private const val TAG = "ProvisioningViewModel"
    }

    enum class State {
        IDLE,
        CREDENTIAL_REGISTRATION,
        EVIDENCE_REQUESTS_READY,
        SUBMITTING_EVIDENCE,
        PROOFING_COMPLETE,
        FAILED,
    }

    var state = mutableStateOf(ProvisioningViewModel.State.IDLE)

    var error: Throwable? = null

    // If we're provisioning, this is non-null set to the issuer
    private lateinit var issuer: IssuingAuthority
//    private lateinit var application: WalletApplication

    fun reset() {
        state.value = State.IDLE
        error = null
        credential = null
        proofingFlow = null
        evidenceRequests = null
    }

    private var proofingFlow: ProofingFlow? = null

    var credential: Credential? = null
    var evidenceRequests: List<EvidenceRequest>? = null

    fun start(
        issuingAuthorityRepository: IssuingAuthorityRepository,
        credentialStore: CredentialStore,
        issuer: IssuingAuthority
    ) {
        this.issuer = issuer

        viewModelScope.launch(Dispatchers.IO) {
            try {
                state.value = State.CREDENTIAL_REGISTRATION
                val createCredentialKeyFlow = this@ProvisioningViewModel.issuer.registerCredential()
                val credentialRegistrationConfiguration =
                    createCredentialKeyFlow.getCredentialRegistrationConfiguration()
                val issuerCredentialIdentifier = credentialRegistrationConfiguration.identifier
                val response = CredentialRegistrationResponse()
                createCredentialKeyFlow.sendCredentialRegistrationResponse(response)

                val credentialIdentifier =
                    issuer.configuration.identifier + "_" + issuerCredentialIdentifier
                credential = credentialStore.createCredential(credentialIdentifier)
                val pendingCredConf = issuer.configuration.pendingCredentialInformation

                credential!!.let {
                    it.issuingAuthorityIdentifier = issuer.configuration.identifier
                    it.credentialIdentifier = issuerCredentialIdentifier
                    it.credentialConfiguration = pendingCredConf
                    it.refreshState(issuingAuthorityRepository)
                }

                proofingFlow = issuer.credentialProof(issuerCredentialIdentifier)
                evidenceRequests = proofingFlow!!.getEvidenceRequests()
                Logger.d(TAG, "ers0 ${evidenceRequests!!.size}")
                if (evidenceRequests!!.size == 0) {
                    state.value = State.PROOFING_COMPLETE
                    credential!!.let {
                        it.refreshState(issuingAuthorityRepository)
                    }
                } else {
                    state.value = State.EVIDENCE_REQUESTS_READY
                }
            } catch (e: Throwable) {
                if (credential != null) {
                    credentialStore.deleteCredential(credential!!.name)
                }
                Logger.w(TAG, "Error registering Credential", e)
                e.printStackTrace()
                error = e
                state.value = State.FAILED
            }
        }
    }

    fun provideEvidence(
        evidence: EvidenceResponse,
        issuingAuthorityRepository: IssuingAuthorityRepository,
        credentialStore: CredentialStore
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                state.value = State.SUBMITTING_EVIDENCE

                proofingFlow!!.sendEvidence(evidence)

                evidenceRequests = proofingFlow!!.getEvidenceRequests()
                Logger.d(TAG, "ers1 ${evidenceRequests!!.size}")
                if (evidenceRequests!!.size == 0) {
                    state.value = State.PROOFING_COMPLETE
                    credential!!.let {
                        it.refreshState(issuingAuthorityRepository)
                    }
                } else {
                    state.value = State.EVIDENCE_REQUESTS_READY
                }
            } catch (e: Throwable) {
                if (credential != null) {
                    credentialStore.deleteCredential(credential!!.name)
                }
                Logger.w(TAG, "Error submitting evidence", e)
                e.printStackTrace()
                error = e
                state.value = State.FAILED
            }
        }
    }

    /**
     * Handles request/response exchange through NFC tunnel.
     *
     * This must be called on a backround thread and will block until the tunnel is
     * closed.
     *
     * @param handler a handler that communicates to the chip passing in the requests that it
     *     gets as parameter returning responses wrapped in [EvidenceResponseIcaoTunnel].
     */
    fun runIcaoNfcTunnel(handler: (EvidenceRequestIcaoNfcTunnel) -> EvidenceResponseIcaoNfcTunnel) {
        // must run on a background thread
        if (Looper.getMainLooper().isCurrentThread) {
            throw IllegalStateException("Must not be called on main thread")
        }

        runBlocking {
            // handshake
            proofingFlow!!.sendEvidence(EvidenceResponseIcaoNfcTunnel(byteArrayOf()))

            while (true) {
                val requests = proofingFlow!!.getEvidenceRequests()
                if (requests.size != 1) {
                    break
                }
                val request = requests[0]
                if (request !is EvidenceRequestIcaoNfcTunnel) {
                    break
                }

                val response = handler(request)
                proofingFlow!!.sendEvidence(response)
            }
        }
    }

    fun finishTunnel() {
        viewModelScope.launch(Dispatchers.IO) {
            // This is a hack needed since evidenceRequests is not a state (and it should be).
            // TODO: remove this once evidenceRequests becomes state
            state.value = State.SUBMITTING_EVIDENCE
            state.value = State.EVIDENCE_REQUESTS_READY
            evidenceRequests = proofingFlow!!.getEvidenceRequests()
        }
    }
}