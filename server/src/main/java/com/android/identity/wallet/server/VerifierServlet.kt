package org.multipaz.wallet.server

import org.multipaz.asn1.ASN1Integer
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.CborMap
import org.multipaz.cbor.DiagnosticOption
import org.multipaz.cbor.Simple
import org.multipaz.cbor.Tstr
import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.EcPublicKeyDoubleCoordinate
import org.multipaz.crypto.JsonWebEncryption
import org.multipaz.crypto.JsonWebSignature
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509CertChain
import org.multipaz.crypto.javaPrivateKey
import org.multipaz.crypto.javaPublicKey
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.DocumentCannedRequest
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.documenttype.knowntypes.EUCertificateOfResidence
import org.multipaz.documenttype.knowntypes.EUPersonalID
import org.multipaz.documenttype.knowntypes.GermanPersonalID
import org.multipaz.documenttype.knowntypes.PhotoID
import org.multipaz.documenttype.knowntypes.UtopiaMovieTicket
import org.multipaz.documenttype.knowntypes.UtopiaNaturalization
import org.multipaz.flow.handler.FlowNotifications
import org.multipaz.flow.server.Configuration
import org.multipaz.flow.server.FlowEnvironment
import org.multipaz.flow.server.getTable
import org.multipaz.mdoc.request.DeviceRequestGenerator
import org.multipaz.mdoc.response.DeviceResponseParser
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.sdjwt.presentation.SdJwtVerifiablePresentation
import org.multipaz.sdjwt.vc.JwtBody
import org.multipaz.server.BaseHttpServlet
import org.multipaz.storage.StorageTable
import org.multipaz.storage.StorageTableSpec
import org.multipaz.util.Logger
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
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
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import net.minidev.json.JSONArray
import net.minidev.json.JSONObject
import net.minidev.json.JSONStyle
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URLEncoder
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

private const val TAG = "VerifierServlet"

enum class Protocol {
    W3C_DC_PREVIEW,
    W3C_DC_ARF,
    W3C_DC_MDOC_API,
    W3C_DC_OPENID4VP,
    PLAIN_OPENID4VP,
    EUDI_OPENID4VP,
    MDOC_OPENID4VP,
    CUSTOM_OPENID4VP,
}

@Serializable
private data class OpenID4VPBeginRequest(
    val format: String,
    val docType: String,
    val requestId: String,
    val protocol: String,
    val origin: String,
    val scheme: String
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
    val requestFormat: String,      // "mdoc" or "vc"
    val requestDocType: String,     // mdoc DocType or VC vct
    val requestId: String,          // DocumentWellKnownRequest.id
    val protocol: Protocol,
    val nonce: ByteString,
    val origin: String,
    val encryptionKey: EcPrivateKey,
    val signRequest: Boolean = true,
    val encryptResponse: Boolean = true,
    var responseUri: String? = null,
    var deviceResponse: ByteArray? = null,
    var verifiablePresentation: String? = null,
    var sessionTranscript: ByteArray? = null,
    var responseWasEncrypted: Boolean = false,
) {
    companion object
}

@Serializable
private data class AvailableRequests(
    val documentTypesWithRequests: List<DocumentTypeWithRequests>
)

@Serializable
private data class DocumentTypeWithRequests(
    val documentDisplayName: String,
    val mdocDocType: String?,
    val vcVct: String?,
    val sampleRequests: List<SampleRequest>
)

@Serializable
private data class SampleRequest(
    val id: String,
    val displayName: String,
    val supportsMdoc: Boolean,
    val supportsVc: Boolean
)

@Serializable
private data class DCBeginRequest(
    val format: String,
    val docType: String,
    val requestId: String,
    val protocol: String,
    val origin: String,
    val signRequest: Boolean,
    val encryptResponse: Boolean,
)

@Serializable
private data class DCBeginResponse(
    val sessionId: String,
    val dcRequestString: String
)

@Serializable
private data class DCGetDataRequest(
    val sessionId: String,
    val credentialResponse: String
)

@Serializable
private data class DCPreviewResponse(
    val token: String
)

@Serializable
private data class DCArfResponse(
    val encryptedResponse: String
)

/**
 * Verifier servlet (may trigger warning as unused in the code).
 *
 * This is using the configuration and storage interfaces from
 * [org.multipaz.server.ServerEnvironment].
 */
class VerifierServlet : BaseHttpServlet() {

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

                // Create Reader Root w/ self-signed certificate.
                //
                // TODO: Migrate to Curve P-384 once we migrate off com.nimbusds.* which
                // only supports Curve P-256.
                //
                val readerRootKey = Crypto.createEcPrivateKey(EcCurve.P256)
                val readerRootKeySignatureAlgorithm = Algorithm.ES256
                val readerRootKeySubject = "CN=OWF IC Online Verifier Reader Root Key"
                val readerRootKeyCertificate = MdocUtil.generateReaderRootCertificate(
                    readerRootKey = readerRootKey,
                    subject = X500Name.fromName(readerRootKeySubject),
                    serial = ASN1Integer(1L),
                    validFrom = validFrom,
                    validUntil = validUntil
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
        val SESSION_EXPIRATION_INTERVAL = 1.days

        private val verifierSessionTableSpec = StorageTableSpec(
            name = "VerifierSessions",
            supportPartitions = false,
            supportExpiration = true
        )

        private val verifierRootStateTableSpec = StorageTableSpec(
            name = "VerifierRootState",
            supportPartitions = false,
            supportExpiration = false
        )

        private lateinit var keyMaterial: KeyMaterial
        private lateinit var configuration: Configuration
        private lateinit var verifierSessionTable: StorageTable
        private lateinit var verifierRootStateTable: StorageTable

        private fun createKeyMaterial(serverEnvironment: FlowEnvironment): KeyMaterial {
            val keyMaterialBlob = runBlocking {
                verifierRootStateTable = serverEnvironment.getTable(verifierRootStateTableSpec)
                verifierSessionTable = serverEnvironment.getTable(verifierSessionTableSpec)
                verifierRootStateTable.get("verifierKeyMaterial")?.toByteArray()
                    ?: let {
                        val blob = KeyMaterial.createKeyMaterial().toCbor()
                        verifierRootStateTable.insert(
                            key = "verifierKeyMaterial",
                            data = ByteString(blob),
                        )
                        blob
                    }
            }
            return KeyMaterial.fromCbor(keyMaterialBlob)
        }

        private val documentTypeRepo: DocumentTypeRepository by lazy {
            val repo =  DocumentTypeRepository()
            repo.addDocumentType(DrivingLicense.getDocumentType())
            repo.addDocumentType(EUPersonalID.getDocumentType())
            repo.addDocumentType(GermanPersonalID.getDocumentType())
            repo.addDocumentType(PhotoID.getDocumentType())
            repo.addDocumentType(EUCertificateOfResidence.getDocumentType())
            repo.addDocumentType(UtopiaNaturalization.getDocumentType())
            repo.addDocumentType(UtopiaMovieTicket.getDocumentType())
            repo
        }
    }

    override fun initializeEnvironment(env: FlowEnvironment): FlowNotifications? {
        configuration = env.getInterface(Configuration::class)!!
        keyMaterial = createKeyMaterial(env)
        return null
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
            // Remove the http:// or https:// from the baseUrl.
            val startIndex = baseUrl.findAnyOf(listOf("://"))?.first
            ret = if (startIndex == null) baseUrl else baseUrl.removeRange(0, startIndex+3)
        }
        "x509_san_dns:$ret"
    }

    private fun createSingleUseReaderKey(): Pair<EcPrivateKey, X509CertChain> {
        val now = Clock.System.now()
        val validFrom = now.plus(DateTimePeriod(minutes = -10), TimeZone.currentSystemDefault())
        val validUntil = now.plus(DateTimePeriod(minutes = 10), TimeZone.currentSystemDefault())
        val readerKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val readerKeySubject = "CN=OWF IC Online Verifier Single-Use Reader Key"

        // TODO: for now, instead of using the per-site Reader Root generated at first run, use the
        //  well-know OWF IC Reader root checked into Git.
        val owfIcReaderRootKeyPub = EcPublicKey.fromPem(
            """
                    -----BEGIN PUBLIC KEY-----
                    MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE+QDye70m2O0llPXMjVjxVZz3m5k6agT+
                    wih+L79b7jyqUl99sbeUnpxaLD+cmB3HK3twkA7fmVJSobBc+9CDhkh3mx6n+YoH
                    5RulaSWThWBfMyRjsfVODkosHLCDnbPV
                    -----END PUBLIC KEY-----
                """.trimIndent().trim(),
            EcCurve.P384
        )
        val owfIcReaderRootKey = EcPrivateKey.fromPem(
            """
                    -----BEGIN PRIVATE KEY-----
                    MIG2AgEAMBAGByqGSM49AgEGBSuBBAAiBIGeMIGbAgEBBDCcRuzXW3pW2h9W8pu5
                    /CSR6JSnfnZVATq+408WPoNC3LzXqJEQSMzPsI9U1q+wZ2yhZANiAAT5APJ7vSbY
                    7SWU9cyNWPFVnPebmTpqBP7CKH4vv1vuPKpSX32xt5SenFosP5yYHccre3CQDt+Z
                    UlKhsFz70IOGSHebHqf5igflG6VpJZOFYF8zJGOx9U4OSiwcsIOds9U=
                    -----END PRIVATE KEY-----
                """.trimIndent().trim(),
            owfIcReaderRootKeyPub
        )
        val certsValidFrom = LocalDate.parse("2024-12-01").atStartOfDayIn(TimeZone.UTC)
        val certsValidUntil = LocalDate.parse("2034-12-01").atStartOfDayIn(TimeZone.UTC)
        val owfIcReaderRootCert = MdocUtil.generateReaderRootCertificate(
            readerRootKey = owfIcReaderRootKey,
            subject = X500Name.fromName("CN=OWF IC TestApp Reader Root"),
            serial = ASN1Integer(1L),
            validFrom = certsValidFrom,
            validUntil = certsValidUntil,
        )

        val readerKeyCertificate = MdocUtil.generateReaderCertificate(
            readerRootCert = owfIcReaderRootCert,
            readerRootKey = owfIcReaderRootKey,
            readerKey = readerKey.publicKey,
            subject = X500Name.fromName(readerKeySubject),
            serial = ASN1Integer(1L),
            validFrom = validFrom,
            validUntil = validUntil
        )
        return Pair(
            readerKey,
            X509CertChain(listOf(readerKeyCertificate) + owfIcReaderRootCert)
        )
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val remoteHost = getRemoteHost(req)
        Logger.i(TAG, "$remoteHost: POST ${req.requestURI}")

        val requestLength = req.contentLength
        val requestData = req.inputStream.readNBytes(requestLength)

        if (req.requestURI.endsWith("verifier/getAvailableRequests")) {
            handleGetAvailableRequests(remoteHost, req, resp, requestData)
        } else if (req.requestURI.endsWith("verifier/openid4vpBegin")) {
            handleOpenID4VPBegin(remoteHost, req, resp, requestData)
        } else if (req.requestURI.endsWith("verifier/openid4vpGetData")) {
            handleOpenID4VPGetData(remoteHost, req, resp, requestData)
        } else if (req.requestURI.endsWith("verifier/openid4vpResponse")) {
            return handleOpenID4VPResponse(remoteHost, req, resp, requestData)
        } else if (req.requestURI.endsWith("verifier/dcBegin")) {
            handleDcBegin(remoteHost, req, resp, requestData)
        } else if (req.requestURI.endsWith("verifier/dcGetData")) {
            handleDcGetData(remoteHost, req, resp, requestData)
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

    private fun handleGetAvailableRequests(
        remoteHost: String,
        req: HttpServletRequest,
        resp: HttpServletResponse,
        requestData: ByteArray
    ) {
        val requests = mutableListOf<DocumentTypeWithRequests>()
        for (dt in documentTypeRepo.documentTypes) {
            if (!dt.cannedRequests.isEmpty()) {
                val sampleRequests = mutableListOf<SampleRequest>()
                var dtSupportsMdoc = false
                var dtSupportsVc = false
                for (sr in dt.cannedRequests) {
                    sampleRequests.add(SampleRequest(
                        sr.id,
                        sr.displayName,
                        sr.mdocRequest != null,
                        sr.vcRequest != null,
                    ))
                    if (sr.mdocRequest != null) {
                        dtSupportsMdoc = true
                    }
                    if (sr.vcRequest != null) {
                        dtSupportsVc = true
                    }
                }
                requests.add(DocumentTypeWithRequests(
                    dt.displayName,
                    if (dtSupportsMdoc) dt.mdocDocumentType!!.docType else null,
                    if (dtSupportsVc) dt.vcDocumentType!!.type else null,
                    sampleRequests
                ))
            }
        }

        val json = Json { ignoreUnknownKeys = true }
        val responseString = json.encodeToString(AvailableRequests(requests))
        resp.status = HttpServletResponse.SC_OK
        resp.outputStream.write(responseString.encodeToByteArray())
        resp.contentType = "application/json"
    }

    private fun lookupWellknownRequest(
        format: String,
        docType: String,
        requestId: String
    ): DocumentCannedRequest {
        return when (format) {
            "mdoc" -> documentTypeRepo.getDocumentTypeForMdoc(docType)!!.cannedRequests.first { it.id == requestId}
            "vc" -> documentTypeRepo.getDocumentTypeForVc(docType)!!.cannedRequests.first { it.id == requestId}
            else -> throw IllegalArgumentException("Unknown format $format")
        }
    }

    private fun handleDcBegin(
        remoteHost: String,
        req: HttpServletRequest,
        resp: HttpServletResponse,
        requestData: ByteArray
    ) {
        val requestString = String(requestData, 0, requestData.size, Charsets.UTF_8)
        val request = Json.decodeFromString<DCBeginRequest>(requestString)
        Logger.i(TAG, "format ${request.format} protocol ${request.protocol}")

        val protocol = when (request.protocol) {
            // Keep in sync with verifier.html
            "w3c_dc_preview" -> Protocol.W3C_DC_PREVIEW
            "w3c_dc_arf" -> Protocol.W3C_DC_ARF
            "w3c_dc_mdoc_api" -> Protocol.W3C_DC_MDOC_API
            "w3c_dc_openid4vp" -> Protocol.W3C_DC_OPENID4VP
            "openid4vp_plain" -> Protocol.PLAIN_OPENID4VP
            "openid4vp_eudi" -> Protocol.EUDI_OPENID4VP
            "openid4vp_mdoc" -> Protocol.MDOC_OPENID4VP
            "openid4vp_custom" -> Protocol.CUSTOM_OPENID4VP
            else -> {
                Logger.w(TAG, "$remoteHost: Unknown protocol '$request.protocol'")
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                return
            }
        }

        // Create a new session
        val session = Session(
            nonce = ByteString(Random.Default.nextBytes(16)),
            origin = request.origin,
            encryptionKey = Crypto.createEcPrivateKey(EcCurve.P256),
            requestFormat = request.format,
            requestDocType = request.docType,
            requestId = request.requestId,
            protocol = protocol,
            signRequest = request.signRequest,
            encryptResponse = request.encryptResponse,
        )
        val sessionId = runBlocking {
            verifierSessionTable.insert(
                key = null,
                data = ByteString(session.toCbor()),
                expiration = Clock.System.now() + SESSION_EXPIRATION_INTERVAL
            )
        }

        val (readerAuthKey, readerAuthKeyCertification) = createSingleUseReaderKey()

        // Uncomment when making test vectors...
        //Logger.iCbor(TAG, "readerKey: ", Cbor.encode(session.encryptionKey.toCoseKey().toDataItem()))

        val dcRequestString = mdocCalcDcRequestString(
            documentTypeRepo,
            request.format,
            session,
            lookupWellknownRequest(session.requestFormat, session.requestDocType, session.requestId),
            session.protocol,
            session.nonce,
            session.origin,
            session.encryptionKey,
            session.encryptionKey.publicKey as EcPublicKeyDoubleCoordinate,
            readerAuthKey,
            readerAuthKeyCertification,
            request.signRequest,
            request.encryptResponse,
        )
        Logger.i(TAG, "dcRequestString: $dcRequestString")
        val json = Json { ignoreUnknownKeys = true }
        val responseString = json.encodeToString(DCBeginResponse(sessionId, dcRequestString))
        resp.status = HttpServletResponse.SC_OK
        resp.outputStream.write(responseString.encodeToByteArray())
        resp.contentType = "application/json"
    }

    private fun handleDcGetData(
        remoteHost: String,
        req: HttpServletRequest,
        resp: HttpServletResponse,
        requestData: ByteArray
    ) {
        val requestString = String(requestData, 0, requestData.size, Charsets.UTF_8)
        val request = Json.decodeFromString<DCGetDataRequest>(requestString)

        val encodedSession = runBlocking {
            verifierSessionTable.get(request.sessionId)
        }
        if (encodedSession == null) {
            Logger.e(TAG, "$remoteHost: No session for sessionId ${request.sessionId}")
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            return
        }
        val session = Session.fromCbor(encodedSession.toByteArray())

        Logger.i(TAG, "Data received from WC3 DC API: ${request.credentialResponse}")

        try {
            when (session.protocol) {
                Protocol.W3C_DC_PREVIEW ->handleDcGetDataPreview(session, request.credentialResponse)
                Protocol.W3C_DC_ARF -> handleDcGetDataArf(session, request.credentialResponse)
                Protocol.W3C_DC_MDOC_API -> handleDcGetDataMdocApi(session, request.credentialResponse)
                Protocol.W3C_DC_OPENID4VP -> handleDcGetDataOpenID4VP(session, request.credentialResponse)
                else -> throw IllegalArgumentException("unsupported protocol ${session.protocol}")
            }
        } catch (e: Throwable) {
            Logger.e(TAG, "$remoteHost: failed with", e)
            e.printStackTrace()
            resp.status = HttpServletResponse.SC_BAD_REQUEST
        }

        try {
            when (session.requestFormat) {
                "mdoc" -> {
                    handleGetDataMdoc(session, resp)
                }
                "vc" -> {
                    val clientIdToUse = if (session.signRequest) {
                        "x509_san_uri:${session.origin}/server/verifier.html"
                    } else {
                        "web-origin:${session.origin}"
                    }
                    handleGetDataSdJwt(session, resp, clientIdToUse)
                }
            }
        } catch (e: Throwable) {
            Logger.e(TAG, "$remoteHost: Error validating DeviceResponse", e)
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            return
        }

        resp.contentType = "application/json"
        resp.status = HttpServletResponse.SC_OK
    }

    private fun handleDcGetDataPreview(
        session: Session,
        credentialResponse: String
    ) {
        val tokenBase64 = Json.decodeFromString<DCPreviewResponse>(credentialResponse).token

        val (cipherText, encapsulatedPublicKey) = parseCredentialDocument(tokenBase64.fromBase64Url())
        val uncompressed = (session.encryptionKey.publicKey as EcPublicKeyDoubleCoordinate).asUncompressedPointEncoding
        session.sessionTranscript = generateBrowserSessionTranscript(
            session.nonce,
            session.origin,
            Crypto.digest(Algorithm.SHA256, uncompressed)
        )
        session.responseWasEncrypted = true
        session.deviceResponse = Crypto.hpkeDecrypt(
            Algorithm.HPKE_BASE_P256_SHA256_AES128GCM,
            session.encryptionKey,
            cipherText,
            session.sessionTranscript!!,
            encapsulatedPublicKey)
    }

    private fun handleDcGetDataArf(
        session: Session,
        credentialResponse: String
    ) {
        val encryptedResponseBase64 = Json.decodeFromString<DCArfResponse>(credentialResponse).encryptedResponse

        val array = Cbor.decode(encryptedResponseBase64.fromBase64Url()).asArray
        if (array.get(0).asTstr != "ARFencryptionv2") {
            throw IllegalArgumentException("Excepted ARFencryptionv2 as first array element")
        }
        val encryptionParameters = array.get(1).asMap
        val encapsulatedPublicKey = encryptionParameters[Tstr("pkEM")]!!.asCoseKey.ecPublicKey
        val cipherText = encryptionParameters[Tstr("cipherText")]!!.asBstr

        val arfEncryptionInfo = CborMap.builder()
            .put("nonce", session.nonce.toByteArray())
            .put("readerPublicKey", session.encryptionKey.publicKey.toCoseKey().toDataItem())
            .end()
            .build()
        val encryptionInfo = CborArray.builder()
            .add("ARFEncryptionv2")
            .add(arfEncryptionInfo)
            .end()
            .build()
        val base64EncryptionInfo = Cbor.encode(encryptionInfo).toBase64Url()

        session.sessionTranscript =
            Cbor.encode(
                CborArray.builder()
                    .add(Simple.NULL) // DeviceEngagementBytes
                    .add(Simple.NULL) // EReaderKeyBytes
                    .addArray() // BrowserHandover
                    .add("ARFHandoverv2")
                    .add(base64EncryptionInfo)
                    .add(session.origin)
                    .end()
                    .end()
                    .build()
            )

        session.responseWasEncrypted = true
        session.deviceResponse = Crypto.hpkeDecrypt(
            Algorithm.HPKE_BASE_P256_SHA256_AES128GCM,
            session.encryptionKey,
            cipherText,
            session.sessionTranscript!!,
            encapsulatedPublicKey)

        Logger.iCbor(TAG, "decrypted DeviceResponse", session.deviceResponse!!)
        Logger.iCbor(TAG, "SessionTranscript", session.sessionTranscript!!)
    }

    private fun handleDcGetDataMdocApi(
        session: Session,
        credentialResponse: String
    ) {
        val response = Json.parseToJsonElement(credentialResponse).jsonObject
        val encryptedResponseBase64 = response["Response"]!!.jsonPrimitive.content

        val array = Cbor.decode(encryptedResponseBase64.fromBase64Url()).asArray
        if (array.get(0).asTstr != "dcapi") {
            throw IllegalArgumentException("Excepted dcapi as first array element")
        }
        val encryptionParameters = array.get(1).asMap
        val enc = encryptionParameters[Tstr("enc")]!!.asBstr
        val encapsulatedPublicKey = EcPublicKeyDoubleCoordinate.fromUncompressedPointEncoding(
            EcCurve.P256,
            enc
        )
        val cipherText = encryptionParameters[Tstr("cipherText")]!!.asBstr

        val arfEncryptionInfo = CborMap.builder()
            .put("nonce", session.nonce.toByteArray())
            .put("recipientPublicKey", session.encryptionKey.publicKey.toCoseKey().toDataItem())
            .end()
            .build()
        val encryptionInfo = CborArray.builder()
            .add("dcapi")
            .add(arfEncryptionInfo)
            .end()
            .build()
        val base64EncryptionInfo = Cbor.encode(encryptionInfo).toBase64Url()

        val dcapiInfo = CborArray.builder()
            .add(base64EncryptionInfo)
            .add(session.origin)
            .end()
            .build()

        session.sessionTranscript = Cbor.encode(
            CborArray.builder()
                .add(Simple.NULL) // DeviceEngagementBytes
                .add(Simple.NULL) // EReaderKeyBytes
                .addArray() // BrowserHandover
                .add("dcapi")
                .add(Crypto.digest(Algorithm.SHA256, Cbor.encode(dcapiInfo)))
                .end()
                .end()
                .build()
        )

        session.responseWasEncrypted = true
        session.deviceResponse = Crypto.hpkeDecrypt(
            Algorithm.HPKE_BASE_P256_SHA256_AES128GCM,
            session.encryptionKey,
            cipherText,
            session.sessionTranscript!!,
            encapsulatedPublicKey)

        Logger.iCbor(TAG, "decrypted DeviceResponse", session.deviceResponse!!)
        Logger.iCbor(TAG, "SessionTranscript", session.sessionTranscript!!)
    }

    private fun handleDcGetDataOpenID4VP(
        session: Session,
        credentialResponse: String
    ) {
        val response = Json.parseToJsonElement(credentialResponse).jsonObject

        val encryptedResponse = response["response"]
        val vpToken = if (encryptedResponse != null) {
            session.responseWasEncrypted = true
            val decryptedResponse = JsonWebEncryption.decrypt(encryptedResponse, session.encryptionKey).jsonObject
            decryptedResponse["vp_token"]!!.jsonObject
        } else {
            response["vp_token"]!!.jsonObject
        }
        Logger.i(TAG, "vp_token: ${vpToken.toString()}")
        val vpTokenForCred = vpToken["cred1"]!!.jsonPrimitive.content

        when (session.requestFormat) {
            "mdoc" -> {
                val effectiveClientId = if (session.signRequest) {
                    "x509_san_uri:${session.origin}/server/verifier.html"
                } else {
                    "web-origin:${session.origin}"
                }
                val handoverInfo = Cbor.encode(
                    CborArray.builder()
                        .add(session.origin)
                        .add(effectiveClientId)
                        .add(session.nonce.toByteArray().toBase64Url())
                        .end()
                        .build()
                )
                session.sessionTranscript = Cbor.encode(
                    CborArray.builder()
                        .add(Simple.NULL) // DeviceEngagementBytes
                        .add(Simple.NULL) // EReaderKeyBytes
                        .addArray()
                        .add("OpenID4VPDCAPIHandover")
                        .add(Crypto.digest(Algorithm.SHA256, handoverInfo))
                        .end()
                        .end()
                        .build()
                )
                Logger.iCbor(TAG, "handoverInfo", handoverInfo)
                Logger.iCbor(TAG, "sessionTranscript", session.sessionTranscript!!)
                session.deviceResponse = vpTokenForCred.fromBase64Url()
            }
            "vc" -> {
                session.verifiablePresentation = vpTokenForCred
            }
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
            "w3c_dc_preview" -> Protocol.W3C_DC_PREVIEW
            "w3c_dc_arf" -> Protocol.W3C_DC_ARF
            "w3c_dc_mdoc_api" -> Protocol.W3C_DC_MDOC_API
            "w3c_dc_openid4vp" -> Protocol.W3C_DC_OPENID4VP
            "openid4vp_plain" -> Protocol.PLAIN_OPENID4VP
            "openid4vp_eudi" -> Protocol.EUDI_OPENID4VP
            "openid4vp_mdoc" -> Protocol.MDOC_OPENID4VP
            "openid4vp_custom" -> Protocol.CUSTOM_OPENID4VP
            else -> {
                Logger.w(TAG, "$remoteHost: Unknown protocol '$request.protocol'")
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                return
            }
        }

        // Create a new session
        val session = Session(
            nonce = ByteString(Random.Default.nextBytes(16)),
            origin = request.origin,
            encryptionKey = Crypto.createEcPrivateKey(EcCurve.P256),
            requestFormat = request.format,
            requestDocType = request.docType,
            requestId = request.requestId,
            protocol = protocol
        )
        val sessionId = runBlocking {
            verifierSessionTable.insert(
                key = null,
                data = ByteString(session.toCbor()),
                expiration = Clock.System.now() + SESSION_EXPIRATION_INTERVAL
            )
        }

        val uriScheme = when (session.protocol) {
            Protocol.PLAIN_OPENID4VP -> "openid4vp://"
            Protocol.EUDI_OPENID4VP -> "eudi-openid4vp://"
            Protocol.MDOC_OPENID4VP -> "mdoc-openid4vp://"
            Protocol.CUSTOM_OPENID4VP -> request.scheme
            else -> {
                Logger.w(TAG, "$remoteHost: Unknown protocol '${session.protocol}'")
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                return
            }
        }
        val requestUri = baseUrl + "/verifier/openid4vpRequest?sessionId=${sessionId}"
        val uri = uriScheme +
                "?client_id=" + URLEncoder.encode(clientId, Charsets.UTF_8) +
                "&request_uri=" + URLEncoder.encode(requestUri, Charsets.UTF_8)

        val json = Json { ignoreUnknownKeys = true }
        val responseString = json.encodeToString(OpenID4VPBeginResponse(uri))
        resp.status = HttpServletResponse.SC_OK
        resp.outputStream.write(responseString.encodeToByteArray())
        resp.contentType = "application/json"
        Logger.i(TAG, "Sending handleOpenID4VPBegin response: $responseString")
    }

    @OptIn(ExperimentalEncodingApi::class)
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
            verifierSessionTable.get(sessionId)
        }
        if (encodedSession == null) {
            Logger.e(TAG, "$remoteHost: No session for sessionId $sessionId")
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            return
        }
        val session = Session.fromCbor(encodedSession.toByteArray())

        val responseUri = baseUrl + "/verifier/openid4vpResponse?sessionId=${sessionId}"

        val (singleUseReaderKeyPriv, singleUseReaderKeyCertChain) = createSingleUseReaderKey()

        val readerPublic = singleUseReaderKeyPriv.publicKey.javaPublicKey as ECPublicKey
        val readerPrivate = singleUseReaderKeyPriv.javaPrivateKey as ECPrivateKey

        // TODO: b/393388152: ECKey is deprecated, but might be current library dependency.
        @Suppress("DEPRECATION")
        val readerKey = ECKey(
            Curve.P_256,
            readerPublic,
            readerPrivate,
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
            Base64.from(kotlin.io.encoding.Base64.Default.encode(cert.encodedCertificate))
        }

        val request = lookupWellknownRequest(session.requestFormat, session.requestDocType, session.requestId)
        val presentationDefinition = when (session.requestFormat) {
            "mdoc" -> mdocCalcPresentationDefinition(documentTypeRepo, request)
            "vc" -> sdjwtCalcPresentationDefinition(documentTypeRepo, request)
            else -> throw IllegalArgumentException("Unknown format ${session.requestFormat}")
        }

        val claimsSet = JWTClaimsSet.Builder()
            .claim("client_id", clientId)
            .claim("client_id_scheme", "x509_san_dns")
            .claim("response_uri", responseUri)
            .claim("response_type", "vp_token")
            .claim("response_mode", "direct_post.jwt")
            .claim("nonce", session.nonce.toByteArray().toBase64Url())
            .claim("state", sessionId)
            .claim("presentation_definition", presentationDefinition)
            .claim("client_metadata", calcClientMetadata(session, session.requestFormat))
            .build()
        Logger.i(TAG, "Sending OpenID4VPRequest claims set: $claimsSet")

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
        Logger.i(TAG, "Signed JWT: $s")
        resp.contentType = "application/oauth-authz-req+jwt"
        resp.outputStream.write(s.encodeToByteArray())
        resp.status = HttpServletResponse.SC_OK

        // We'll need responseUri later (to calculate sessionTranscript)
        session.responseUri = responseUri
        runBlocking {
            verifierSessionTable.update(
                key = sessionId,
                data = ByteString(session.toCbor()),
                expiration = Clock.System.now() + SESSION_EXPIRATION_INTERVAL
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
            verifierSessionTable.get(sessionId)
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

            val encPublic = session.encryptionKey.publicKey.javaPublicKey as ECPublicKey
            val encPrivate = session.encryptionKey.javaPrivateKey as ECPrivateKey

            // TODO: b/393388152: ECKey is deprecated, but might be current library dependency.
            @Suppress("DEPRECATION")
            val encKey = ECKey(
                Curve.P_256,
                encPublic,
                encPrivate,
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
            session.responseWasEncrypted = true
            if (session.requestFormat == "mdoc") {
                session.deviceResponse = vpToken.fromBase64Url()
            } else {
                session.verifiablePresentation = vpToken
            }

            // According to ISO 23220-4, the mdoc profile is required to have the apv and apu params
            // set in the JWE header. However, there is no such requirement for the sd-jwt profile.
            val apv = encryptedJWT.header.agreementPartyVInfo
            val apu = encryptedJWT.header.agreementPartyUInfo
            if (session.requestFormat == "mdoc") {
                if ((apu == null) or (apv == null)) {
                    // Log a warning here instead of throwing an error since apu + apv are not req
                    // for functionality.
                    Logger.w(TAG, "Mdoc wallet did not provide both apu and apv JWE headers as expected.")
                }
            }
            session.sessionTranscript = createSessionTranscriptOpenID4VP(
                clientId = clientId,
                responseUri = session.responseUri!!,
                authorizationRequestNonce = apv?.toString(),
                mdocGeneratedNonce = apu?.toString()
            )

            // Save `deviceResponse` and `sessionTranscript`, for later
            runBlocking {
                verifierSessionTable.update(
                    key = sessionId,
                    data = ByteString(session.toCbor()),
                    expiration = Clock.System.now() + SESSION_EXPIRATION_INTERVAL
                )
            }

        } catch (e: Throwable) {
            Logger.w(TAG, "$remoteHost: handleResponse: Error getting response", e)
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            return
        }

        val redirectUri = baseUrl + "/verifier_redirect.html?sessionId=${sessionId}"
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
            verifierSessionTable.get(request.sessionId)
        }
        if (encodedSession == null) {
            Logger.e(TAG, "$remoteHost: No session for sessionId ${request.sessionId}")
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            return
        }
        val session = Session.fromCbor(encodedSession.toByteArray())

        try {
            when (session.requestFormat) {
                "mdoc" -> handleGetDataMdoc(session, resp)
                "vc" -> handleGetDataSdJwt(session, resp, clientId)
            }
        } catch (e: Throwable) {
            Logger.e(TAG, "$remoteHost: Error validating DeviceResponse", e)
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            return
        }

        resp.contentType = "application/json"
        resp.status = HttpServletResponse.SC_OK
    }

    private fun handleGetDataMdoc(
        session: Session,
        resp: HttpServletResponse
    ) {
        val parser = DeviceResponseParser(session.deviceResponse!!, session.sessionTranscript!!)
        val deviceResponse = parser.parse()
        Logger.i(TAG, "Validated DeviceResponse!")

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
            lines.add(OpenID4VPResultLine("Response end-to-end encrypted", "${session.responseWasEncrypted}"))
            lines.add(OpenID4VPResultLine("DeviceSigned Authenticated", "${document.deviceSignedAuthenticated}"))
            lines.add(OpenID4VPResultLine("IssuerSigned Authenticated", "${document.issuerSignedAuthenticated}"))
            lines.add(OpenID4VPResultLine("Number of Digest Failures", "${document.numIssuerEntryDigestMatchFailures}"))
        }

        val json = Json { ignoreUnknownKeys = true }
        resp.outputStream.write(json.encodeToString(OpenID4VPResultData(lines)).encodeToByteArray())
    }

    private fun handleGetDataSdJwt(
        session: Session,
        resp: HttpServletResponse,
        clientIdToUse: String,
    ) {
        val presentationString = session.verifiablePresentation!!
        Logger.d(TAG, "Handling SD-JWT: $presentationString")
        val presentation = SdJwtVerifiablePresentation.fromString(presentationString)

        val lines = mutableListOf<OpenID4VPResultLine>()

        for (disclosure in presentation.sdJwtVc.disclosures.sortedBy { it.key }) {
            val valueToAdd = when (disclosure.value) {
                is JsonPrimitive -> disclosure.value.jsonPrimitive.content
                is JsonArray -> disclosure.value.jsonArray.toString()
                else -> "Unknown Response Type: ${disclosure.value}"
            }
            lines.add(OpenID4VPResultLine(disclosure.key, valueToAdd))
        }

        // Check for the actual claims we requested, in addition to what was supplied
        // in the response.
        val disclosedClaims = presentation.sdJwtVc.disclosures.map { it.key }.toMutableSet()
        // There are several special cases that aren't in the selective disclosures, which our
        // JwtBody implementation copies into its properties:
        val jwtBody = JwtBody.fromString(presentation.sdJwtVc.body)
        val specialCases: Map<String, String?> = mapOf(
            Pair("iss", jwtBody.issuer),
            Pair("vct", jwtBody.docType),
            Pair("iat", jwtBody.timeSigned?.toString()),
            Pair("nbf", jwtBody.timeValidityBegin?.toString()),
            Pair("exp", jwtBody.timeValidityEnd?.toString()),
            Pair("cnf", jwtBody.publicKey?.asJwk.toString())
        )
        for (key in specialCases.keys) {
            val value = specialCases[key] ?: continue
            lines.add(OpenID4VPResultLine(key, value))
            disclosedClaims.add(key)
            Logger.i(TAG, "Adding special case $key: $value")
        }

        // on the verifier, check that the key binding can be verified with the
        // key mentioned in the SD-JWT:
        Logger.i(TAG, "using client_id ${clientIdToUse}")
        try {
            if (presentation.verifyKeyBinding(
                    checkAudience = { clientIdToUse == it },
                    checkNonce = {
                        session.nonce.toByteArray().toBase64Url() == it
                    },
                    checkCreationTime = { true /* TODO: sometimes flaky it < Clock.System.now() */ }
                )) {
                lines.add(OpenID4VPResultLine("Key-binding", "Verified"))
            } else {
                lines.add(OpenID4VPResultLine("Key-binding", "Not Key-bound"))
            }
        } catch (e: Throwable) {
            lines.add(OpenID4VPResultLine("Key-binding", "Verification failed: ${e.message}"))
        }

        // also on the verifier, check the signature over the SD-JWT from the issuer
        // TODO: We need to verify the issuer signature. Where do we get the public
        // key of the issuer?
        //presentation.sdJwtVc.verifyIssuerSignature(issuerCert.ecPublicKey)

        val json = Json { ignoreUnknownKeys = true }
        resp.outputStream.write(json.encodeToString(OpenID4VPResultData(lines)).encodeToByteArray())
    }
}

// defined in ISO 18013-7 Annex B
private fun createSessionTranscriptOpenID4VP(
    clientId: String,
    responseUri: String,
    authorizationRequestNonce: String?,
    mdocGeneratedNonce: String?
): ByteArray {
    val clientIdBuilder = CborArray.builder().add(clientId)
    mdocGeneratedNonce?.let { clientIdBuilder.add(it) }
    val clientIdHash = Crypto.digest(Algorithm.SHA256, Cbor.encode(clientIdBuilder.end().build()))

    val responseUriBuilder = CborArray.builder().add(responseUri)
    mdocGeneratedNonce?.let { responseUriBuilder.add(it) }
    val responseUriHash = Crypto.digest(Algorithm.SHA256, Cbor.encode(responseUriBuilder.end().build()))

    val oid4vpHandoverBuilder = CborArray.builder()
        .add(clientIdHash)
        .add(responseUriHash)
    authorizationRequestNonce?.let { oid4vpHandoverBuilder.add(it) }

    return Cbor.encode(
        CborArray.builder()
            .add(Simple.NULL)
            .add(Simple.NULL)
            .add(oid4vpHandoverBuilder.end().build())
            .end()
            .build()
    )
}

private fun mdocCalcDcRequestString(
    documentTypeRepository: DocumentTypeRepository,
    format: String,
    session: Session,
    request: DocumentCannedRequest,
    protocol: Protocol,
    nonce: ByteString,
    origin: String,
    readerKey: EcPrivateKey,
    readerPublicKey: EcPublicKeyDoubleCoordinate,
    readerAuthKey: EcPrivateKey,
    readerAuthKeyCertification: X509CertChain,
    signRequest: Boolean,
    encryptResponse: Boolean,
): String {
    when (protocol) {
        Protocol.W3C_DC_PREVIEW -> {
            return mdocCalcDcRequestStringPreview(
                documentTypeRepository,
                request,
                nonce,
                origin,
                readerPublicKey
            )
        }
        Protocol.W3C_DC_ARF -> {
            return mdocCalcDcRequestStringArf(
                documentTypeRepository,
                request,
                nonce,
                origin,
                readerKey,
                readerPublicKey,
                readerAuthKey,
                readerAuthKeyCertification
            )
        }
        Protocol.W3C_DC_MDOC_API -> {
            return mdocCalcDcRequestStringMdocApi(
                documentTypeRepository,
                request,
                nonce,
                origin,
                readerKey,
                readerPublicKey,
                readerAuthKey,
                readerAuthKeyCertification
            )
        }
        Protocol.W3C_DC_OPENID4VP -> {
            return calcDcRequestStringOpenID4VP(
                documentTypeRepository,
                format,
                session,
                request,
                nonce,
                origin,
                readerKey,
                readerPublicKey,
                readerAuthKey,
                readerAuthKeyCertification,
                signRequest,
                encryptResponse,
            )
        }
        else -> {
            throw IllegalStateException("Unsupported protocol $protocol")
        }
    }
}

private fun mdocCalcDcRequestStringPreview(
    documentTypeRepository: DocumentTypeRepository,
    request: DocumentCannedRequest,
    nonce: ByteString,
    origin: String,
    readerPublicKey: EcPublicKeyDoubleCoordinate
    ): String {
    val top = JSONObject()

    val selector = JSONObject()
    val format = JSONArray()
    format.add("mdoc")
    selector.put("format", format)
    top.put("selector", selector)

    selector.put("doctype", request.mdocRequest!!.docType)

    val fields = JSONArray()
    for (ns in request.mdocRequest!!.namespacesToRequest) {
        for ((de, intentToRetain) in ns.dataElementsToRequest) {
            val field = JSONObject()
            field.put("namespace", ns.namespace)
            field.put("name", de.attribute.identifier)
            field.put("intentToRetain", intentToRetain)
            fields.add(field)
        }
    }
    selector.put("fields", fields)

    top.put("nonce", nonce.toByteArray().toBase64Url())
    top.put("readerPublicKey", readerPublicKey.asUncompressedPointEncoding.toBase64Url())

    return top.toString(JSONStyle.NO_COMPRESS)
}

private fun calcDcRequestStringOpenID4VP(
    documentTypeRepository: DocumentTypeRepository,
    format: String,
    session: Session,
    request: DocumentCannedRequest,
    nonce: ByteString,
    origin: String,
    readerKey: EcPrivateKey,
    readerPublicKey: EcPublicKeyDoubleCoordinate,
    readerAuthKey: EcPrivateKey,
    readerAuthKeyCertification: X509CertChain,
    signRequest: Boolean,
    encryptResponse: Boolean
): String {
    val responseMode = if (encryptResponse) {
        Logger.i(TAG, "readerPublicKey is ${readerPublicKey}")
        "dc_api.jwt"
    } else {
        "dc_api"
    }

    val clientMetadata = buildJsonObject {
        put("vp_formats", buildJsonObject {
            putJsonObject("mso_mdoc") {
                putJsonArray("alg") {
                    add(JsonPrimitive("ES256"))
                }
            }
            putJsonObject("dc+sd-jwt") {
                putJsonArray("sd-jwt_alg_values") {
                    add(JsonPrimitive("ES256"))
                }
                putJsonArray("kb-jwt_alg_values") {
                    add(JsonPrimitive("ES256"))
                }
            }
        })
        // TODO:  "require_signed_request_object": true
        if (encryptResponse) {
            put("authorization_encrypted_response_alg", JsonPrimitive("ECDH-ES"))
            put("authorization_encrypted_response_enc", JsonPrimitive("A128GCM"))
        }
        putJsonObject("jwks") {
            putJsonArray("keys") {
                if (encryptResponse) {
                    addJsonObject {
                        put("kty", JsonPrimitive("EC"))
                        put("use", JsonPrimitive("enc"))
                        put("crv", JsonPrimitive("P-256"))
                        put("alg", JsonPrimitive("ECDH-ES"))
                        put("x", JsonPrimitive(readerPublicKey.x.toBase64Url()))
                        put("y", JsonPrimitive(readerPublicKey.y.toBase64Url()))
                    }
                }
            }
        }
    }

    val unsignedRequest = buildJsonObject {
        put("response_type", JsonPrimitive("vp_token"))
        put("response_mode", JsonPrimitive(responseMode))
        put("client_metadata", clientMetadata)
        // Only include client_id for signed requests
        if (signRequest) {
            put("client_id", JsonPrimitive("x509_san_uri:${session.origin}/server/verifier.html"))
        }
        put("nonce", JsonPrimitive(nonce.toByteArray().toBase64Url()))

        putJsonObject("dcql_query") {
            putJsonArray("credentials") {
                if (format == "vc") {
                    addJsonObject {
                        put("id", JsonPrimitive("cred1"))
                        put("format", JsonPrimitive("dc+sd-jwt"))
                        putJsonObject("meta") {
                            put("vct_values",
                                buildJsonArray {
                                    add(JsonPrimitive(request.vcRequest!!.vct))
                                }
                            )
                        }
                        putJsonArray("claims") {
                            // TODO: support path-based claims, e.g. ["address", "postal_code"]
                            for (claim in request.vcRequest!!.claimsToRequest) {
                                addJsonObject {
                                    putJsonArray("path") {
                                        add(JsonPrimitive(claim.identifier))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    addJsonObject {
                        put("id", JsonPrimitive("cred1"))
                        put("format", JsonPrimitive("mso_mdoc"))
                        putJsonObject("meta") {
                            put("doctype_value", JsonPrimitive(request.mdocRequest!!.docType))
                        }
                        putJsonArray("claims") {
                            for (ns in request.mdocRequest!!.namespacesToRequest) {
                                for ((de, intentToRetain) in ns.dataElementsToRequest) {
                                    addJsonObject {
                                        putJsonArray("path") {
                                            add(JsonPrimitive(ns.namespace))
                                            add(JsonPrimitive(de.attribute.identifier))
                                        }
                                        put("intent_to_retain", JsonPrimitive(intentToRetain))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (!signRequest) {
        return unsignedRequest.toString()
    }
    val signedRequestElement = JsonWebSignature.sign(
        key = readerAuthKey,
        signatureAlgorithm = readerAuthKey.curve.defaultSigningAlgorithm,
        claimsSet = unsignedRequest,
        type = "oauth-authz-req+jwt",
        x5c = readerAuthKeyCertification
    )
    val signedRequest = buildJsonObject {
        put("request", JsonPrimitive(signedRequestElement.jsonPrimitive.content))
    }
    return signedRequest.toString()
}

private fun mdocCalcDcRequestStringArf(
    documentTypeRepository: DocumentTypeRepository,
    request: DocumentCannedRequest,
    nonce: ByteString,
    origin: String,
    readerKey: EcPrivateKey,
    readerPublicKey: EcPublicKeyDoubleCoordinate,
    readerAuthKey: EcPrivateKey,
    readerAuthKeyCertification: X509CertChain
): String {
    val arfEncryptionInfo = CborMap.builder()
        .put("nonce", nonce.toByteArray())
        .put("readerPublicKey", readerPublicKey.toCoseKey().toDataItem())
        .end()
        .build()
    val encryptionInfo = CborArray.builder()
        .add("ARFEncryptionv2")
        .add(arfEncryptionInfo)
        .end()
        .build()
    val base64EncryptionInfo = Cbor.encode(encryptionInfo).toBase64Url()

    val sessionTranscript = Cbor.encode(
        CborArray.builder()
            .add(Simple.NULL) // DeviceEngagementBytes
            .add(Simple.NULL) // EReaderKeyBytes
            .addArray() // BrowserHandover
            .add("ARFHandoverv2")
            .add(base64EncryptionInfo)
            .add(origin)
            .end()
            .end()
            .build()
    )

    val itemsToRequest = mutableMapOf<String, MutableMap<String, Boolean>>()
    for (ns in request.mdocRequest!!.namespacesToRequest) {
        for ((de, intentToRetain) in ns.dataElementsToRequest) {
            itemsToRequest.getOrPut(ns.namespace) { mutableMapOf() }
                .put(de.attribute.identifier, intentToRetain)
        }
    }
    val generator = DeviceRequestGenerator(sessionTranscript)
    generator.addDocumentRequest(
        docType = request.mdocRequest!!.docType,
        itemsToRequest = itemsToRequest,
        requestInfo = null,
        readerKey = readerAuthKey,
        signatureAlgorithm = Algorithm.ES256,
        readerKeyCertificateChain = readerAuthKeyCertification,
    )
    val deviceRequest = generator.generate()
    val base64DeviceRequest = deviceRequest.toBase64Url()

    val top = JSONObject()
    top.put("deviceRequest", base64DeviceRequest)
    top.put("encryptionInfo", base64EncryptionInfo)
    return top.toString(JSONStyle.NO_COMPRESS)
}

private fun mdocCalcDcRequestStringMdocApi(
    documentTypeRepository: DocumentTypeRepository,
    request: DocumentCannedRequest,
    nonce: ByteString,
    origin: String,
    readerKey: EcPrivateKey,
    readerPublicKey: EcPublicKeyDoubleCoordinate,
    readerAuthKey: EcPrivateKey,
    readerAuthKeyCertification: X509CertChain
): String {
    val encryptionParameters = CborMap.builder()
        .put("nonce", nonce.toByteArray())
        .put("recipientPublicKey", readerPublicKey.toCoseKey().toDataItem())
        .end()
        .build()
    val encryptionInfo = CborArray.builder()
        .add("dcapi")
        .add(encryptionParameters)
        .end()
        .build()
    val base64EncryptionInfo = Cbor.encode(encryptionInfo).toBase64Url()

    val dcapiInfo = CborArray.builder()
        .add(base64EncryptionInfo)
        .add(origin)
        .end()
        .build()

    val sessionTranscript = Cbor.encode(
        CborArray.builder()
            .add(Simple.NULL) // DeviceEngagementBytes
            .add(Simple.NULL) // EReaderKeyBytes
            .addArray() // BrowserHandover
            .add("dcapi")
            .add(Crypto.digest(Algorithm.SHA256, Cbor.encode(dcapiInfo)))
            .end()
            .end()
            .build()
    )

    val itemsToRequest = mutableMapOf<String, MutableMap<String, Boolean>>()
    for (ns in request.mdocRequest!!.namespacesToRequest) {
        for ((de, intentToRetain) in ns.dataElementsToRequest) {
            itemsToRequest.getOrPut(ns.namespace) { mutableMapOf() }
                .put(de.attribute.identifier, intentToRetain)
        }
    }
    val generator = DeviceRequestGenerator(sessionTranscript)
    generator.addDocumentRequest(
        docType = request.mdocRequest!!.docType,
        itemsToRequest = itemsToRequest,
        requestInfo = null,
        readerKey = readerAuthKey,
        signatureAlgorithm = Algorithm.ES256,
        readerKeyCertificateChain = readerAuthKeyCertification,
    )
    val deviceRequest = generator.generate()
    val base64DeviceRequest = deviceRequest.toBase64Url()

    val top = JSONObject()
    top.put("deviceRequest", base64DeviceRequest)
    top.put("encryptionInfo", base64EncryptionInfo)
    return top.toString(JSONStyle.NO_COMPRESS)
}

private fun mdocCalcPresentationDefinition(
    documentTypeRepository: DocumentTypeRepository,
    request: DocumentCannedRequest
): JSONObject {
    val alg = JSONArray()
    alg.addAll(listOf("ES256"))
    val mso_mdoc = JSONObject()
    mso_mdoc.put("alg", alg)
    val format = JSONObject()
    format.put("mso_mdoc", mso_mdoc)

    val fields = JSONArray()
    for (ns in request.mdocRequest!!.namespacesToRequest) {
        for ((de, intentToRetain) in ns.dataElementsToRequest) {
            var array = JSONArray()
            array.add("\$['${ns.namespace}']['${de.attribute.identifier}']")
            val field = JSONObject()
            field.put("path", array)
            field.put("intent_to_retain", intentToRetain)
            fields.add(field)
        }
    }
    val constraints = JSONObject()
    constraints.put("limit_disclosure", "required")
    constraints.put("fields", fields)

    val input_descriptor_0 = JSONObject()
    input_descriptor_0.put("id", request.mdocRequest!!.docType)
    input_descriptor_0.put("format", format)
    input_descriptor_0.put("constraints", constraints)
    val input_descriptors = JSONArray()
    input_descriptors.add(input_descriptor_0)

    val presentation_definition = JSONObject()
    // TODO: Fill in a unique ID.
    presentation_definition.put("id", "request-TODO-id")
    presentation_definition.put("input_descriptors", input_descriptors)

    return presentation_definition
}

private fun sdjwtCalcPresentationDefinition(
    documentTypeRepository: DocumentTypeRepository,
    request: DocumentCannedRequest
): JSONObject {
    val alg = JSONArray()
    alg.addAll(listOf("ES256"))
    val algContainer = JSONObject()
    algContainer.put("alg", alg)
    val format = JSONObject()
    format.put("jwt_vc", algContainer)

    val fields = JSONArray()
    val vctArray = JSONArray()
    vctArray.add("\$.vct")
    val vctFilter = JSONObject()
    vctFilter.put("const", request.vcRequest!!.vct)
    val vctField = JSONObject()
    vctField.put("path", vctArray)
    vctField.put("filter", vctFilter)
    fields.add(vctField)
    for (claim in request.vcRequest!!.claimsToRequest) {
        var array = JSONArray()
        array.add("\$.${claim.identifier}")
        val field = JSONObject()
        field.put("path", array)
        fields.add(field)
    }
    val constraints = JSONObject()
    constraints.put("limit_disclosure", "required")
    constraints.put("fields", fields)

    val input_descriptor_0 = JSONObject()
    input_descriptor_0.put("id", "Example PID")
    input_descriptor_0.put("format", format)
    input_descriptor_0.put("constraints", constraints)
    val input_descriptors = JSONArray()
    input_descriptors.add(input_descriptor_0)

    val presentation_definition = JSONObject()
    // TODO: Fill in a unique ID.
    presentation_definition.put("id", "request-TODO-id")
    presentation_definition.put("input_descriptors", input_descriptors)

    return presentation_definition
}

private fun calcClientMetadata(session: Session, format: String): JSONObject {
    val encPub = session.encryptionKey.publicKey as EcPublicKeyDoubleCoordinate

    val client_metadata = JSONObject()
    client_metadata.put("authorization_encrypted_response_alg", "ECDH-ES")
    client_metadata.put("authorization_encrypted_response_enc", "A128CBC-HS256")
    client_metadata.put("response_mode", "direct_post.jwt")

    val vpFormats = when (format) {
        "vc" -> {
            val vpFormats = JSONObject()
            val algList = JSONArray()
            algList.addAll(listOf("ES256"))
            val algObj = JSONObject()
            algObj.put("alg", algList)
            vpFormats.put("jwt_vc", algObj)
            vpFormats
        }
        "mdoc" -> {
            val vpFormats = JSONObject()
            val algList = JSONArray()
            algList.addAll(listOf("ES256"))
            val algObj = JSONObject()
            algObj.put("alg", algList)
            vpFormats.put("mso_mdoc", algObj)
            vpFormats
        }

        else -> throw IllegalArgumentException("Unknown format $format")
    }
    client_metadata.put("vp_formats", vpFormats)
    client_metadata.put("vp_formats_supported", vpFormats)

    val key = JSONObject()
    key.put("kty", "EC")
    key.put("use", "enc")
    key.put("crv", "P-256")
    key.put("alg", "ECDH-ES")
    key.put("x", encPub.x.toBase64Url())
    key.put("y", encPub.y.toBase64Url())

    val keys = JSONArray()
    keys.add(key)

    val keys_map = JSONObject()
    keys_map.put("keys", keys)

    client_metadata.put("jwks", keys_map)

    return client_metadata
}

private const val BROWSER_HANDOVER_V1 = "BrowserHandoverv1"
private const val ANDROID_CREDENTIAL_DOCUMENT_VERSION = "ANDROID-HPKE-v1"

private fun parseCredentialDocument(encodedCredentialDocument: ByteArray
): Pair<ByteArray, EcPublicKey> {
    val map = Cbor.decode(encodedCredentialDocument)
    val version = map["version"].asTstr
    if (!version.equals(ANDROID_CREDENTIAL_DOCUMENT_VERSION)) {
        throw IllegalArgumentException("Unexpected version $version")
    }
    val encryptionParameters = map["encryptionParameters"]
    val pkEm = encryptionParameters["pkEm"].asBstr
    val encapsulatedPublicKey =
        EcPublicKeyDoubleCoordinate.fromUncompressedPointEncoding(EcCurve.P256, pkEm)
    val cipherText = map["cipherText"].asBstr
    return Pair(cipherText, encapsulatedPublicKey)
}

//    SessionTranscript = [
//      null, // DeviceEngagementBytes not available
//      null, // EReaderKeyBytes not available
//      AndroidHandover // defined below
//    ]
//
//    From https://github.com/WICG/mobile-document-request-api
//
//    BrowserHandover = [
//      "BrowserHandoverv1",
//      nonce,
//      OriginInfoBytes, // origin of the request as defined in ISO/IEC 18013-7
//      RequesterIdentity, // ? (omitting)
//      pkRHash
//    ]
private fun generateBrowserSessionTranscript(
    nonce: ByteString,
    origin: String,
    requesterIdHash: ByteArray
): ByteArray {
    // TODO: Instead of hand-rolling this, we should use OriginInfoDomain which
    //   uses `domain` instead of `baseUrl` which is what the latest version of 18013-7
    //   calls for.
    val originInfoBytes = Cbor.encode(
        CborMap.builder()
            .put("cat", 1)
            .put("type", 1)
            .putMap("details")
            .put("baseUrl", origin)
            .end()
            .end()
            .build()
    )
    return Cbor.encode(
        CborArray.builder()
            .add(Simple.NULL) // DeviceEngagementBytes
            .add(Simple.NULL) // EReaderKeyBytes
            .addArray() // BrowserHandover
            .add(BROWSER_HANDOVER_V1)
            .add(nonce.toByteArray())
            .add(originInfoBytes)
            .add(requesterIdHash)
            .end()
            .end()
            .build()
    )
}
