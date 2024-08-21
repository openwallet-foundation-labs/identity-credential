package com.android.identity.wallet.server

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.DiagnosticOption
import com.android.identity.cbor.Simple
import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPublicKeyDoubleCoordinate
import com.android.identity.crypto.X509Cert
import com.android.identity.crypto.X509CertChain
import com.android.identity.crypto.X509CertificateCreateOption
import com.android.identity.crypto.X509CertificateExtension
import com.android.identity.crypto.create
import com.android.identity.crypto.javaPrivateKey
import com.android.identity.crypto.javaPublicKey
import com.android.identity.flow.server.Configuration
import com.android.identity.flow.server.Storage
import com.android.identity.mdoc.response.DeviceResponseParser
import com.android.identity.util.Logger
import com.android.identity.util.fromBase64
import com.android.identity.util.toBase64
import com.android.identity.util.toHex
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.ECDHDecrypter
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.util.Base64
import com.nimbusds.jwt.EncryptedJWT
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import jakarta.servlet.ServletConfig
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minidev.json.JSONArray
import net.minidev.json.JSONObject
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URLEncoder
import java.security.Security
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import kotlin.random.Random

enum class Protocol {
    PLAIN_OPENID4VP,
    EUDI_OPENID4VP,
    MDOC_OPENID4VP,
}

enum class RequestType {
    PID_MDOC_AGE_OVER_18,
    PID_MDOC_MANDATORY,
    PID_MDOC_FULL,
    MDL_MDOC_AGE_OVER_18,
    MDL_MDOC_AGE_OVER_21,
    MDL_MDOC_MANDATORY,
    MDL_MDOC_FULL,
}

@Serializable
private data class OpenID4VPBeginRequest(
    val requestType: String,
    val protocol: String
)

@Serializable
private data class OpenID4VPBeginResponse(
    val uri: String
)

@Serializable
private data class OpenID4VPRedirectUriResponse(
    val redirect_uri: String
)

@Serializable
private data class OpenID4VPGetData(
    val sessionId: String
)

@Serializable
private data class OpenID4VPResultData(
    val lines: List<OpenID4VPResultLine>
)

@Serializable
private data class OpenID4VPResultLine(
    val key: String,
    val value: String
)

@CborSerializable
data class Session(
    val id: String,
    val requestType: RequestType,
    val protocol: Protocol,
    val nonce: String,
    val encryptionKey: EcPrivateKey,
    var responseUri: String? = null,
    var deviceResponse: ByteArray? = null,
    var sessionTranscript: ByteArray? = null
) {
    companion object
}

/**
 * Verifier servlet.
 *
 * This is using the configuration and storage interfaces from [ServerEnvironment].
 */
class VerifierServlet : HttpServlet() {

    data class KeyMaterial(
        val readerRootKey: EcPrivateKey,
        val readerRootKeyCertificates: X509CertChain,
        val readerRootKeySignatureAlgorithm: Algorithm,
        val readerRootKeyIssuer: String,
    ) {
        fun toCbor() = Cbor.encode(
            CborArray.builder()
                .add(readerRootKey.toCoseKey().toDataItem())
                .add(readerRootKeyCertificates.toDataItem())
                .add(readerRootKeySignatureAlgorithm.coseAlgorithmIdentifier)
                .add(readerRootKeyIssuer)
                .end().build()
        )

        companion object {
            fun fromCbor(encodedCbor: ByteArray): KeyMaterial {
                val array = Cbor.decode(encodedCbor).asArray
                return KeyMaterial(
                    array[0].asCoseKey.ecPrivateKey,
                    array[1].asX509CertChain,
                    Algorithm.fromInt(array[2].asNumber.toInt()),
                    array[3].asTstr,
                )
            }

            fun createKeyMaterial(): KeyMaterial {
                val now = Clock.System.now()
                val validFrom = now
                val validUntil = now.plus(DateTimePeriod(years = 10), TimeZone.currentSystemDefault())

                val extensions = mutableListOf<X509CertificateExtension>()
                extensions.add(
                    X509CertificateExtension(
                        Extension.keyUsage.toString(),
                        true,
                        KeyUsage(KeyUsage.cRLSign + KeyUsage.keyCertSign).encoded
                    )
                )

                // Create Reader Root w/ self-signed certificate.
                //
                // TODO: Migrate to Curve P-384 once we migrate off com.nimbusds.* which
                // only supports Curve P-256.
                //
                val readerRootKey = Crypto.createEcPrivateKey(EcCurve.P256)
                val readerRootKeySignatureAlgorithm = Algorithm.ES256
                val readerRootKeySubject = "CN=OWF IC Online Verifier Reader Root Key"
                val readerRootKeyCertificate = X509Cert.create(
                    readerRootKey.publicKey,
                    readerRootKey,
                    null,
                    readerRootKeySignatureAlgorithm,
                    "1",
                    readerRootKeySubject,
                    readerRootKeySubject,
                    validFrom,
                    validUntil,
                    setOf(
                        X509CertificateCreateOption.INCLUDE_SUBJECT_KEY_IDENTIFIER,
                        X509CertificateCreateOption.INCLUDE_AUTHORITY_KEY_IDENTIFIER_AS_SUBJECT_KEY_IDENTIFIER
                    ),
                    extensions
                )

                return KeyMaterial(
                    readerRootKey,
                    X509CertChain(listOf(readerRootKeyCertificate)),
                    readerRootKeySignatureAlgorithm,
                    readerRootKeySubject,
                )
            }

        }
    }

    companion object {
        private const val TAG = "VerifierServlet"

        private const val STORAGE_TABLE_NAME = "VerifierSessions"

        private lateinit var serverEnvironment: ServerEnvironment
        private lateinit var storage: Storage
        private lateinit var configuration: Configuration

        @Synchronized
        private fun initialize(servletConfig: ServletConfig) {
            if (this::serverEnvironment.isInitialized) {
                return
            }

            serverEnvironment = ServerEnvironment(servletConfig)
            storage = serverEnvironment.getInterface(Storage::class)!!
            configuration = serverEnvironment.getInterface(Configuration::class)!!
        }

        private val keyMaterial: KeyMaterial by lazy {
            val storage = serverEnvironment.getInterface(Storage::class)!!
            val keyMaterialBlob = runBlocking {
                storage.get("RootState", "", "verifierKeyMaterial")?.toByteArray()
                    ?: let {
                        val blob = KeyMaterial.createKeyMaterial().toCbor()
                        storage.insert(
                            "RootState",
                            "",
                            ByteString(blob),
                            "verifierKeyMaterial"
                        )
                        blob
                    }
            }
            KeyMaterial.fromCbor(keyMaterialBlob)
        }
    }

    @Override
    override fun init() {
        super.init()

        Security.addProvider(BouncyCastleProvider())

        initialize(servletConfig)
    }

    private fun getRemoteHost(req: HttpServletRequest): String {
        var remoteHost = req.remoteHost
        val forwardedFor = req.getHeader("X-Forwarded-For")
        if (forwardedFor != null) {
            remoteHost = forwardedFor
        }
        return remoteHost
    }

    // Helper to get the local IP address used...
    private fun calcLocalAddress(): InetAddress {
        try {
            var candidateAddress: InetAddress? = null
            for (iface in NetworkInterface.getNetworkInterfaces()) {
                for (inetAddress in iface.inetAddresses) {
                    if (!inetAddress.isLoopbackAddress) {
                        if (inetAddress.isSiteLocalAddress) {
                            return inetAddress
                        } else if (candidateAddress == null) {
                            candidateAddress = inetAddress
                        }
                    }
                }
            }
            if (candidateAddress != null) {
                return candidateAddress
            }
            val jdkSuppliedAddress = InetAddress.getLocalHost()
                ?: throw IllegalStateException("Unexpected null from InetAddress.getLocalHost()")
            return jdkSuppliedAddress
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to determine address", e)
            throw IllegalStateException("Failed to determine address", e)
        }
    }

    private val baseUrl: String by lazy {
        var ret = configuration.getValue("verifierBaseUrl")
        if (ret == null || ret.length == 0) {
            ret = "http://" + calcLocalAddress().toString() + ":8080" + servletContext.contextPath
            Logger.i(TAG, "Using baseUrl calculated from IP address: $ret")
        } else {
            Logger.i(TAG, "Using baseUrl from configuration: $ret")
        }
        ret
    }

    private val clientId: String by lazy {
        var ret = configuration.getValue("verifierClientId")
        if (ret == null || ret.length == 0) {
            ret = baseUrl
        }
        ret
    }

    private fun createSingleUseReaderKey(): Pair<EcPrivateKey, X509CertChain> {
        val now = Clock.System.now()
        val validFrom = now
        val validUntil = now.plus(DateTimePeriod(minutes = 5), TimeZone.currentSystemDefault())
        val readerKey = Crypto.createEcPrivateKey(EcCurve.P256)

        val extensions = mutableListOf<X509CertificateExtension>()
        extensions.add(
            X509CertificateExtension(
                Extension.keyUsage.toString(),
                true,
                KeyUsage(KeyUsage.digitalSignature).encoded
            )
        )
        extensions.add(
            X509CertificateExtension(
                Extension.extendedKeyUsage.toString(),
                true,
                ExtendedKeyUsage(
                    KeyPurposeId.getInstance(ASN1ObjectIdentifier("1.0.18013.5.1.2"))
                ).encoded
            )
        )
        val readerKeySubject = "CN=OWF IC Online Verifier Single-Use Reader Key"
        val readerKeyCertificate = X509Cert.create(
            readerKey.publicKey,
            keyMaterial.readerRootKey,
            keyMaterial.readerRootKeyCertificates.certificates[0],
            keyMaterial.readerRootKeySignatureAlgorithm,
            "1",
            readerKeySubject,
            keyMaterial.readerRootKeyIssuer,
            validFrom,
            validUntil,
            setOf(
                X509CertificateCreateOption.INCLUDE_SUBJECT_KEY_IDENTIFIER,
                X509CertificateCreateOption.INCLUDE_AUTHORITY_KEY_IDENTIFIER_FROM_SIGNING_KEY_CERTIFICATE
            ),
            extensions
        )
        return Pair(
            readerKey,
            X509CertChain(listOf(readerKeyCertificate) + keyMaterial.readerRootKeyCertificates.certificates)
        )
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val remoteHost = getRemoteHost(req)
        Logger.i(TAG, "$remoteHost: POST ${req.requestURI}")

        val requestLength = req.contentLength
        val requestData = req.inputStream.readNBytes(requestLength)

        if (req.requestURI.endsWith("verifier/openid4vpBegin")) {
            handleOpenID4VPBegin(remoteHost, req, resp, requestData)

        } else if (req.requestURI.endsWith("verifier/openid4vpGetData")) {
            handleOpenID4VPGetData(remoteHost, req, resp, requestData)

        } else if (req.requestURI.endsWith("verifier/openid4vpResponse")) {
            return handleOpenID4VPResponse(remoteHost, req, resp, requestData)
        } else {
            Logger.w(TAG, "$remoteHost: Unexpected URI ${req.requestURI}")
            resp.status = HttpServletResponse.SC_BAD_REQUEST
        }
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val remoteHost = getRemoteHost(req)

        Logger.i(TAG, "$remoteHost: GET ${req.requestURI}")

        if (req.requestURI.endsWith("verifier/openid4vpRequest")) {
            handleOpenID4VPRequest(remoteHost, req, resp)
        } else if (req.requestURI.endsWith("verifier/readerRootCert")) {
            handleGetReaderRootCert(remoteHost, req, resp)
        } else {
            Logger.w(TAG, "$remoteHost: Unexpected URI ${req.requestURI}")
            resp.status = HttpServletResponse.SC_BAD_REQUEST
        }
    }

    private fun handleOpenID4VPBegin(
        remoteHost: String,
        req: HttpServletRequest,
        resp: HttpServletResponse,
        requestData: ByteArray
    ) {
        val requestString = String(requestData, 0, requestData.size, Charsets.UTF_8)
        val request = Json.decodeFromString<OpenID4VPBeginRequest>(requestString)

        val protocol = when (request.protocol) {
            // Keep in sync with verifier.html
            "openid4vp_plain" -> Protocol.PLAIN_OPENID4VP
            "openid4vp_eudi" -> Protocol.EUDI_OPENID4VP
            "openid4vp_mdoc" -> Protocol.MDOC_OPENID4VP
            else -> {
                Logger.w(TAG, "$remoteHost: Unknown protocol '$request.protocol'")
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                return
            }
        }

        val requestType = when (request.requestType) {
            // Keep in sync with verifier.html
            "pid_mdoc_age_over_18" -> RequestType.PID_MDOC_AGE_OVER_18
            "pid_mdoc_mandatory" -> RequestType.PID_MDOC_MANDATORY
            "pid_mdoc_full" -> RequestType.PID_MDOC_FULL
            "mdl_mdoc_age_over_18" -> RequestType.MDL_MDOC_AGE_OVER_18
            "mdl_mdoc_age_over_21" -> RequestType.MDL_MDOC_AGE_OVER_21
            "mdl_mdoc_mandatory" -> RequestType.MDL_MDOC_MANDATORY
            "mdl_mdoc_full" -> RequestType.MDL_MDOC_FULL
            else -> {
                Logger.w(TAG, "$remoteHost: Unknown request type '${request.requestType}'")
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                return
            }
        }

        // Create a new session
        val session = Session(
            id = Random.Default.nextBytes(16).toHex(),
            nonce = Random.Default.nextBytes(16).toHex(),
            encryptionKey = Crypto.createEcPrivateKey(EcCurve.P256),
            requestType = requestType,
            protocol = protocol
        )
        runBlocking {
            storage.insert(
                STORAGE_TABLE_NAME,
                "",
                ByteString(session.toCbor()),
                session.id
            )
        }

        val uriScheme = when (session.protocol) {
            Protocol.PLAIN_OPENID4VP -> "openid4vp://"
            Protocol.EUDI_OPENID4VP -> "eudi-openid4vp://"
            Protocol.MDOC_OPENID4VP -> "mdoc-openid4vp://"
        }
        val requestUri = baseUrl + "/verifier/openid4vpRequest?sessionId=${session.id}"
        val uri = uriScheme +
                "?client_id=" + URLEncoder.encode(clientId, Charsets.UTF_8) +
                "&request_uri=" + URLEncoder.encode(requestUri, Charsets.UTF_8)

        val json = Json { ignoreUnknownKeys = true }
        val responseString = json.encodeToString(OpenID4VPBeginResponse(uri))
        resp.status = HttpServletResponse.SC_OK
        resp.outputStream.write(responseString.encodeToByteArray())
        resp.contentType = "application/json"
    }

    private fun handleOpenID4VPRequest(
        remoteHost: String,
        req: HttpServletRequest,
        resp: HttpServletResponse,
    ) {
        val sessionId = req.getParameter("sessionId")
        if (sessionId == null) {
            Logger.e(TAG, "$remoteHost: No session parameter ${req.requestURI}")
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            return
        }
        val encodedSession = runBlocking {
            storage.get(
                STORAGE_TABLE_NAME,
                "",
                sessionId
            )
        }
        if (encodedSession == null) {
            Logger.e(TAG, "$remoteHost: No session for sessionId $sessionId")
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            return
        }
        val session = Session.fromCbor(encodedSession.toByteArray())

        val responseUri = baseUrl + "/verifier/openid4vpResponse?sessionId=${session.id}"

        val (singleUseReaderKeyPriv, singleUseReaderKeyCertChain) = createSingleUseReaderKey()

        val readerPub = singleUseReaderKeyPriv.publicKey.javaPublicKey as ECPublicKey
        val readerPriv = singleUseReaderKeyPriv.javaPrivateKey as ECPrivateKey
        val readerKey = ECKey(
            Curve.P_256,
            readerPub,
            readerPriv,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        )

        val readerX5c = singleUseReaderKeyCertChain.certificates.map { cert ->
            Base64.from(cert.encodedCertificate.toBase64())
        }

        val claimsSet = JWTClaimsSet.Builder()
            .claim("client_id", clientId)
            .claim("response_uri", responseUri)
            .claim("response_type", "vp_token")
            .claim("response_mode", "direct_post.jwt")
            .claim("nonce", session.nonce)
            .claim("state", session.id)
            .claim("presentation_definition", mdocCalcPresentationDefinition(session.requestType))
            .claim("client_metadata", calcClientMetadata(session))
            .build()

        val signedJWT = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(readerKey.getKeyID())
                .x509CertChain(readerX5c)
                .type(JOSEObjectType("oauth-authz-req+jwt"))
                .build(),
            claimsSet
        )

        val signer: JWSSigner = ECDSASigner(readerKey)
        signedJWT.sign(signer)

        val s = signedJWT.serialize()
        resp.contentType = "application/oauth-authz-req+jwt"
        resp.outputStream.write(s.encodeToByteArray())
        resp.status = HttpServletResponse.SC_OK

        // We'll need responseUri later (to calculate sessionTranscript)
        session.responseUri = responseUri
        runBlocking {
            storage.update(
                STORAGE_TABLE_NAME,
                "",
                sessionId,
                ByteString(session.toCbor())
            )
        }

    }

    private fun handleGetReaderRootCert(
        remoteHost: String,
        req: HttpServletRequest,
        resp: HttpServletResponse,
    ) {
        val readerCertPem = keyMaterial.readerRootKeyCertificates.certificates[0].toPem()
        resp.outputStream.write(readerCertPem.encodeToByteArray())
        resp.contentType = "text/plain"
        resp.status = HttpServletResponse.SC_OK
    }

    private fun handleOpenID4VPResponse(
        remoteHost: String,
        req: HttpServletRequest,
        resp: HttpServletResponse,
        requestData: ByteArray
    ) {
        val sessionId = req.getParameter("sessionId")
        if (sessionId == null) {
            Logger.e(TAG, "$remoteHost: No session parameter ${req.requestURI}")
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            return
        }
        val encodedSession = runBlocking {
            storage.get(
                STORAGE_TABLE_NAME,
                "",
                sessionId
            )
        }
        if (encodedSession == null) {
            Logger.e(TAG, "$remoteHost: No session for sessionId $sessionId")
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            return
        }
        val session = Session.fromCbor(encodedSession.toByteArray())

        val responseString = String(requestData, 0, requestData.size, Charsets.UTF_8)
        try {
            val kvPairs = mutableMapOf<String, String>()
            for (part in responseString.split("&")) {
                val parts = part.split("=", limit = 2)
                kvPairs[parts[0]] = parts[1]
            }

            val response = kvPairs["response"]
            val encryptedJWT = EncryptedJWT.parse(response)

            val encPub = session.encryptionKey.publicKey.javaPublicKey as ECPublicKey
            val encPriv = session.encryptionKey.javaPrivateKey as ECPrivateKey
            val encKey = ECKey(
                Curve.P_256,
                encPub,
                encPriv,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            )

            val decrypter = ECDHDecrypter(encKey)
            encryptedJWT.decrypt(decrypter)

            val vpToken = encryptedJWT.jwtClaimsSet.getClaim("vp_token") as String
            session.deviceResponse = vpToken.fromBase64()
            session.sessionTranscript = createSessionTranscriptOpenID4VP(
                clientId = clientId,
                responseUri = session.responseUri!!,
                authorizationRequestNonce = encryptedJWT.header.agreementPartyVInfo.toString(),
                mdocGeneratedNonce = encryptedJWT.header.agreementPartyUInfo.toString()
            )

            // Save `deviceResponse` and `sessionTranscript`, for later
            runBlocking {
                storage.update(
                    STORAGE_TABLE_NAME,
                    "",
                    sessionId,
                    ByteString(session.toCbor())
                )
            }

        } catch (e: Throwable) {
            Logger.w(TAG, "$remoteHost: handleResponse: Error getting response", e)
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            return
        }

        val redirectUri = baseUrl + "/verifier_redirect.html?sessionId=${session.id}"
        val json = Json { ignoreUnknownKeys = true }
        resp.outputStream.write(
            json.encodeToString(OpenID4VPRedirectUriResponse(redirectUri))
                .encodeToByteArray()
        )
        resp.contentType = "application/json"
        resp.status = HttpServletResponse.SC_OK
    }

    private fun handleOpenID4VPGetData(
        remoteHost: String,
        req: HttpServletRequest,
        resp: HttpServletResponse,
        requestData: ByteArray
    ) {
        val requestString = String(requestData, 0, requestData.size, Charsets.UTF_8)
        val request = Json.decodeFromString<OpenID4VPGetData>(requestString)

        val encodedSession = runBlocking {
            storage.get(
                STORAGE_TABLE_NAME,
                "",
                request.sessionId
            )
        }
        if (encodedSession == null) {
            Logger.e(TAG, "$remoteHost: No session for sessionId ${request.sessionId}")
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            return
        }
        val session = Session.fromCbor(encodedSession.toByteArray())

        val deviceResponse = try {
            val parser = DeviceResponseParser(session.deviceResponse!!, session.sessionTranscript!!)
            parser.parse()
        } catch (e: Throwable) {
            Logger.e(TAG, "$remoteHost: Error validating DeviceResponse", e)
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            return
        }

        // TODO: Add more sophistication in how we convey the result to the webpage, for example
        //  support the following value types
        //  - textual string
        //  - images
        //  - etc/
        //
        // TODO: Also check whether IssuerSigned and DeviceSigned validates and whether we trust
        //  the IACA certificate. Also include a check/fail for every data element to convey if
        //  the IssuerSignedItem digest matches the expected value.
        //
        val lines = mutableListOf<OpenID4VPResultLine>()
        for (document in deviceResponse.documents) {
            lines.add(OpenID4VPResultLine("DocType", document.docType))
            for (namespaceName in document.issuerNamespaces) {
                lines.add(OpenID4VPResultLine("NameSpace", namespaceName))
                for (dataElementName in document.getIssuerEntryNames(namespaceName)) {
                    val value = document.getIssuerEntryData(namespaceName, dataElementName)
                    val dataItem = Cbor.decode(value)
                    val renderedValue = Cbor.toDiagnostics(
                        dataItem,
                        setOf(
                            DiagnosticOption.PRETTY_PRINT,
                            DiagnosticOption.BSTR_PRINT_LENGTH
                        )
                    )
                    lines.add(OpenID4VPResultLine(dataElementName, renderedValue))
                }
            }
        }

        val json = Json { ignoreUnknownKeys = true }
        resp.outputStream.write(json.encodeToString(OpenID4VPResultData(lines)).encodeToByteArray())

        resp.contentType = "application/json"
        resp.status = HttpServletResponse.SC_OK
    }

}

// defined in ISO 18013-7 Annex B
private fun createSessionTranscriptOpenID4VP(
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

private data class MdocRequestNamespace(
    val name: String,
    val dataElements: List<String>
)

private data class MdocRequest(
    val docType: String,
    val namespaces: List<MdocRequestNamespace>
)

private val knownMdocRequests: Map<RequestType, MdocRequest> = mapOf(
    Pair(
        RequestType.PID_MDOC_AGE_OVER_18,
        MdocRequest(
            "eu.europa.ec.eudi.pid.1",
            listOf(
                MdocRequestNamespace(
                    "eu.europa.ec.eudi.pid.1",
                    listOf(
                        "age_over_18"
                    )
                )
            )
        )
    ),
    Pair(
        RequestType.PID_MDOC_MANDATORY,
        MdocRequest(
            "eu.europa.ec.eudi.pid.1",
            listOf(
                MdocRequestNamespace(
                    "eu.europa.ec.eudi.pid.1",
                    listOf(
                        "family_name",
                        "given_name",
                        "birth_date",
                        "age_over_18",
                        "issuance_date",
                        "expiry_date",
                        "issuing_authority",
                        "issuing_country"
                    )
                )
            )
        )
    ),
    Pair(
        RequestType.PID_MDOC_FULL,
        MdocRequest(
            "eu.europa.ec.eudi.pid.1",
            listOf(
                MdocRequestNamespace(
                    "eu.europa.ec.eudi.pid.1",
                    listOf(
                        "family_name",
                        "given_name",
                        "birth_date",
                        "age_over_18",
                        "age_in_years",
                        "age_birth_year",
                        "family_name_birth",
                        "given_name_birth",
                        "birth_place",
                        "birth_country",
                        "birth_state",
                        "birth_city",
                        "resident_address",
                        "resident_country",
                        "resident_state",
                        "resident_city",
                        "resident_postal_code",
                        "resident_street",
                        "resident_house_number",
                        "gender",
                        "nationality",
                        "issuance_date",
                        "expiry_date",
                        "issuing_authority",
                        "document_number",
                        "administrative_number",
                        "issuing_country",
                        "issuing_jurisdiction",
                    )
                )
            )
        )
    ),
    Pair(
        RequestType.MDL_MDOC_AGE_OVER_18,
        MdocRequest(
            "org.iso.18013.5.1.mDL",
            listOf(
                MdocRequestNamespace(
                    "org.iso.18013.5.1",
                    listOf(
                        "age_over_18"
                    )
                )
            )
        )
    ),
    Pair(
        RequestType.MDL_MDOC_AGE_OVER_21,
        MdocRequest(
            "org.iso.18013.5.1.mDL",
            listOf(
                MdocRequestNamespace(
                    "org.iso.18013.5.1",
                    listOf(
                        "age_over_21"
                    )
                )
            )
        )
    ),
    Pair(
        RequestType.MDL_MDOC_MANDATORY,
        MdocRequest(
            "org.iso.18013.5.1.mDL",
            listOf(
                MdocRequestNamespace(
                    "org.iso.18013.5.1",
                    listOf(
                        "family_name",
                        "given_name",
                        "birth_date",
                        "issue_date",
                        "expiry_date",
                        "issuing_country",
                        "issuing_authority",
                        "document_number",
                        "portrait",
                        "driving_privileges",
                        "un_distinguishing_sign",
                    )
                )
            )
        )
    ),
    Pair(
        RequestType.MDL_MDOC_FULL,
        MdocRequest(
            "org.iso.18013.5.1.mDL",
            listOf(
                MdocRequestNamespace(
                    "org.iso.18013.5.1",
                    listOf(
                        "family_name",
                        "given_name",
                        "birth_date",
                        "issue_date",
                        "expiry_date",
                        "issuing_country",
                        "issuing_authority",
                        "document_number",
                        "portrait",
                        "driving_privileges",
                        "un_distinguishing_sign",
                        "administrative_number",
                        "sex",
                        "height",
                        "weight",
                        "eye_colour",
                        "hair_colour",
                        "birth_place",
                        "resident_address",
                        "portrait_capture_date",
                        "age_in_years",
                        "age_birth_year",
                        "age_over_13",
                        "age_over_16",
                        "age_over_18",
                        "age_over_21",
                        "age_over_25",
                        "age_over_60",
                        "age_over_62",
                        "age_over_65",
                        "age_over_68",
                    )
                )
            )
        )
    ),
)

private fun mdocCalcPresentationDefinition(requestType: RequestType): JSONObject {
    val alg = JSONArray()
    alg.addAll(listOf("ES256"))
    val mso_mdoc = JSONObject()
    mso_mdoc.put("alg", alg)
    val format = JSONObject()
    format.put("mso_mdoc", mso_mdoc)

    val request = knownMdocRequests.get(requestType)
    if (request == null) {
        throw IllegalStateException("Unknown request type $requestType")
    }

    val fields = JSONArray()
    for (ns in request.namespaces) {
        for (de in ns.dataElements) {
            var array = JSONArray()
            array.add("\$['${ns.name}']['$de']")
            val field = JSONObject()
            field.put("path", array)
            field.put("intent_to_retain", false)
            fields.add(field)
        }
    }
    val constraints = JSONObject()
    constraints.put("limit_disclosure", "required")
    constraints.put("fields", fields)

    val input_descriptor_0 = JSONObject()
    input_descriptor_0.put("id", request.docType)
    input_descriptor_0.put("format", format)
    input_descriptor_0.put("constraints", constraints)
    val input_descriptors = JSONArray()
    input_descriptors.add(input_descriptor_0)

    val presentation_definition = JSONObject()
    presentation_definition.put("id", "request-TODO-id")
    presentation_definition.put("input_descriptors", input_descriptors)

    return presentation_definition
}

private fun calcClientMetadata(session: Session): JSONObject {
    val encPub = session.encryptionKey.publicKey as EcPublicKeyDoubleCoordinate

    val client_metadata = JSONObject()
    client_metadata.put("authorization_encrypted_response_alg", "ECDH-ES")
    client_metadata.put("authorization_encrypted_response_enc", "A128CBC-HS256")
    client_metadata.put("response_mode", "direct_post.jwt")

    val key = JSONObject()
    key.put("kty", "EC")
    key.put("use", "enc")
    key.put("crv", "P-256")
    key.put("alg", "ECDH-ES")
    key.put("x", encPub.x.toBase64())
    key.put("y", encPub.y.toBase64())

    val keys = JSONArray()
    keys.add(key)

    client_metadata.put("jwks", keys)

    return client_metadata
}