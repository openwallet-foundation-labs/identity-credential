package com.android.identity_credential.wallet.presentation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.android.identity.android.securearea.AndroidKeystoreKeyInfo
import com.android.identity.android.securearea.AndroidKeystoreKeyUnlockData
import com.android.identity.android.securearea.UserAuthenticationType
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.Simple
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import com.android.identity.document.Document
import com.android.identity.document.DocumentRequest
import com.android.identity.document.NameSpacedData
import com.android.identity.issuance.CredentialFormat
import com.android.identity.issuance.DocumentExtensions.documentConfiguration
import com.android.identity.issuance.DocumentExtensions.issuingAuthorityIdentifier
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.mdoc.mso.MobileSecurityObjectParser
import com.android.identity.mdoc.mso.StaticAuthDataParser
import com.android.identity.mdoc.response.DeviceResponseGenerator
import com.android.identity.mdoc.response.DocumentGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.securearea.KeyLockedException
import com.android.identity.securearea.KeyUnlockData
import com.android.identity.trustmanagement.TrustPoint
import com.android.identity.util.Constants
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.WalletApplication
import com.android.identity_credential.wallet.ui.ScreenWithAppBar
import com.android.identity_credential.wallet.ui.prompt.biometric.showBiometricPrompt
import com.android.identity_credential.wallet.ui.prompt.consent.ConsentPromptEntryField
import com.android.identity_credential.wallet.ui.prompt.consent.ConsentPromptEntryFieldData
import com.android.identity_credential.wallet.ui.theme.IdentityCredentialTheme
// TODO: replace the nimbusds library usage with non-java-based alternative
import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.crypto.ECDHEncrypter
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier
import com.nimbusds.jose.proc.JWSKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.shaded.gson.Gson
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jose.util.X509CertUtils
import com.nimbusds.jwt.EncryptedJWT
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jwt.proc.BadJWTException
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.formUrlEncode
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.InternalAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.cert.X509Certificate
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

@Serializable
internal data class PresentationSubmission(
    @Required val id: String,
    @Required
    @SerialName("definition_id")
    val definitionId: String,
    @Required
    @SerialName("descriptor_map")
    val descriptorMaps: List<DescriptorMap>,
)

@Serializable
internal data class DescriptorMap(
    @Required val id: String,
    @Required val format: String,
    @Required val path: String,
)

internal data class AuthorizationRequest (
    var presentationDefinition: JsonObject,
    var clientId: String,
    var nonce: String,
    var responseUri: String,
    var state: String?,
    var clientMetadata: JsonObject,
    var certificateChain: List<X509Certificate>?
)

internal data class ResponseComponents (
    var documentRequest: DocumentRequest,
    var sessionTranscript: ByteArray,
    var jweHeader: JWEHeader,
    var responseUrl: Url,
    var state: String?,
    var presentationSubmission: PresentationSubmission,
    val keySet: JWKSet
)

class OpenID4VPPresentationActivity : FragmentActivity() {
    companion object {
        private const val TAG = "OpenID4VPPresentationActivity"
    }

    private enum class State { PROCESSING, RESPONSE_SENT }

    private var state = MutableLiveData<State>()
    private var document: Document? = null
    private var responseComponents: ResponseComponents? = null
    private var consentData: ConsentPromptEntryFieldData? = null

    // creating new HttpClients is not cheap, better to reuse/not create when possible
    private var httpClient = lazy { HttpClient {
        install(ContentNegotiation) { json() }
        expectSuccess = true
    } }

    private val walletApp: WalletApplication by lazy {
        application as WalletApplication
    }

    private val showErrorAndDismiss: (Throwable) -> Unit = { throwable ->
        Toast.makeText(applicationContext, throwable.message, Toast.LENGTH_SHORT).show()
        onDestroy()
    }

    private fun onAuthenticationKeyLocked(credential: MdocCredential) {
        val keyInfo = credential.secureArea.getKeyInfo(credential.alias)
        var userAuthenticationTypes = emptySet<UserAuthenticationType>()
        if (keyInfo is AndroidKeystoreKeyInfo) {
            userAuthenticationTypes = keyInfo.userAuthenticationTypes
        }

        val unlockData = AndroidKeystoreKeyUnlockData(credential.alias)
        val cryptoObject = unlockData.getCryptoObjectForSigning(Algorithm.ES256)

        showBiometricPrompt(
            activity = this,
            title = applicationContext.resources.getString(R.string.presentation_biometric_prompt_title),
            subtitle = applicationContext.resources.getString(R.string.presentation_biometric_prompt_subtitle),
            cryptoObject = cryptoObject,
            userAuthenticationTypes = userAuthenticationTypes,
            requireConfirmation = false,
            onCanceled = { finish() },
            onSuccess = {
                // create and send response on IO thread
                lifecycleScope.launch {
                    try {
                        createAndSendResponse(unlockData, credential)
                    } catch (e: Throwable) {
                        Logger.e(TAG, e.toString())
                        showErrorAndDismiss(IllegalStateException("Unexpected error"))
                    }
                } },
            onError = {exception ->
                Logger.e(TAG, exception.toString())
                finish() },
        )
    }

    override fun onDestroy() {
        Logger.i(TAG, "onDestroy")
        if (httpClient.isInitialized()) {
            httpClient.value.close()
        }
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val supportedUriSchemes = listOf("mdoc-openid4vp", "eudi-openid4vp")
        var authorizationRequest = ""
        if (supportedUriSchemes.contains(intent.scheme)) {
            authorizationRequest = Uri.parse(intent.toUri(0)).toString()
            state.value = State.PROCESSING
        } else {
            Logger.e(TAG, "URI scheme not recognized")
            showErrorAndDismiss(IllegalArgumentException("URI scheme not recognized"))
        }

        setContent {
            IdentityCredentialTheme {

                val stateDisplay = remember { mutableStateOf("Idle") }
                val consentPromptData = remember { mutableStateOf<ConsentPromptEntryFieldData?>(null) }

                state.observe(this as LifecycleOwner) { state ->
                    when (state) {
                        State.PROCESSING -> {
                            Logger.i(TAG, "State: Processing")
                            stateDisplay.value = "Processing"
                            try {
                                processRequest(authorizationRequest, consentPromptData)
                            } catch (e: Throwable) {
                                Logger.e(TAG, e.toString())
                                showErrorAndDismiss(IllegalStateException("Unexpected error"))
                            }

                        }

                        State.RESPONSE_SENT -> {
                            Logger.i(TAG, "State: Response Sent")
                            stateDisplay.value = "Response Sent"
                        }

                        else -> {}
                    }
                }

                ScreenWithAppBar(title = "Presenting", navigationIcon = { }) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Sending mDL to reader.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "TODO: finalize UI",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        HorizontalDivider()
                        Text(
                            text = "State: ${stateDisplay.value}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        HorizontalDivider()
                        Button(onClick = { finish() }) {
                            Text("Close")
                        }
                    }

                    // when consent data is available, show consent prompt
                    consentData = consentPromptData.value
                    if (consentData != null) {
                        ConsentPromptEntryField(
                            consentData = consentData!!,
                            documentTypeRepository = walletApp.documentTypeRepository,
                            onConfirm = {
                                // create and send response on IO thread
                                lifecycleScope.launch {
                                    try {
                                        createAndSendResponse()
                                    } catch (e: Throwable) {
                                        Logger.e(TAG, e.toString())
                                        showErrorAndDismiss(IllegalStateException("Unexpected error"))
                                    }
                                }
                            },
                            onCancel = {
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun processRequest(
        authRequest: String,
        consentPromptData: MutableState<ConsentPromptEntryFieldData?>
    )  {
        val uri = Uri.parse(authRequest)

        lifecycleScope.launch {
            val authorizationRequest = getAuthorizationRequest(uri, httpClient, showErrorAndDismiss)
            val presentationSubmission = createPresentationSubmission(authorizationRequest)
            val inputDescriptors = authorizationRequest.presentationDefinition["input_descriptors"]!!.jsonArray

            // for now, we only respond to the first credential being requested
            // NOTE: openid4vp spec gives a non-normative example of multiple input descriptors
            // as "alternatives credentials" https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-5.1-6
            // but identity.foundation says all input descriptors MUST be satisfied https://identity.foundation/presentation-exchange/spec/v2.0.0/#input-descriptor
            val inputDescriptorObj = inputDescriptors[0].jsonObject
            val docType = inputDescriptorObj["id"]!!.toString().run { substring(1, this.length - 1) }
            val verifierName = inputDescriptorObj["name"].toString().run { substring(1, this.length - 1) }
            val format = inputDescriptorObj["format"]!!.jsonObject
            val documentRequest = formatAsDocumentRequest(inputDescriptorObj)

            val credentialFormat: CredentialFormat
            if (format.contains("mso_mdoc")) {
                credentialFormat = CredentialFormat.MDOC_MSO
            } else {
                throw IllegalArgumentException("We only support the mdoc profile.")
            }
            document = firstMatchingDocument(credentialFormat, docType)
                ?: run { throw IllegalStateException("No matching credentials in wallet") }

            // begin collecting and creating data needed for the response
            val secureRandom = Random.Default
            val bytes = ByteArray(16)
            secureRandom.nextBytes(bytes)
            val mdocGeneratedNonce = Base64.UrlSafe.encode(bytes)
            val sessionTranscript = createSessionTranscript(
                clientId = authorizationRequest.clientId,
                responseUri = authorizationRequest.responseUri,
                authorizationRequestNonce = authorizationRequest.nonce,
                mdocGeneratedNonce = mdocGeneratedNonce
            )

            if (authorizationRequest.clientMetadata["authorization_signed_response_alg"] != null) {
                TODO("Support signing the authorization response")
            }
            val responseEncryptionAlg = JWEAlgorithm.parse(authorizationRequest.clientMetadata
                ["authorization_encrypted_response_alg"]!!.toString().run { substring(1, this.length - 1) })
            val responseEncryptionMethod = EncryptionMethod.parse(authorizationRequest.clientMetadata
                ["authorization_encrypted_response_enc"]!!.toString().run { substring(1, this.length - 1) })
            val apv = Base64URL.encode(authorizationRequest.nonce)
            val apu = Base64URL.encode(mdocGeneratedNonce)
            val jweHeader = JWEHeader.Builder(responseEncryptionAlg, responseEncryptionMethod)
                .apply {
                    apv?.let(::agreementPartyVInfo)
                    apu?.let(::agreementPartyUInfo)
                }
                .build()

            responseComponents = ResponseComponents(
                documentRequest = documentRequest,
                sessionTranscript = sessionTranscript,
                jweHeader = jweHeader,
                responseUrl = Url(authorizationRequest.responseUri),
                state = authorizationRequest.state,
                presentationSubmission = presentationSubmission,
                keySet = getKeySet(authorizationRequest.clientMetadata)
            )

            consentPromptData.value = ConsentPromptEntryFieldData(
                documentRequest = documentRequest,
                docType = docType,
                documentName = document!!.documentConfiguration.displayName,
                credentialData = document!!.documentConfiguration.mdocConfiguration!!.staticData,
                credentialId = document!!.name,
                verifier = if (authorizationRequest.certificateChain != null) TrustPoint(
                    authorizationRequest.certificateChain!![0], verifierName) else null
            )
        }
    }

    private fun firstMatchingDocument(
        credentialFormat: CredentialFormat,
        docType: String
    ): Document? {
        val settingsModel = walletApp.settingsModel
        val documentStore = walletApp.documentStore

        // prefer the credential which is on-screen if possible
        val credentialIdFromPager: String? = settingsModel.focusedCardId.value
        if (credentialIdFromPager != null
            && canDocumentSatisfyRequest(credentialIdFromPager, credentialFormat, docType)
        ) {
            return documentStore.lookupDocument(credentialIdFromPager)!!
        }

        val docId = documentStore.listDocuments().firstOrNull { credentialId ->
            canDocumentSatisfyRequest(credentialId, credentialFormat, docType)
        }
        return docId?.let { documentStore.lookupDocument(it) }
    }

    private fun canDocumentSatisfyRequest(
        credentialId: String,
        credentialFormat: CredentialFormat,
        docType: String
    ): Boolean {
        val credential = walletApp.documentStore.lookupDocument(credentialId)!!
        val issuingAuthorityIdentifier = credential.issuingAuthorityIdentifier
//        if (!credentialFormats.contains(credentialFormat)) {
//            return false
//        }

        return credential.documentConfiguration.mdocConfiguration?.docType == docType
    }

    private suspend fun getKeySet(clientMetadata: JsonObject): JWKSet {
        if (clientMetadata["jwks"] != null) {
            TODO("Add support for parsing keySet directly")
        }

        val jwksUri = clientMetadata["jwks_uri"].toString().run { substring(1, this.length - 1) }
        val unparsed = httpClient.value.get(Url(jwksUri)).body<String>()
        return JWKSet.parse(unparsed)
    }

    /**
     * [OutgoingContent] for `application/x-www-form-urlencoded` formatted requests that use US-ASCII encoding.
     */
    internal class FormData(
        val formData: Parameters,
    ) : OutgoingContent.ByteArrayContent() {
        private val content = formData.formUrlEncode().toByteArray(Charsets.US_ASCII)

        override val contentLength: Long = content.size.toLong()
        override val contentType: ContentType = ContentType.Application.FormUrlEncoded

        override fun bytes(): ByteArray = content
    }

    @OptIn(InternalAPI::class, ExperimentalEncodingApi::class)
    private suspend fun createAndSendResponse(
        keyUnlockData: KeyUnlockData? = null,
        credential: MdocCredential? = null,
    ) {
        val now = Clock.System.now()
        val credentialToUse: MdocCredential = credential
            ?: (document!!.findCredential(WalletApplication.CREDENTIAL_DOMAIN_MDOC, now)
                ?: run {
                    showErrorAndDismiss(IllegalArgumentException("No valid credentials, please request more"))
                    return
                }) as MdocCredential

        val staticAuthData = StaticAuthDataParser(credentialToUse.issuerProvidedData).parse()
        val issuerAuthCoseSign1 = Cbor.decode(staticAuthData.issuerAuth).asCoseSign1
        val encodedMsoBytes = Cbor.decode(issuerAuthCoseSign1.payload!!)
        val encodedMso = Cbor.encode(encodedMsoBytes.asTaggedEncodedCbor)
        val mso = MobileSecurityObjectParser(encodedMso).parse()

        val credentialConfiguration = document!!.documentConfiguration
        val mergedIssuerNamespaces = MdocUtil.mergeIssuerNamesSpaces(
            responseComponents!!.documentRequest,
            credentialConfiguration.mdocConfiguration!!.staticData,
            staticAuthData
        )

        val deviceResponseGenerator = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)

        // in sep coroutine so that an unexpected error will still allow this function to
        // finish and send potentially empty response
        val result = withContext(Dispatchers.IO) { //<- Offload from UI thread
            addDocumentToResponse(
                deviceResponseGenerator = deviceResponseGenerator,
                docType = mso.docType,
                issuerAuth = staticAuthData.issuerAuth,
                mergedIssuerNamespaces = mergedIssuerNamespaces,
                credential = credentialToUse,
                keyUnlockData = keyUnlockData
            )
        }

        if (result != null) {
            onAuthenticationKeyLocked(result)
            return
        }

        // build response
        val deviceResponseCbor = deviceResponseGenerator.generate()
        val vpToken = Base64.UrlSafe.encode(deviceResponseCbor)
        val claimSet = JWTClaimsSet.parse(Json.encodeToString(buildJsonObject {
            // put("id_token", idToken) // depends on response type, only supporting vp_token for now
            put("state", responseComponents!!.state)
            put("vp_token", vpToken)
            put("presentation_submission", Json.encodeToJsonElement(responseComponents!!.presentationSubmission))
        }))


        // encrypt
        val jweEncrypter: ECDHEncrypter? = responseComponents!!.keySet.keys.mapNotNull { key ->
            runCatching { ECDHEncrypter(key as ECKey) }.getOrNull()?.let { encrypter -> key to encrypter }}
            .toMap().firstNotNullOfOrNull { it.value }
        val encrypted = EncryptedJWT(responseComponents!!.jweHeader, claimSet).apply { encrypt(jweEncrypter) }

        // send response
        val requestState: String? = responseComponents!!.state
        val response = httpClient.value.post(responseComponents!!.responseUrl.toString()) {
            body = FormData(Parameters.build {
                append("response", encrypted.serialize())
                requestState?.let {
                    append("state", it)
                }
            })
        }

        // receive redirectUri and launch with browser
        when (response.status) {
            HttpStatusCode.OK -> {
                val responseBody = response.body<JsonObject?>()
                val redirectUri =
                    try {
                        responseBody
                            ?.get("redirect_uri")
                            ?.takeIf { it is JsonPrimitive }
                            ?.jsonPrimitive?.contentOrNull
                            ?.let { Uri.parse(it) }
                    } catch (t: NoTransformationFoundException) {
                        null
                    }
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(redirectUri.toString())))
            }

            else -> Logger.d(TAG, "Response from verifier not ok")
        }

        // ensure we update UI-bound state value on Main thread
        lifecycleScope.launch {
            state.value = State.RESPONSE_SENT
        }

        // terminate PresentationActivity since "presentation is complete" (once response is sent)
        finish()
    }

    private suspend fun addDocumentToResponse(
        deviceResponseGenerator: DeviceResponseGenerator,
        docType: String,
        issuerAuth: ByteArray,
        mergedIssuerNamespaces: Map<String, MutableList<ByteArray>>,
        credential: MdocCredential,
        keyUnlockData: KeyUnlockData?
    ) = suspendCancellableCoroutine { continuation ->
        var result: MdocCredential?

        try {
            deviceResponseGenerator.addDocument(
                DocumentGenerator(
                    docType,
                    issuerAuth, responseComponents!!.sessionTranscript
                )
                    .setIssuerNamespaces(mergedIssuerNamespaces)
                    .setDeviceNamespacesSignature(
                        NameSpacedData.Builder().build(),
                        credential.secureArea,
                        credential.alias,
                        keyUnlockData,
                        Algorithm.ES256
                    )
                    .generate()
            )
            credential.increaseUsageCount()
            if (credential.usageCount > 1) {
                Toast.makeText(
                    applicationContext,
                    applicationContext.resources.getString(R.string.presentation_credential_usage_warning),
                    Toast.LENGTH_SHORT
                ).show()
            }
            result = null
        } catch (e: KeyLockedException) {
            result = credential
        }

        continuation.resume(result)
    }
}

// returns <namespace, dataElem>
internal fun parsePathItem(item: String): Pair<String, String> {
    // format: "$['namespace']['dataElem']"
    val spacer = item.indexOf("']['")
    val namespace = item.substring(4, spacer)
    val dataElem = item.substring(spacer + 4, item.length - 3)
    return Pair(namespace, dataElem)
}

// defined in ISO 18013-7 Annex B
private fun createSessionTranscript(
    clientId: String,
    responseUri: String,
    authorizationRequestNonce: String,
    mdocGeneratedNonce: String
): ByteArray {
    val clientIdToHash = Cbor.encode(CborArray.builder()
        .add(clientId)
        .add(mdocGeneratedNonce)
        .end()
        .build())
    val clientIdHash = Crypto.digest(Algorithm.SHA256, clientIdToHash)

    val responseUriToHash = Cbor.encode(CborArray.builder()
        .add(responseUri)
        .add(mdocGeneratedNonce)
        .end()
        .build())
    val responseUriHash = Crypto.digest(Algorithm.SHA256, responseUriToHash)

    val oid4vpHandover = CborArray.builder()
        .add(clientIdHash)
        .add(responseUriHash)
        .add(authorizationRequestNonce)
        .end()
        .build()

    return Cbor.encode(
        CborArray.builder()
            .add(Simple.NULL)
            .add(Simple.NULL)
            .add(oid4vpHandover)
            .end()
            .build()
    )
}

private suspend fun getAuthorizationRequest(requestUri: Uri, httpClient: Lazy<HttpClient>, onError: (Exception) -> Unit): AuthorizationRequest {
    // simplified same-device flow:
    val presentationDefinition = requestUri.getQueryParameter("presentation_definition")?.let { Json.parseToJsonElement(it).jsonObject }
    if (presentationDefinition != null) {
        return AuthorizationRequest(
            presentationDefinition = presentationDefinition,
            clientId = requestUri.getQueryParameter("client_id")!!,
            nonce = requestUri.getQueryParameter("nonce")!!,
            responseUri = requestUri.getQueryParameter("response_uri")!!,
            state = requestUri.getQueryParameter("state"),
            clientMetadata = requestUri.getQueryParameter("client_metadata")!!
                .run { Json.parseToJsonElement(this).jsonObject },
            certificateChain = null
        )
    }

    val clientId = requestUri.getQueryParameter("client_id")!!
    val requestValue = requestUri.getQueryParameter("request")
    if (requestValue != null) {
        return getAuthRequestFromJwt(SignedJWT.parse(requestValue), clientId)
    }

    // more complex same-device flow as outline in Annex B -> request_uri is
    // needed to retrieve Request Object from provided url
    val requestUriValue = requestUri.getQueryParameter("request_uri")
    if (requestUriValue == null) {
        onError(IllegalArgumentException("Unexpected Error"))
    }

    val httpResponse = httpClient.value.get(urlString = requestUriValue!!) {
        accept(ContentType.parse("application/oauth-authz-req+jwt"))
        accept(ContentType.parse("application/jwt"))
    }.body<String>()

    return getAuthRequestFromJwt(SignedJWT.parse(httpResponse), clientId)
}

internal fun getAuthRequestFromJwt(signedJWT: SignedJWT, clientId: String): AuthorizationRequest {
    if (signedJWT.jwtClaimsSet.getStringClaim("client_id") != clientId) {
        throw IllegalArgumentException("Client ID doesn't match")
    }
    val x5c = signedJWT.header?.x509CertChain ?: throw IllegalArgumentException("Error retrieving cert chain")
    val pubCertChain = x5c.mapNotNull { runCatching { X509CertUtils.parse(it.decode()) }.getOrNull() }
    if (pubCertChain.isEmpty()) {
        throw IllegalArgumentException("Invalid x5c")
    }

    // verify JWT signature
    try {
        val jwtProcessor = DefaultJWTProcessor<SecurityContext>().apply {
            // see also: DefaultJOSEObjectTypeVerifier.JWT
            jwsTypeVerifier =
                DefaultJOSEObjectTypeVerifier(
                    JOSEObjectType("oauth-authz-req+jwt"),
                    JOSEObjectType.JWT,
                    JOSEObjectType(""),
                    null,
                )
            jwsKeySelector = JWSKeySelector<SecurityContext> { _, _ -> listOf(pubCertChain[0].publicKey) }
            jwtClaimsSetVerifier = TimeChecks()
        }
        jwtProcessor.process(signedJWT, null)
    } catch (e: Throwable) {
        throw RuntimeException(e)
    }

    // build auth request
    val jsonStr = Gson().toJson(signedJWT.jwtClaimsSet.getJSONObjectClaim("presentation_definition"))
    val presentationDefinition = Json.parseToJsonElement(jsonStr).jsonObject

    if (signedJWT.jwtClaimsSet.getStringClaim("response_mode") != "direct_post.jwt") { // required as part of mdoc profile; NOTE that sd-jwt profile requires direct_post
        throw IllegalArgumentException("Response modes other than direct_post.jwt are unsupported.")
    }
    if (signedJWT.jwtClaimsSet.getStringClaim("response_type") != "vp_token") { // not supporting id_token atm
        throw IllegalArgumentException("Response types other than vp_token are unsupported.")
    }

    return AuthorizationRequest(
        presentationDefinition = presentationDefinition,
        clientId = signedJWT.jwtClaimsSet.getStringClaim("client_id"),
        nonce = signedJWT.jwtClaimsSet.getStringClaim("nonce"),
        responseUri = signedJWT.jwtClaimsSet.getStringClaim("response_uri"),
        state = signedJWT.jwtClaimsSet.getStringClaim("state"),
        clientMetadata = Json.parseToJsonElement(Gson().toJson(signedJWT.jwtClaimsSet.getJSONObjectClaim("client_metadata"))).jsonObject,
        certificateChain = pubCertChain
    )
}

private class TimeChecks : JWTClaimsSetVerifier<SecurityContext> {
    @Throws(BadJWTException::class)
    override fun verify(claimsSet: JWTClaimsSet, context: SecurityContext?) {
        val now = Clock.System.now()

        val expiration = claimsSet.expirationTime
        var exp: Instant? = null
        if (expiration != null) {
            exp = Instant.fromEpochMilliseconds(expiration.time)
            if (exp >= now) {
                throw BadJWTException("Expired JWT")
            }
        }

        val issuance = claimsSet.issueTime
        var iat: Instant? = null
        if (issuance != null) {
            iat = Instant.fromEpochMilliseconds(issuance.time)
            if (now <= iat) {
                throw BadJWTException("JWT issued in the future")
            }

            if (exp != null) {
                if (exp <= iat) {
                    throw BadJWTException("JWT issued after expiration")
                }
            }
        }

        val notBefore = claimsSet.notBeforeTime
        if (notBefore != null) {
            val nbf = Instant.fromEpochMilliseconds(notBefore.time)
            if (nbf >= now) {
                throw BadJWTException("JWT not yet active")
            }

            if (exp != null) {
                if (nbf >= exp) {
                    throw BadJWTException("JWT active after expiration")
                }
            }

            if (iat != null) {
                if (nbf <= iat) {
                    throw BadJWTException("JWT active before issuance")
                }
            }
        }
    }
}

internal fun createPresentationSubmission(authRequest: AuthorizationRequest): PresentationSubmission {
    val descriptorMaps = ArrayList<DescriptorMap>()
    val inputDescriptors = authRequest.presentationDefinition["input_descriptors"]!!.jsonArray

    for (inputDescriptor: JsonElement in inputDescriptors) {
        val inputDescriptorObj = inputDescriptor.jsonObject
        val docType = inputDescriptorObj["id"]!!.toString().run { substring(1, this.length - 1) }
        descriptorMaps.add(DescriptorMap(
            id = docType,
            format = "mso_mdoc",
            path = "$"
        ))
    }

    return PresentationSubmission(
        id = UUID.randomUUID().toString(),
        definitionId = authRequest.presentationDefinition["id"]!!.toString().run { substring(1, this.length - 1) },
        descriptorMaps = descriptorMaps
    )
}

internal fun formatAsDocumentRequest(inputDescriptor: JsonObject): DocumentRequest {
    val requestedDataElements = ArrayList<DocumentRequest.DataElement>()
    val constraints = inputDescriptor["constraints"]!!.jsonObject
    val fields = constraints["fields"]!!.jsonArray

    for (field: JsonElement in fields) {
        val fieldObj = field.jsonObject
        val path = fieldObj["path"]!!.jsonArray[0].toString()
        val intentToRetain = fieldObj["intent_to_retain"].toString()
        val parsed = parsePathItem(path)
        requestedDataElements.add(DocumentRequest.DataElement(
            nameSpaceName = parsed.first,
            dataElementName = parsed.second,
            intentToRetain = intentToRetain == "true"
        ))
    }
    return DocumentRequest(requestedDataElements)
}