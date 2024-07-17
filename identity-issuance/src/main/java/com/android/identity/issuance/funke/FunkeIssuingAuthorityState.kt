package com.android.identity.issuance.funke

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.documenttype.knowntypes.EUPersonalID
import com.android.identity.flow.annotation.FlowJoin
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.flow.server.Storage
import com.android.identity.issuance.CredentialData
import com.android.identity.issuance.DocumentCondition
import com.android.identity.issuance.DocumentConfiguration
import com.android.identity.issuance.DocumentState
import com.android.identity.issuance.IssuingAuthority
import com.android.identity.issuance.IssuingAuthorityConfiguration
import com.android.identity.issuance.IssuingAuthorityNotification
import com.android.identity.issuance.RegistrationResponse
import com.android.identity.issuance.WalletServerSettings
import com.android.identity.issuance.common.AbstractIssuingAuthorityState
import com.android.identity.issuance.common.cache
import com.android.identity.util.Logger
import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteString

@FlowState(
    flowInterface = IssuingAuthority::class
)
@CborSerializable
class FunkeIssuingAuthorityState(
    val clientId: String
) : AbstractIssuingAuthorityState() {
    companion object {
        private const val TAG = "FunkeIssuingAuthorityState"

        private const val DOCUMENT_TABLE = "FunkeIssuerDocument"

        fun getConfiguration(env: FlowEnvironment): IssuingAuthorityConfiguration {
            return env.cache(IssuingAuthorityConfiguration::class, "funke") { configuration, resources ->
                val logoPath = "funke/logo.png"
                val logo = resources.getRawResource(logoPath)!!
                val artPath = "funke/card_art.png"
                val art = resources.getRawResource(artPath)!!
                val requireUserAuthenticationToViewDocument = true
                IssuingAuthorityConfiguration(
                    identifier = "funke",
                    issuingAuthorityName = "Funke PID Issuer",
                    issuingAuthorityLogo = logo.toByteArray(),
                    issuingAuthorityDescription = "Funke",
                    pendingDocumentInformation = DocumentConfiguration(
                        displayName = "Pending",
                        typeDisplayName = "EU Personal ID",
                        cardArt = art.toByteArray(),
                        requireUserAuthenticationToViewDocument = requireUserAuthenticationToViewDocument,
                        mdocConfiguration = null,
                        sdJwtVcDocumentConfiguration = null
                    )
                )
            }
        }

        val documentTypeRepository = DocumentTypeRepository().apply {
            addDocumentType(EUPersonalID.getDocumentType())
        }
    }

    @FlowMethod
    fun getConfiguration(env: FlowEnvironment): IssuingAuthorityConfiguration {
        return FunkeIssuingAuthorityState.getConfiguration(env)
    }

    @FlowMethod
    suspend fun register(env: FlowEnvironment): FunkeRegistrationState {
        val documentId = createIssuerDocument(env,
            FunkeIssuerDocument(
                RegistrationResponse(false),
                DocumentCondition.PROOFING_REQUIRED,
                null,
                null,
                mutableListOf()
            )
        )
        return FunkeRegistrationState(documentId)
    }

    @FlowJoin
    suspend fun completeRegistration(env: FlowEnvironment, registrationState: FunkeRegistrationState) {
        updateIssuerDocument(
            env,
            registrationState.documentId,
            FunkeIssuerDocument(
                registrationState.response!!,
                DocumentCondition.PROOFING_REQUIRED,
                null, // no evidence yet
                null,  // no initial document configuration
                mutableListOf()           // cpoRequests - initially empty
            ))
    }

    @FlowMethod
    suspend fun getState(env: FlowEnvironment, documentId: String): DocumentState {
        val now = Clock.System.now()

        if (!issuerDocumentExists(env, documentId)) {
            return DocumentState(
                now,
                DocumentCondition.NO_SUCH_DOCUMENT,
                0,
                0
            )
        }

        val issuerDocument = loadIssuerDocument(env, documentId)

        if (issuerDocument.state == DocumentCondition.PROOFING_PROCESSING
            && issuerDocument.evidence != null) {
            issuerDocument.state = if (issuerDocument.evidence?.url != null) {
                DocumentCondition.CONFIGURATION_AVAILABLE
            } else {
                DocumentCondition.PROOFING_FAILED
            }
            updateIssuerDocument(env, documentId, issuerDocument)
        }

        // This information is helpful for when using the admin web interface.
        Logger.i(
            TAG, "Returning state for clientId=$clientId documentId=$documentId")

        // We create and sign Credential requests immediately so numPendingCredentialRequests is
        // always 0
        return DocumentState(
            now,
            issuerDocument.state,
            0,
            issuerDocument.simpleCredentialRequests.count()
        )
    }

    @FlowMethod
    fun proof(env: FlowEnvironment, documentId: String): FunkeProofingState {
        return FunkeProofingState(documentId)
    }

    @FlowJoin
    suspend fun completeProof(env: FlowEnvironment, state: FunkeProofingState) {
        val issuerDocument = loadIssuerDocument(env, state.documentId)
        issuerDocument.evidence = state.evidence
        updateIssuerDocument(env, state.documentId, issuerDocument)
    }

    @FlowMethod
    suspend fun getDocumentConfiguration(
        env: FlowEnvironment,
        documentId: String
    ): DocumentConfiguration {
        // TODO
        throw RuntimeException()
    }

    @FlowMethod
    suspend fun requestCredentials(
        env: FlowEnvironment,
        documentId: String
    ): FunkeRequestCredentialsState {
        // TODO
        throw RuntimeException()
    }

    @FlowMethod
    suspend fun getCredentials(env: FlowEnvironment, documentId: String): List<CredentialData> {
        // TODO
        throw RuntimeException()
    }

    @FlowMethod
    suspend fun developerModeRequestUpdate(
        env: FlowEnvironment,
        documentId: String,
        requestRemoteDeletion: Boolean,
        notifyApplicationOfUpdate: Boolean
    ) {
        // TODO
        throw RuntimeException()
    }

    private suspend fun issuerDocumentExists(env: FlowEnvironment, documentId: String): Boolean {
        if (clientId.isEmpty()) {
            throw IllegalStateException("Client not authenticated")
        }
        val storage = env.getInterface(Storage::class)!!
        val encodedCbor = storage.get(DOCUMENT_TABLE, clientId, documentId)
        return encodedCbor != null
    }

    private suspend fun loadIssuerDocument(env: FlowEnvironment, documentId: String): FunkeIssuerDocument {
        if (clientId.isEmpty()) {
            throw IllegalStateException("Client not authenticated")
        }
        val storage = env.getInterface(Storage::class)!!
        val encodedCbor = storage.get(DOCUMENT_TABLE, clientId, documentId)
            ?: throw Error("No such document")
        return FunkeIssuerDocument.fromCbor(encodedCbor.toByteArray())
    }

    private suspend fun createIssuerDocument(env: FlowEnvironment, document: FunkeIssuerDocument): String {
        if (clientId.isEmpty()) {
            throw IllegalStateException("Client not authenticated")
        }
        val storage = env.getInterface(Storage::class)!!
        val bytes = document.toCbor()
        return storage.insert(DOCUMENT_TABLE, clientId, ByteString(bytes))
    }

    private suspend fun deleteIssuerDocument(env: FlowEnvironment,
                                             documentId: String,
                                             emitNotification: Boolean = true) {
        if (clientId.isEmpty()) {
            throw IllegalStateException("Client not authenticated")
        }
        val storage = env.getInterface(Storage::class)!!
        storage.delete(DOCUMENT_TABLE, clientId, documentId)
        if (emitNotification) {
            emit(env, IssuingAuthorityNotification(documentId))
        }
    }

    private suspend fun updateIssuerDocument(
        env: FlowEnvironment,
        documentId: String,
        document: FunkeIssuerDocument,
        emitNotification: Boolean = true,
    ) {
        if (clientId.isEmpty()) {
            throw IllegalStateException("Client not authenticated")
        }
        val storage = env.getInterface(Storage::class)!!
        val bytes = document.toCbor()
        storage.update(DOCUMENT_TABLE, clientId, documentId, ByteString(bytes))
        if (emitNotification) {
            emit(env, IssuingAuthorityNotification(documentId))
        }
    }
}