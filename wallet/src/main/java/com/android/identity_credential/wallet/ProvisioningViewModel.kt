package com.android.identity_credential.wallet

import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.identity.document.Document
import com.android.identity.document.DocumentStore
import com.android.identity.issuance.DocumentCondition
import com.android.identity.issuance.DocumentExtensions.documentConfiguration
import com.android.identity.issuance.DocumentExtensions.documentIdentifier
import com.android.identity.issuance.DocumentExtensions.issuingAuthorityConfiguration
import com.android.identity.issuance.DocumentExtensions.issuingAuthorityIdentifier
import com.android.identity.issuance.DocumentExtensions.refreshState
import com.android.identity.issuance.DocumentExtensions.state
import com.android.identity.issuance.RegistrationResponse
import com.android.identity.issuance.IssuingAuthority
import com.android.identity.issuance.IssuingAuthorityException
import com.android.identity.issuance.ProofingFlow
import com.android.identity.issuance.evidence.EvidenceRequest
import com.android.identity.issuance.evidence.EvidenceRequestIcaoNfcTunnel
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseIcaoNfcTunnel
import com.android.identity.issuance.remote.WalletServerProvider
import com.android.identity.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.buildByteString

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

    private lateinit var issuer: IssuingAuthority

    fun reset() {
        state.value = State.IDLE
        error = null
        document = null
        proofingFlow = null
        evidenceRequests = null
        nextEvidenceRequest.value = null
    }

    private var proofingFlow: ProofingFlow? = null

    var document: Document? = null
    private var evidenceRequests: List<EvidenceRequest>? = null

    val nextEvidenceRequest = mutableStateOf<EvidenceRequest?>(null)

    fun start(
        walletServerProvider: WalletServerProvider,
        documentStore: DocumentStore,
        issuerIdentifier: String,
        settingsModel: SettingsModel,
    ) {

        viewModelScope.launch(Dispatchers.IO) {
            try {
                issuer = walletServerProvider.getIssuingAuthority(issuerIdentifier)
                val issuerConfiguration = issuer.getConfiguration()

                state.value = State.CREDENTIAL_REGISTRATION
                val createDocumentKeyFlow = this@ProvisioningViewModel.issuer.register()
                val documentRegistrationConfiguration =
                    createDocumentKeyFlow.getDocumentRegistrationConfiguration()
                val issuerDocumentIdentifier = documentRegistrationConfiguration.documentId
                val response = RegistrationResponse(
                    settingsModel.developerModeEnabled.value!!
                )
                createDocumentKeyFlow.sendDocumentRegistrationResponse(response)
                createDocumentKeyFlow.complete()

                val documentIdentifier =
                    issuerConfiguration.identifier + "_" + issuerDocumentIdentifier
                document = documentStore.createDocument(documentIdentifier)
                val pendingDocumentConfiguration = issuerConfiguration.pendingDocumentInformation

                document!!.let {
                    it.issuingAuthorityIdentifier = issuerConfiguration.identifier
                    it.documentIdentifier = issuerDocumentIdentifier
                    it.documentConfiguration = pendingDocumentConfiguration
                    it.issuingAuthorityConfiguration = issuerConfiguration
                    it.refreshState(walletServerProvider)
                }

                proofingFlow = issuer.proof(issuerDocumentIdentifier)
                evidenceRequests = proofingFlow!!.getEvidenceRequests()
                Logger.d(TAG, "ers0 ${evidenceRequests!!.size}")
                if (evidenceRequests!!.size == 0) {
                    state.value = State.PROOFING_COMPLETE
                    document!!.let {
                        it.refreshState(walletServerProvider)
                    }
                    documentStore.addDocument(document!!)
                    proofingFlow!!.complete()
                } else {
                    nextEvidenceRequest.value = evidenceRequests!!.first()
                    state.value = State.EVIDENCE_REQUESTS_READY
                }
            } catch (e: Throwable) {
                if (document != null) {
                    documentStore.deleteDocument(document!!.name)
                }
                Logger.w(TAG, "Error registering Document", e)
                e.printStackTrace()
                error = e
                state.value = State.FAILED
            }
        }
    }

    fun evidenceCollectionFailed(
        error: Throwable,
        walletServerProvider: WalletServerProvider,
        documentStore: DocumentStore
    ) {
        if (document != null) {
            documentStore.deleteDocument(document!!.name)
        }
        Logger.w(TAG, "Error collecting evidence", error)
        this.error = error
        state.value = State.FAILED
    }

    fun provideEvidence(
        evidence: EvidenceResponse,
        walletServerProvider: WalletServerProvider,
        documentStore: DocumentStore
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                state.value = State.SUBMITTING_EVIDENCE

                proofingFlow!!.sendEvidence(evidence)

                evidenceRequests = proofingFlow!!.getEvidenceRequests()
                Logger.d(TAG, "ers1 ${evidenceRequests!!.size}")
                if (evidenceRequests!!.size == 0) {
                    state.value = State.PROOFING_COMPLETE
                    document!!.refreshState(walletServerProvider)
                    documentStore.addDocument(document!!)
                    proofingFlow!!.complete()
                    document!!.refreshState(walletServerProvider)
                } else {
                    nextEvidenceRequest.value = evidenceRequests!!.first()
                    state.value = State.EVIDENCE_REQUESTS_READY
                }
            } catch (e: Throwable) {
                if (document != null) {
                    documentStore.deleteDocument(document!!.name)
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
            proofingFlow!!.sendEvidence(EvidenceResponseIcaoNfcTunnel(buildByteString {}))

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
            nextEvidenceRequest.value = evidenceRequests!!.first()
        }
    }
}