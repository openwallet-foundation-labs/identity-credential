package org.multipaz.models.provisioning

import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.io.bytestring.encodeToByteString
import org.multipaz.credential.Credential
import org.multipaz.document.AbstractDocumentMetadata
import org.multipaz.document.Document
import org.multipaz.document.DocumentStore
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.prompt.PromptModel
import org.multipaz.provision.AuthorizationChallenge
import org.multipaz.provision.AuthorizationResponse
import org.multipaz.provision.CredentialFormat
import org.multipaz.provision.Display
import org.multipaz.provision.KeyBindingInfo
import org.multipaz.provision.KeyBindingType
import org.multipaz.provision.ProvisioningClient
import org.multipaz.provision.openid4vci.OpenID4VCI
import org.multipaz.provision.openid4vci.OpenID4VCIBackend
import org.multipaz.provision.openid4vci.OpenID4VCIClientPreferences
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.sdjwt.credential.KeyBoundSdJwtVcCredential
import org.multipaz.sdjwt.credential.KeylessSdJwtVcCredential
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.SecureArea
import org.multipaz.securearea.SecureAreaProvider
import org.multipaz.storage.Storage
import org.multipaz.util.Logger
import kotlin.math.min
import kotlin.reflect.KClass
import kotlin.reflect.safeCast
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class ProvisioningModel(
    val documentStore: DocumentStore,
    val secureArea: SecureArea,
    val httpClient: HttpClient,
    val storage: Storage,
    val promptModel: PromptModel,
    val documentMetadataInitializer: suspend (
            metadata: AbstractDocumentMetadata,
            credentialDisplay: Display,
            issuerDisplay: Display
        ) -> Unit
) {
    private var mutableState = MutableSharedFlow<State>()

    val state: SharedFlow<State> get() = mutableState

    private val authorizationResponseChannel = Channel<AuthorizationResponse>()

    fun launchOpenid4VciProvisioning(
        offerUri: String,
        clientPreferences: OpenID4VCIClientPreferences,
        backend: OpenID4VCIBackend
    ): Deferred<Document> {
        val coroutineContext =
            Dispatchers.Default + promptModel + ProvisioningEnvironment(backend)
        return CoroutineScope(coroutineContext).async {
            try {
                mutableState.emit(Initial)
                val provisioningClient = OpenID4VCI.createClientFromOffer(offerUri, clientPreferences)
                runProvisioning(provisioningClient)
            } catch(err: CancellationException) {
                throw err
            } catch(err: Throwable) {
                Logger.e(TAG, "Error provisioning", err)
                mutableState.emit(Error(err))
                throw err
            }
        }
    }

    suspend fun provideAuthorizationResponse(response: AuthorizationResponse) {
        authorizationResponseChannel.send(response)
    }


    private suspend fun runProvisioning(provisioningClient: ProvisioningClient): Document {
        mutableState.emit(Connected)
        val issuerMetadata = provisioningClient.getMetadata()
        val credentialMetadata = issuerMetadata.credentials.values.first()

        var evidenceRequests = provisioningClient.getAuthorizationChallenges()

        while (evidenceRequests.isNotEmpty()) {
            mutableState.emit(Authorizing(evidenceRequests))
            val authorizationResponse = authorizationResponseChannel.receive()
            mutableState.emit(ProcessingAuthorization)
            provisioningClient.authorize(authorizationResponse)
            evidenceRequests = provisioningClient.getAuthorizationChallenges()
        }

        mutableState.emit(Authorized)

        val format = credentialMetadata.format
        val document = documentStore.createDocument { metadata ->
            documentMetadataInitializer(
                metadata,
                credentialMetadata.display,
                issuerMetadata.display
            )
        }
        val credentialCount = min(credentialMetadata.maxBatchSize, 3)

        var pendingCredentials: List<Credential>

        // get the initial set of credentials
        val keyInfo = if (credentialMetadata.keyProofType == KeyBindingType.Keyless) {
            // keyless, no need for keys
            pendingCredentials = listOf()
            KeyBindingInfo.Keyless
        } else {
            // create keys in the selected secure area and send them to the issuer
            val keyChallenge = provisioningClient.getKeyBindingChallenge()
            val createKeySettings = CreateKeySettings(
                algorithm = when (val type = credentialMetadata.keyProofType) {
                    is KeyBindingType.OpenidProofOfPossession -> type.algorithm
                    is KeyBindingType.Attestation -> type.algorithm
                    else -> throw IllegalStateException()
                },
                nonce = keyChallenge.encodeToByteString(),
                userAuthenticationRequired = true
            )
            if (format is CredentialFormat.Mdoc) {
                pendingCredentials = (0..<credentialCount).map {
                    MdocCredential.create(
                        document = document,
                        asReplacementForIdentifier = null,
                        domain = CREDENTIAL_DOMAIN_MDOC,
                        secureArea = secureArea,
                        docType = format.docType,
                        createKeySettings = createKeySettings
                    )
                }
            } else if (format is CredentialFormat.SdJwt) {
                pendingCredentials = (0..<credentialCount).map {
                    KeyBoundSdJwtVcCredential.create(
                        document = document,
                        asReplacementForIdentifier = null,
                        domain = CREDENTIAL_DOMAIN_SD_JWT_VC,
                        secureArea = secureArea,
                        vct = format.vct,
                        createKeySettings = createKeySettings
                    )
                }
            } else {
                throw IllegalArgumentException("no supported configuration")
            }

            when (credentialMetadata.keyProofType) {
                is KeyBindingType.Attestation -> {
                    KeyBindingInfo.Attestation(pendingCredentials.map { it.getAttestation() })
                }
                // TODO: proof of possession
                else -> throw IllegalStateException()
            }
        }

        mutableState.emit(RequestingCredentials)
        val credentials = provisioningClient.obtainCredentials(keyInfo)

        if (credentialMetadata.keyProofType == KeyBindingType.Keyless) {
            if (credentials.size != 1) {
                throw IllegalStateException("Only a single keyless credential must be issued")
            }
            KeylessSdJwtVcCredential.create(
                document,
                null,
                CREDENTIAL_DOMAIN_SD_JWT_VC_KEYLESS,
                (format as CredentialFormat.SdJwt).vct
            )
        } else {
            for ((credentialData, pendingCredential) in credentials.zip(pendingCredentials)) {
                pendingCredential.certify(
                    credentialData.toByteArray(),
                    Clock.System.now(),  // TODO: remove the parameter
                    Clock.System.now() + 30.days  // TODO
                )
            }
        }
        document.metadata.markAsProvisioned()
        mutableState.emit(CredentialsIssued)
        return document
    }


    sealed class State

    data object Initial: State()
    data object Connected: State()
    data class Authorizing(
        val authorizationChallenges: List<AuthorizationChallenge>
    ): State()
    data object ProcessingAuthorization: State()
    data object Authorized: State()
    data object RequestingCredentials: State()
    data object CredentialsIssued: State()

    data class Error(
        val err: Throwable
    ): State()

    inner class ProvisioningEnvironment(
        val openid4VciBackend: OpenID4VCIBackend
    ): BackendEnvironment {
        val secureAreaProvider = SecureAreaProvider { secureArea }
        override fun <T : Any> getInterface(clazz: KClass<T>): T? = clazz.safeCast(
            when (clazz) {
                HttpClient::class -> httpClient
                Storage::class -> storage
                SecureAreaProvider::class -> secureAreaProvider
                OpenID4VCIBackend::class -> openid4VciBackend
                else -> null
            }
        )
    }

    companion object {
        const val CREDENTIAL_DOMAIN_MDOC = "mdoc_user_auth"
        const val CREDENTIAL_DOMAIN_SD_JWT_VC = "sdjwt_user_auth"
        const val CREDENTIAL_DOMAIN_SD_JWT_VC_KEYLESS = "sdjwt_keyless"

        const val TAG = "ProvisioningModel"
    }

}