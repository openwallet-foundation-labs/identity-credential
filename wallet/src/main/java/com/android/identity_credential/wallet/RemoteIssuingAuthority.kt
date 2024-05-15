package com.android.identity_credential.wallet

import com.android.identity.document.NameSpacedData
import com.android.identity.documenttype.knowntypes.DrivingLicense
import com.android.identity.flow.handler.FlowEnvironment
import com.android.identity.flow.handler.FlowHandler
import com.android.identity.flow.handler.FlowHandlerLocal
import com.android.identity.flow.handler.FlowHandlerRemote
import com.android.identity.issuance.CredentialData
import com.android.identity.issuance.CredentialFormat
import com.android.identity.issuance.DocumentConfiguration
import com.android.identity.issuance.DocumentState
import com.android.identity.issuance.IssuingAuthority
import com.android.identity.issuance.IssuingAuthorityConfiguration
import com.android.identity.issuance.IssuingAuthorityMainImpl
import com.android.identity.issuance.ProofingFlow
import com.android.identity.issuance.RegistrationFlow
import com.android.identity.issuance.RequestCredentialsFlow
import com.android.identity.issuance.hardcoded.IssuingAuthorityState
import com.android.identity.issuance.hardcoded.dmv_issuing_authority_logo_png
import com.android.identity.issuance.hardcoded.driving_license_card_art_png
import com.android.identity.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString

class RemoteIssuingAuthority : IssuingAuthority {
    private val backend = IssuingAuthorityMainImpl(
        // Use remote = false for development only
        createFlowHandler(remote = true),
        "IssuingAuthorityState"
    )

    companion object {

        // TODO: this should be fetched from the server, but there is no good place to add
        // that extra (asynchronous) step yet.
        val hardcodedConfig = IssuingAuthorityConfiguration(
            identifier = "mDL_Elbonia",
            issuingAuthorityName = "Elbonia DMV",
            issuingAuthorityLogo = dmv_issuing_authority_logo_png,
            description = "Elbonia Driver's License",
            documentFormats = setOf(CredentialFormat.MDOC_MSO),
            pendingDocumentInformation = DocumentConfiguration(
                "Pending",
                driving_license_card_art_png,
                DrivingLicense.MDL_DOCTYPE,
                NameSpacedData.Builder().build(),
                true
            ))

        // Run the server locally on your dev coomputer and tunnel it to your phone
        // using this command:
        //
        // adb reverse tcp:8080 tcp:8080
        //
        val BASE_URL = "http://localhost:8080/wallet-server"

        val httpClient = object: FlowHandlerRemote.HttpClient {
            val client = HttpClient(CIO)

            override suspend fun get(url: String): FlowHandlerRemote.HttpResponse {
                try {
                    val response = client.get("$BASE_URL/$url")
                    return FlowHandlerRemote.HttpResponse(
                        response.status.value,
                        response.status.description,
                        ByteString(response.readBytes())
                    )
                } catch (ex: Exception) {
                    throw FlowHandlerRemote.ConnectionException(ex.message ?: "")
                }
            }

            override suspend fun post(
                url: String,
                data: ByteString
            ): FlowHandlerRemote.HttpResponse {
                try {
                    val response = client.post("$BASE_URL/$url") {
                        setBody(data.toByteArray())
                    }
                    return FlowHandlerRemote.HttpResponse(
                        response.status.value,
                        response.status.description,
                        ByteString(response.readBytes())
                    )
                } catch (ex: Exception) {
                    throw FlowHandlerRemote.ConnectionException(ex.message ?: "")
                }
            }
        }

        private val noopCipher = object: FlowHandlerLocal.SimpleCipher {
            override fun encrypt(plaintext: ByteArray): ByteArray {
                return plaintext
            }

            override fun decrypt(ciphertext: ByteArray): ByteArray {
                return ciphertext
            }
        }

        fun createFlowHandler(remote: Boolean): FlowHandler {
            return if (remote) {
                FlowHandlerRemote(httpClient)
            } else {
                val flowHandlerLocal = FlowHandlerLocal.Builder()
                IssuingAuthorityState.registerAll(flowHandlerLocal)
                flowHandlerLocal.build(noopCipher, FlowEnvironment.EMPTY)
            }
        }
    }

    override val configuration: IssuingAuthorityConfiguration
        get() = hardcodedConfig

    override suspend fun register(): RegistrationFlow {
        return backend.register()
    }

    override suspend fun getState(documentId: String): DocumentState {
        return backend.getState(documentId)
    }

    private val _eventFlow = MutableSharedFlow<Pair<IssuingAuthority, String>>()

    override val eventFlow
        get() = _eventFlow.asSharedFlow()

    override suspend fun proof(documentId: String): ProofingFlow {
        return backend.proof(documentId)
    }

    override suspend fun getDocumentConfiguration(documentId: String): DocumentConfiguration {
        return backend.getDocumentConfiguration(documentId)
    }

    override suspend fun requestCredentials(documentId: String): RequestCredentialsFlow {
        return backend.requestCredentials(documentId)
    }

    override suspend fun getCredentials(documentId: String): List<CredentialData> {
        return backend.getCredentials(documentId)
    }

    override suspend fun developerModeRequestUpdate(
        documentId: String,
        requestRemoteDeletion: Boolean,
        notifyApplicationOfUpdate: Boolean
    ) {
        return backend.developerModeRequestUpdate(documentId,
            requestRemoteDeletion, notifyApplicationOfUpdate)
    }
}