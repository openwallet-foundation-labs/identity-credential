package com.android.identity_credential.wallet

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.identity.credential.Credential
import com.android.identity.issuance.CredentialRegistrationResponse
import com.android.identity.issuance.evidence.EvidenceRequest
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.IssuingAuthority
import com.android.identity.issuance.ProofingFlow
import com.android.identity.issuance.CredentialExtensions.credentialConfiguration
import com.android.identity.issuance.CredentialExtensions.credentialIdentifier
import com.android.identity.issuance.CredentialExtensions.issuingAuthorityIdentifier
import com.android.identity.issuance.CredentialExtensions.refreshState
import com.android.identity.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    private lateinit var application: WalletApplication

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

    fun start(application: WalletApplication,
              issuer: IssuingAuthority) {
        this.application = application
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

                val credentialIdentifier = issuer.configuration.identifier + "_" + issuerCredentialIdentifier
                credential = application.credentialStore.createCredential(credentialIdentifier)
                val pendingCredConf = issuer.configuration.pendingCredentialInformation

                credential!!.let {
                    it.issuingAuthorityIdentifier = issuer.configuration.identifier
                    it.credentialIdentifier = issuerCredentialIdentifier
                    it.credentialConfiguration = pendingCredConf
                    it.refreshState(application.issuingAuthorityRepository)
                }

                proofingFlow = issuer.credentialProof(issuerCredentialIdentifier)
                evidenceRequests = proofingFlow!!.getEvidenceRequests()
                Logger.d(TAG, "ers0 ${evidenceRequests!!.size}")
                if (evidenceRequests!!.size == 0) {
                    state.value = State.PROOFING_COMPLETE
                    credential!!.let {
                        it.refreshState(application.issuingAuthorityRepository)
                    }
                } else {
                    state.value = State.EVIDENCE_REQUESTS_READY
                }
            } catch (e: Throwable) {
                if (credential != null) {
                    application.credentialStore.deleteCredential(credential!!.name)
                }
                Logger.w(TAG, "Error registering Credential", e)
                e.printStackTrace()
                error = e
                state.value = State.FAILED
            }
        }
    }

    fun provideEvidence(evidence: EvidenceResponse) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                state.value = State.SUBMITTING_EVIDENCE

                proofingFlow!!.sendEvidence(evidence)

                evidenceRequests = proofingFlow!!.getEvidenceRequests()
                Logger.d(TAG, "ers1 ${evidenceRequests!!.size}")
                if (evidenceRequests!!.size == 0) {
                    state.value = State.PROOFING_COMPLETE
                    credential!!.let {
                        it.refreshState(application.issuingAuthorityRepository)
                    }
                } else {
                    state.value = State.EVIDENCE_REQUESTS_READY
                }
            } catch (e: Throwable) {
                if (credential != null) {
                    application.credentialStore.deleteCredential(credential!!.name)
                }
                Logger.w(TAG, "Error submitting evidence", e)
                e.printStackTrace()
                error = e
                state.value = State.FAILED
            }
        }
    }


}