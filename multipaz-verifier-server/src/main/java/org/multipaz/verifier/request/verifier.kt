package org.multipaz.verifier.request

import org.multipaz.asn1.ASN1Integer
import org.multipaz.cbor.Cbor
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
import org.multipaz.rpc.backend.Configuration
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.getTable
import org.multipaz.mdoc.request.DeviceRequestGenerator
import org.multipaz.mdoc.response.DeviceResponseParser
import org.multipaz.mdoc.util.MdocUtil
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
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
import org.multipaz.asn1.ASN1
import org.multipaz.asn1.ASN1Encoding
import org.multipaz.asn1.ASN1Sequence
import org.multipaz.asn1.ASN1TagClass
import org.multipaz.asn1.ASN1TaggedObject
import org.multipaz.asn1.OID
import org.multipaz.cbor.addCborArray
import org.multipaz.cbor.addCborMap
import org.multipaz.cbor.buildCborArray
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.putCborMap
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509KeyUsage
import org.multipaz.openid.OpenID4VP
import org.multipaz.rpc.cache
import org.multipaz.rpc.handler.InvalidRequestException
import org.multipaz.sdjwt.SdJwt
import org.multipaz.sdjwt.SdJwtKb
import java.net.URLEncoder
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

private const val TAG = "VerifierServlet"

suspend fun verifierPost(call: ApplicationCall, command: String) {
    val requestData = call.receive<ByteArray>()
    when (command) {
        "getAvailableRequests" -> handleGetAvailableRequests(call, requestData)
        "openid4vpBegin" -> handleOpenID4VPBegin(call, requestData)
        "openid4vpGetData" -> handleOpenID4VPGetData(call, requestData)
        "openid4vpResponse" -> handleOpenID4VPResponse(call, requestData)
        "dcBegin" -> handleDcBegin(call, requestData)
        "dcBeginRawDcql" -> handleDcBeginRawDcql(call, requestData)
        "dcGetData" -> handleDcGetData(call, requestData)
        else -> throw InvalidRequestException("Unknown command: $command")
    }
}

suspend fun verifierGet(call: ApplicationCall, command: String) {
    when (command) {
        "openid4vpRequest" -> handleOpenID4VPRequest(call)
        "readerRootCert" -> handleGetReaderRootCert(call)
        else -> throw InvalidRequestException("Unknown command: $command")
    }
}

enum class Protocol {
    W3C_DC_PREVIEW,
    W3C_DC_ARF,
    W3C_DC_MDOC_API,
    W3C_DC_OPENID4VP_24,
    W3C_DC_OPENID4VP_29,
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
    val host: String,
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
    val origin: String,             // e.g. https://ws.davidz25.net
    val host: String,               // e.g. ws.davidz25.net
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
    val host: String,
    val signRequest: Boolean,
    val encryptResponse: Boolean,
)

@Serializable
private data class DCBeginRawDcqlRequest(
    val rawDcql: String,
    val protocol: String,
    val origin: String,
    val host: String,
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

data class KeyMaterial(
    val readerRootKey: EcPrivateKey,
    val readerRootKeyCertificates: X509CertChain,
    val readerRootKeySignatureAlgorithm: Algorithm,
    val readerRootKeyIssuer: String,
) {
    fun toCbor() = Cbor.encode(
        buildCborArray {
            add(readerRootKey.toCoseKey().toDataItem())
            add(readerRootKeyCertificates.toDataItem())
            add(readerRootKeySignatureAlgorithm.coseAlgorithmIdentifier!!)
            add(readerRootKeyIssuer)
        }
    )

    companion object {
        fun fromCbor(encodedCbor: ByteArray): KeyMaterial {
            val array = Cbor.decode(encodedCbor).asArray
            return KeyMaterial(
                array[0].asCoseKey.ecPrivateKey,
                array[1].asX509CertChain,
                Algorithm.fromCoseAlgorithmIdentifier(array[2].asNumber.toInt()),
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
            val readerRootKeySubject = "CN=OWF Multipaz Online Verifier Reader Root Key"
            val readerRootKeyCertificate = MdocUtil.generateReaderRootCertificate(
                readerRootKey = readerRootKey,
                subject = X500Name.fromName(readerRootKeySubject),
                serial = ASN1Integer(1L),
                validFrom = validFrom,
                validUntil = validUntil,
                crlUrl = "https://github.com/openwallet-foundation-labs/identity-credential/crl"
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

@OptIn(ExperimentalSerializationApi::class)
val prettyJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

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

private val keyMaterialLock = Mutex()

private suspend fun getKeyMaterial(): KeyMaterial =
    BackendEnvironment.cache(KeyMaterial::class) { _, _ ->
        val verifierRootStateTable = BackendEnvironment.getTable(verifierRootStateTableSpec)
        keyMaterialLock.withLock {
            val existingBlob = verifierRootStateTable.get("verifierKeyMaterial")
            if (existingBlob != null) {
                KeyMaterial.fromCbor(existingBlob.toByteArray())
            } else {
                val keyMaterial = KeyMaterial.createKeyMaterial()
                verifierRootStateTable.insert(
                    key = "verifierKeyMaterial",
                    data = ByteString(keyMaterial.toCbor()),
                )
                keyMaterial
            }
        }
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

private suspend fun clientId(): String {
    val configuration = BackendEnvironment.getInterface(Configuration::class)!!
    var ret = configuration.getValue("verifierClientId")
    if (ret.isNullOrEmpty()) {
        // Remove the http:// or https:// from the baseUrl.
        val baseUrl = configuration.getValue("base_url")!!
        val startIndex = baseUrl.findAnyOf(listOf("://"))?.first
        ret = if (startIndex == null) baseUrl else baseUrl.removeRange(0, startIndex+3)
    }
    return "x509_san_dns:$ret"
}

private fun createSingleUseReaderKey(dnsName: String): Pair<EcPrivateKey, X509CertChain> {
    val now = Clock.System.now()
    val validFrom = now.plus(DateTimePeriod(minutes = -10), TimeZone.currentSystemDefault())
    val validUntil = now.plus(DateTimePeriod(minutes = 10), TimeZone.currentSystemDefault())
    val readerKey = Crypto.createEcPrivateKey(EcCurve.P256)
    val readerKeySubject = "CN=OWF Multipaz Online Verifier Single-Use Reader Key"

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
        subject = X500Name.fromName("CN=OWF Multipaz TestApp Reader Root"),
        serial = ASN1Integer(1L),
        validFrom = certsValidFrom,
        validUntil = certsValidUntil,
        crlUrl = "https://github.com/openwallet-foundation-labs/identity-credential/crl"
    )

    val readerKeyCertificate = X509Cert.Builder(
        publicKey = readerKey.publicKey,
        signingKey = owfIcReaderRootKey,
        signatureAlgorithm = owfIcReaderRootKey.curve.defaultSigningAlgorithm,
        serialNumber = ASN1Integer(1L),
        subject = X500Name.fromName(readerKeySubject),
        issuer = owfIcReaderRootCert.subject,
        validFrom = validFrom,
        validUntil = validUntil
    )
        .includeSubjectKeyIdentifier()
        .setAuthorityKeyIdentifierToCertificate(owfIcReaderRootCert)
        .setKeyUsage(setOf(X509KeyUsage.DIGITAL_SIGNATURE))
        .addExtension(
            OID.X509_EXTENSION_SUBJECT_ALT_NAME.oid,
            false,
            ASN1.encode(
                ASN1Sequence(listOf(
                    ASN1TaggedObject(
                        ASN1TagClass.CONTEXT_SPECIFIC,
                        ASN1Encoding.PRIMITIVE,
                        2, // dNSName
                        dnsName.encodeToByteArray()
                    )
                ))
            )
        )
        .build()

    return Pair(
        readerKey,
        X509CertChain(listOf(readerKeyCertificate) + owfIcReaderRootCert)
    )
}

private suspend fun handleGetAvailableRequests(
    call: ApplicationCall,
    requestData: ByteArray
) {
    val requests = mutableListOf<DocumentTypeWithRequests>()
    for (dt in documentTypeRepo.documentTypes) {
        if (dt.cannedRequests.isNotEmpty()) {
            val sampleRequests = mutableListOf<SampleRequest>()
            var dtSupportsMdoc = false
            var dtSupportsVc = false
            for (sr in dt.cannedRequests) {
                sampleRequests.add(SampleRequest(
                    sr.id,
                    sr.displayName,
                    sr.mdocRequest != null,
                    sr.jsonRequest != null,
                ))
                if (sr.mdocRequest != null) {
                    dtSupportsMdoc = true
                }
                if (sr.jsonRequest != null) {
                    dtSupportsVc = true
                }
            }
            requests.add(DocumentTypeWithRequests(
                dt.displayName,
                if (dtSupportsMdoc) dt.mdocDocumentType!!.docType else null,
                if (dtSupportsVc) dt.jsonDocumentType!!.vct else null,
                sampleRequests
            ))
        }
    }

    val json = Json { ignoreUnknownKeys = true }
    val responseString = json.encodeToString(AvailableRequests(requests))
    call.respondText(
        status = HttpStatusCode.OK,
        contentType = ContentType.Application.Json,
        text = responseString
    )
}

private fun lookupWellknownRequest(
    format: String,
    docType: String,
    requestId: String
): DocumentCannedRequest {
    return when (format) {
        "mdoc" -> documentTypeRepo.getDocumentTypeForMdoc(docType)!!.cannedRequests.first { it.id == requestId}
        "vc" -> documentTypeRepo.getDocumentTypeForJson(docType)!!.cannedRequests.first { it.id == requestId}
        else -> throw IllegalArgumentException("Unknown format $format")
    }
}

private suspend fun handleDcBegin(
    call: ApplicationCall,
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
        "w3c_dc_openid4vp_24" -> Protocol.W3C_DC_OPENID4VP_24
        "w3c_dc_openid4vp_29" -> Protocol.W3C_DC_OPENID4VP_29
        "openid4vp_plain" -> Protocol.PLAIN_OPENID4VP
        "openid4vp_eudi" -> Protocol.EUDI_OPENID4VP
        "openid4vp_mdoc" -> Protocol.MDOC_OPENID4VP
        "openid4vp_custom" -> Protocol.CUSTOM_OPENID4VP
        else -> throw InvalidRequestException("Unknown protocol '$request.protocol'")
    }

    // Create a new session
    val session = Session(
        nonce = ByteString(Random.Default.nextBytes(16)),
        origin = request.origin,
        host = request.host,
        encryptionKey = Crypto.createEcPrivateKey(EcCurve.P256),
        requestFormat = request.format,
        requestDocType = request.docType,
        requestId = request.requestId,
        protocol = protocol,
        signRequest = request.signRequest,
        encryptResponse = request.encryptResponse,
    )
    val verifierSessionTable = BackendEnvironment.getTable(verifierSessionTableSpec)
    val sessionId = verifierSessionTable.insert(
        key = null,
        data = ByteString(session.toCbor()),
        expiration = Clock.System.now() + SESSION_EXPIRATION_INTERVAL
    )

    val (readerAuthKey, readerAuthKeyCertification) = createSingleUseReaderKey(session.host)

    // Uncomment when making test vectors...
    //Logger.iCbor(TAG, "readerKey: ", Cbor.encode(session.encryptionKey.toCoseKey().toDataItem()))

    val dcRequestString = calcDcRequestString(
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
    call.respondText(
        contentType = ContentType.Application.Json,
        text = json.encodeToString(DCBeginResponse(sessionId, dcRequestString))
    )
}

private suspend fun handleDcBeginRawDcql(
    call: ApplicationCall,
    requestData: ByteArray
) {
    val requestString = String(requestData, 0, requestData.size, Charsets.UTF_8)
    val request = Json.decodeFromString<DCBeginRawDcqlRequest>(requestString)
    Logger.i(TAG, "rawDcql ${request.protocol} ${request.rawDcql}")

    val (protocol, version) = when (request.protocol) {
        // Keep in sync with verifier.html
        "w3c_dc_openid4vp_24" -> Pair(Protocol.W3C_DC_OPENID4VP_24, 24)
        "w3c_dc_openid4vp_29" -> Pair(Protocol.W3C_DC_OPENID4VP_29, 29)
        else -> throw InvalidRequestException("Unknown protocol '$request.protocol'")
    }
    Logger.i(TAG, "version $version")

    // Create a new session
    val session = Session(
        nonce = ByteString(Random.Default.nextBytes(16)),
        origin = request.origin,
        host = request.host,
        encryptionKey = Crypto.createEcPrivateKey(EcCurve.P256),
        requestFormat = "",
        requestDocType = "",
        requestId = "",
        protocol = protocol,
        signRequest = request.signRequest,
        encryptResponse = request.encryptResponse,
    )

    val verifierSessionTable = BackendEnvironment.getTable(verifierSessionTableSpec)
    val sessionId = verifierSessionTable.insert(
        key = null,
        data = ByteString(session.toCbor()),
        expiration = Clock.System.now() + SESSION_EXPIRATION_INTERVAL
    )

    val (readerAuthKey, readerAuthKeyCertification) = createSingleUseReaderKey(session.host)

    val dcRequestString = calcDcRequestStringOpenID4VPforDCQL(
        version = version,
        session = session,
        nonce = session.nonce,
        readerPublicKey = session.encryptionKey.publicKey as EcPublicKeyDoubleCoordinate,
        readerAuthKey = readerAuthKey,
        readerAuthKeyCertification = readerAuthKeyCertification,
        signRequest = request.signRequest,
        encryptResponse = request.encryptResponse,
        dcql = Json.decodeFromString(JsonObject.serializer(), request.rawDcql)
    )
    Logger.i(TAG, "dcRequestString: $dcRequestString")
    val json = Json { ignoreUnknownKeys = true }
    val responseString = json.encodeToString(DCBeginResponse(sessionId, dcRequestString))
    call.respondText(
        contentType = ContentType.Application.Json,
        text = responseString
    )
}

private suspend fun handleDcGetData(
    call: ApplicationCall,
    requestData: ByteArray
) {
    val requestString = String(requestData, 0, requestData.size, Charsets.UTF_8)
    val request = Json.decodeFromString<DCGetDataRequest>(requestString)

    val verifierSessionTable = BackendEnvironment.getTable(verifierSessionTableSpec)
    val encodedSession = verifierSessionTable.get(request.sessionId)
        ?: throw InvalidRequestException("No session for sessionId ${request.sessionId}")
    val session = Session.fromCbor(encodedSession.toByteArray())

    Logger.i(TAG, "Data received from WC3 DC API: ${request.credentialResponse}")

    when (session.protocol) {
        Protocol.W3C_DC_PREVIEW ->handleDcGetDataPreview(session, request.credentialResponse)
        Protocol.W3C_DC_ARF -> handleDcGetDataArf(session, request.credentialResponse)
        Protocol.W3C_DC_MDOC_API -> handleDcGetDataMdocApi(session, request.credentialResponse)
        Protocol.W3C_DC_OPENID4VP_24 -> handleDcGetDataOpenID4VP(24, session, request.credentialResponse)
        Protocol.W3C_DC_OPENID4VP_29 -> handleDcGetDataOpenID4VP(29, session, request.credentialResponse)
        else -> throw IllegalArgumentException("unsupported protocol ${session.protocol}")
    }

    val lines = if (session.sessionTranscript != null) {
        handleGetDataMdoc(session)
    } else {
        val clientIdToUse = if (session.signRequest) {
            "x509_san_dns:${session.host}"
        } else {
            "web-origin:${session.origin}"
        }
        handleGetDataSdJwt(session, clientIdToUse)
    }

    val json = Json { ignoreUnknownKeys = true }

    call.respondText(
        contentType = ContentType.Application.Json,
        text = json.encodeToString(OpenID4VPResultData(lines))
    )
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

    val arfEncryptionInfo = buildCborMap {
        put("nonce", session.nonce.toByteArray())
        put("readerPublicKey", session.encryptionKey.publicKey.toCoseKey().toDataItem())
    }
    val encryptionInfo = buildCborArray {
        add("ARFEncryptionv2")
        add(arfEncryptionInfo)
    }
    val base64EncryptionInfo = Cbor.encode(encryptionInfo).toBase64Url()

    session.sessionTranscript =
        Cbor.encode(
            buildCborArray {
                add(Simple.NULL) // DeviceEngagementBytes
                add(Simple.NULL) // EReaderKeyBytes
                addCborArray {
                    add("ARFHandoverv2")
                    add(base64EncryptionInfo)
                    add(session.origin)
                }
            }
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

    val arfEncryptionInfo = buildCborMap {
        put("nonce", session.nonce.toByteArray())
        put("recipientPublicKey", session.encryptionKey.publicKey.toCoseKey().toDataItem())
    }
    val encryptionInfo = buildCborArray {
        add("dcapi")
        add(arfEncryptionInfo)
    }
    val base64EncryptionInfo = Cbor.encode(encryptionInfo).toBase64Url()

    val dcapiInfo = buildCborArray {
        add(base64EncryptionInfo)
        add(session.origin)
    }

    session.sessionTranscript = Cbor.encode(
        buildCborArray {
            add(Simple.NULL) // DeviceEngagementBytes
            add(Simple.NULL) // EReaderKeyBytes
            addCborArray {
                add("dcapi")
                add(Crypto.digest(Algorithm.SHA256, Cbor.encode(dcapiInfo)))
            }
        }
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
    version: Int,
    session: Session,
    credentialResponse: String
) {
    val response = Json.parseToJsonElement(credentialResponse).jsonObject

    val encryptedResponse = response["response"]
    val vpToken = if (encryptedResponse != null) {
        session.responseWasEncrypted = true
        val decryptedResponse = JsonWebEncryption.decrypt(
            encryptedResponse.jsonPrimitive.content,
            session.encryptionKey
        ).jsonObject
        decryptedResponse["vp_token"]!!.jsonObject
    } else {
        response["vp_token"]!!.jsonObject
    }
    Logger.iJson(TAG, "vpToken", vpToken)

    // TODO: handle multiple vpTokens being returned
    val vpTokenForCred = when (version) {
        24 -> vpToken.values.first().jsonPrimitive.content
        29 -> vpToken.values.first().jsonArray.first().jsonPrimitive.content
        else -> throw IllegalArgumentException("Unsupported OpenID4VP version $version")
    }

    // This is a total hack but in case of Raw DCQL we actually don't really
    // know what was requested. This heuristic to determine if the token is
    // for an ISO mdoc or IETF SD-JWT VC works for now...
    //
    val isMdoc = try {
        val decodedCbor = Cbor.decode(vpTokenForCred.fromBase64Url())
        true
    } catch (e: Throwable) {
        false
    }
    Logger.i(TAG, "isMdoc: $isMdoc")

    if (isMdoc) {
        val effectiveClientId = if (session.signRequest) {
            "x509_san_dns:${session.host}"
        } else {
            "web-origin:${session.origin}"
        }
        val handoverInfo = when (version) {
            24 -> {
                Cbor.encode(
                    buildCborArray {
                        add(session.origin)
                        add(effectiveClientId)
                        add(session.nonce.toByteArray().toBase64Url())
                    }
                )
            }
            29 -> {
                Cbor.encode(
                    buildCborArray {
                        add(session.origin)
                        add(session.nonce.toByteArray().toBase64Url())
                        if (session.encryptResponse) {
                            add(session.encryptionKey.publicKey.toJwkThumbprint(Algorithm.SHA256).toByteArray())
                        } else {
                            add(Simple.NULL)
                        }
                    }
                )
            }
            else -> throw IllegalStateException("Unsupported OpenID4VP version $version")
        }
        session.sessionTranscript = Cbor.encode(
            buildCborArray {
                add(Simple.NULL) // DeviceEngagementBytes
                add(Simple.NULL) // EReaderKeyBytes
                addCborArray {
                    add("OpenID4VPDCAPIHandover")
                    add(Crypto.digest(Algorithm.SHA256, handoverInfo))
                }
            }
        )
        Logger.iCbor(TAG, "handoverInfo", handoverInfo)
        Logger.iCbor(TAG, "sessionTranscript", session.sessionTranscript!!)
        session.deviceResponse = vpTokenForCred.fromBase64Url()
    } else {
        session.verifiablePresentation = vpTokenForCred
    }
}

private suspend fun handleOpenID4VPBegin(
    call: ApplicationCall,
    requestData: ByteArray
) {
    val requestString = String(requestData, 0, requestData.size, Charsets.UTF_8)
    val request = Json.decodeFromString<OpenID4VPBeginRequest>(requestString)

    val protocol = when (request.protocol) {
        // Keep in sync with verifier.html
        "w3c_dc_preview" -> Protocol.W3C_DC_PREVIEW
        "w3c_dc_arf" -> Protocol.W3C_DC_ARF
        "w3c_dc_mdoc_api" -> Protocol.W3C_DC_MDOC_API
        "w3c_dc_openid4vp_24" -> Protocol.W3C_DC_OPENID4VP_24
        "w3c_dc_openid4vp_29" -> Protocol.W3C_DC_OPENID4VP_29
        "openid4vp_plain" -> Protocol.PLAIN_OPENID4VP
        "openid4vp_eudi" -> Protocol.EUDI_OPENID4VP
        "openid4vp_mdoc" -> Protocol.MDOC_OPENID4VP
        "openid4vp_custom" -> Protocol.CUSTOM_OPENID4VP
        else -> throw InvalidRequestException("Unknown protocol '$request.protocol'")
    }

    // Create a new session
    val session = Session(
        nonce = ByteString(Random.Default.nextBytes(16)),
        origin = request.origin,
        host = request.host,
        encryptionKey = Crypto.createEcPrivateKey(EcCurve.P256),
        requestFormat = request.format,
        requestDocType = request.docType,
        requestId = request.requestId,
        protocol = protocol
    )
    val verifierSessionTable = BackendEnvironment.getTable(verifierSessionTableSpec)
    val baseUrl = BackendEnvironment.getInterface(Configuration::class)!!.getValue("base_url")!!
    val clientId = clientId()
    val sessionId = verifierSessionTable.insert(
        key = null,
        data = ByteString(session.toCbor()),
        expiration = Clock.System.now() + SESSION_EXPIRATION_INTERVAL
    )

    val uriScheme = when (session.protocol) {
        Protocol.PLAIN_OPENID4VP -> "openid4vp://"
        Protocol.EUDI_OPENID4VP -> "eudi-openid4vp://"
        Protocol.MDOC_OPENID4VP -> "mdoc-openid4vp://"
        Protocol.CUSTOM_OPENID4VP -> request.scheme
        else -> throw InvalidRequestException("Unknown protocol '$session.protocol'")
    }
    val requestUri = baseUrl + "/verifier/openid4vpRequest?sessionId=${sessionId}"
    val uri = uriScheme +
            "?client_id=" + URLEncoder.encode(clientId, Charsets.UTF_8) +
            "&request_uri=" + URLEncoder.encode(requestUri, Charsets.UTF_8)

    val json = Json { ignoreUnknownKeys = true }
    val responseString = json.encodeToString(OpenID4VPBeginResponse(uri))
    Logger.i(TAG, "Sending handleOpenID4VPBegin response: $responseString")
    call.respondText(
        text = responseString,
        contentType = ContentType.Application.Json
    )
}

val OAUTH_AUTHZ_REQ_JWT = ContentType.parse("application/oauth-authz-req+jwt")

@OptIn(ExperimentalEncodingApi::class)
private suspend fun handleOpenID4VPRequest(
    call: ApplicationCall
) {
    val sessionId = call.request.queryParameters["sessionId"]
        ?: throw InvalidRequestException("No session parameter")
    val verifierSessionTable = BackendEnvironment.getTable(verifierSessionTableSpec)
    val encodedSession = verifierSessionTable.get(sessionId)
        ?: throw InvalidRequestException("No session for sessionId $sessionId")
    val session = Session.fromCbor(encodedSession.toByteArray())
    val baseUrl = BackendEnvironment.getInterface(Configuration::class)!!.getValue("base_url")!!

    val responseUri = baseUrl + "/verifier/openid4vpResponse?sessionId=${sessionId}"

    val (singleUseReaderKeyPriv, singleUseReaderKeyCertChain) = createSingleUseReaderKey(session.host)

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
        .claim("client_id", clientId())
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
    call.respondText(
        contentType = OAUTH_AUTHZ_REQ_JWT,
        text = s
    )

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

private suspend fun handleGetReaderRootCert(
    call: ApplicationCall
) = call.respondText(
    contentType = ContentType.Text.Plain,
    text = getKeyMaterial().readerRootKeyCertificates.certificates[0].toPem()
)

private suspend fun handleOpenID4VPResponse(
    call: ApplicationCall,
    requestData: ByteArray
) {
    val sessionId = call.request.queryParameters["sessionId"]
        ?: throw InvalidRequestException("No session parameter")
    val verifierSessionTable = BackendEnvironment.getTable(verifierSessionTableSpec)
    val encodedSession = verifierSessionTable.get(sessionId)
        ?: throw InvalidRequestException("No session for sessionId $sessionId")
    val session = Session.fromCbor(encodedSession.toByteArray())

    val responseString = String(requestData, 0, requestData.size, Charsets.UTF_8)

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
        clientId = clientId(),
        responseUri = session.responseUri!!,
        authorizationRequestNonce = apv?.toString(),
        mdocGeneratedNonce = apu?.toString()
    )

    // Save `deviceResponse` and `sessionTranscript`, for later
    verifierSessionTable.update(
        key = sessionId,
        data = ByteString(session.toCbor()),
        expiration = Clock.System.now() + SESSION_EXPIRATION_INTERVAL
    )

    val baseUrl = BackendEnvironment.getInterface(Configuration::class)!!.getValue("base_url")!!
    val redirectUri = baseUrl + "/verifier_redirect.html?sessionId=${sessionId}"
    val json = Json { ignoreUnknownKeys = true }
    call.respondText(
        contentType = ContentType.Application.Json,
        text = json.encodeToString(OpenID4VPRedirectUriResponse(redirectUri))
    )
}

private suspend fun handleOpenID4VPGetData(
    call: ApplicationCall,
    requestData: ByteArray
) {
    val requestString = String(requestData, 0, requestData.size, Charsets.UTF_8)
    val request = Json.decodeFromString<OpenID4VPGetData>(requestString)

    val verifierSessionTable = BackendEnvironment.getTable(verifierSessionTableSpec)
    val encodedSession = verifierSessionTable.get(request.sessionId)
        ?: throw InvalidRequestException("No session for sessionId ${request.sessionId}")
    val session = Session.fromCbor(encodedSession.toByteArray())

    val lines = when (session.requestFormat) {
        "mdoc" -> handleGetDataMdoc(session)
        "vc" -> handleGetDataSdJwt(session, clientId())
        else -> throw IllegalStateException("Invalid format ${session.requestFormat}")
    }

    val json = Json { ignoreUnknownKeys = true }

    call.respondText(
        contentType = ContentType.Application.Json,
        text = json.encodeToString(OpenID4VPResultData(lines))
    )
}

private fun handleGetDataMdoc(
    session: Session
): List<OpenID4VPResultLine> {
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

    return lines.toList()
}

private fun handleGetDataSdJwt(
    session: Session,
    clientIdToUse: String,
): List<OpenID4VPResultLine> {
    val lines = mutableListOf<OpenID4VPResultLine>()

    val presentationString = session.verifiablePresentation!!
    Logger.d(TAG, "Handling SD-JWT: $presentationString")
    val (sdJwt, sdJwtKb) = if (presentationString.endsWith("~")) {
        Pair(SdJwt(presentationString), null)
    } else {
        val sdJwtKb = SdJwtKb(presentationString)
        Pair(sdJwtKb.sdJwt, sdJwtKb)
    }
    val issuerCert = sdJwt.x5c?.certificates?.first()
    if (issuerCert == null) {
        lines.add(OpenID4VPResultLine("Error", "Issuer-signed key not in `x5c` in header"))
        return lines.toList()
    }
    if (sdJwtKb == null && sdJwt.jwtBody["cnf"] != null) {
        lines.add(OpenID4VPResultLine("Error", "`cnf` claim present but we got a SD-JWT, not a SD-JWT+KB"))
        return lines.toList()
    }

    val processedJwt = if (sdJwtKb != null) {
        // TODO: actually check nonce, audience, and creationTime
        try {
            val payload = sdJwtKb.verify(
                issuerKey = issuerCert.ecPublicKey,
                checkNonce = { nonce -> true },
                checkAudience = { audience -> true },
                checkCreationTime = { creationTime -> true },
            )
            lines.add(OpenID4VPResultLine("Key-Binding", "Verified"))
            payload
        } catch (e: Throwable) {
            lines.add(OpenID4VPResultLine("Key-Binding", "Error validating: $e"))
            return lines.toList()
        }
    } else {
        try {
            sdJwt.verify(issuerCert.ecPublicKey)
        } catch (e: Throwable) {
            lines.add(OpenID4VPResultLine("Error", "Error validating signature: $e"))
            return lines.toList()
        }
    }

    for ((claimName, claimValue) in processedJwt) {
        val claimValueStr = prettyJson.encodeToString(claimValue)
        lines.add(OpenID4VPResultLine(claimName, claimValueStr))
    }

    return lines.toList()
}

// defined in ISO 18013-7 Annex B
private fun createSessionTranscriptOpenID4VP(
    clientId: String,
    responseUri: String,
    authorizationRequestNonce: String?,
    mdocGeneratedNonce: String?
): ByteArray {
    val clientIdHash = Crypto.digest(
        Algorithm.SHA256,
        Cbor.encode(
            buildCborArray {
                add(clientId)
                mdocGeneratedNonce?.let { add(it) }
            }
        )
    )

    val responseUriHash = Crypto.digest(
        Algorithm.SHA256,
        Cbor.encode(
            buildCborArray {
                add(responseUri)
                mdocGeneratedNonce?.let { add(it) }
            }
        )
    )

    return Cbor.encode(
        buildCborArray {
            add(Simple.NULL)
            add(Simple.NULL)
            addCborArray {
                add(clientIdHash)
                add(responseUriHash)
                authorizationRequestNonce?.let { add(it) }
            }
        }
    )
}

private fun calcDcRequestString(
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
        Protocol.W3C_DC_OPENID4VP_24 -> {
            return calcDcRequestStringOpenID4VP(
                24,
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
        Protocol.W3C_DC_OPENID4VP_29 -> {
            return calcDcRequestStringOpenID4VP(
                29,
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

private fun calcDcRequestStringOpenID4VPforDCQL(
    version: Int,
    session: Session,
    nonce: ByteString,
    readerPublicKey: EcPublicKeyDoubleCoordinate,
    readerAuthKey: EcPrivateKey,
    readerAuthKeyCertification: X509CertChain,
    signRequest: Boolean,
    encryptResponse: Boolean,
    dcql: JsonObject,
): String {
    return when (version) {
        24 -> OpenID4VP.generateRequestDraft24(
            origin = session.origin,
            clientId = "x509_san_dns:${session.host}",
            nonce = nonce.toByteArray().toBase64Url(),
            responseEncryptionKey = if (encryptResponse) readerPublicKey else null,
            requestSigningKey = if (signRequest) readerAuthKey else null,
            requestSigningKeyCertification = if (signRequest) readerAuthKeyCertification else null,
            dclqQuery = dcql
        )
        29 -> OpenID4VP.generateRequest(
            origin = session.origin,
            clientId = "x509_san_dns:${session.host}",
            nonce = nonce.toByteArray().toBase64Url(),
            responseEncryptionKey = if (encryptResponse) readerPublicKey else null,
            requestSigningKey = if (signRequest) readerAuthKey else null,
            requestSigningKeyCertification = if (signRequest) readerAuthKeyCertification else null,
            dclqQuery = dcql
        )
        else -> throw IllegalArgumentException("Unsupport OpenID4VP version $version")
    }.toString()
    /*
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
                        put("kid", JsonPrimitive("reader-auth-key"))
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
            put("client_id", JsonPrimitive("x509_san_dns:${session.host}"))
            putJsonArray("expected_origins") {
                add(JsonPrimitive(session.origin))
            }
        }
        put("nonce", JsonPrimitive(nonce.toByteArray().toBase64Url()))
        put("dcql_query", dcql)
    }

    if (!signRequest) {
        return unsignedRequest.toString()
    }
    val signedRequestElement = JsonWebSignature.sign(
        key = readerAuthKey,
        signatureAlgorithm = readerAuthKey.curve.defaultSigningAlgorithmFullySpecified,
        claimsSet = unsignedRequest,
        type = "oauth-authz-req+jwt",
        x5c = readerAuthKeyCertification
    )
    val signedRequest = buildJsonObject {
        put("request", JsonPrimitive(signedRequestElement))
    }
    return signedRequest.toString()

     */
}

private fun calcDcRequestStringOpenID4VP(
    version: Int,
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
    val dcql = buildJsonObject {
        putJsonArray("credentials") {
            if (format == "vc") {
                addJsonObject {
                    put("id", JsonPrimitive("cred1"))
                    put("format", JsonPrimitive("dc+sd-jwt"))
                    putJsonObject("meta") {
                        put(
                            "vct_values",
                            buildJsonArray {
                                add(JsonPrimitive(request.jsonRequest!!.vct))
                            }
                        )
                    }
                    putJsonArray("claims") {
                        for (claim in request.jsonRequest!!.claimsToRequest) {
                            addJsonObject {
                                putJsonArray("path") {
                                    for (pathElement in claim.identifier.split(".")) {
                                        add(JsonPrimitive(pathElement))
                                    }
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
    return calcDcRequestStringOpenID4VPforDCQL(
        version = version,
        session = session,
        nonce = nonce,
        readerPublicKey = readerPublicKey,
        readerAuthKey = readerAuthKey,
        readerAuthKeyCertification = readerAuthKeyCertification,
        signRequest = signRequest,
        encryptResponse = encryptResponse,
        dcql = dcql
    )
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
    val encryptionInfo = buildCborArray {
        add("ARFEncryptionv2")
        addCborMap {
            put("nonce", nonce.toByteArray())
            put("readerPublicKey", readerPublicKey.toCoseKey().toDataItem())
        }
    }
    val base64EncryptionInfo = Cbor.encode(encryptionInfo).toBase64Url()

    val sessionTranscript = Cbor.encode(
        buildCborArray {
            add(Simple.NULL) // DeviceEngagementBytes
            add(Simple.NULL) // EReaderKeyBytes
            addCborArray {
                add("ARFHandoverv2")
                add(base64EncryptionInfo)
                add(origin)
            }
        }
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
    val encryptionInfo = buildCborArray {
        add("dcapi")
        addCborMap {
            put("nonce", nonce.toByteArray())
            put("recipientPublicKey", readerPublicKey.toCoseKey().toDataItem())
        }
    }
    val base64EncryptionInfo = Cbor.encode(encryptionInfo).toBase64Url()
    val dcapiInfo = buildCborArray {
        add(base64EncryptionInfo)
        add(origin)
    }

    val sessionTranscript = Cbor.encode(
        buildCborArray {
            add(Simple.NULL) // DeviceEngagementBytes
            add(Simple.NULL) // EReaderKeyBytes
            addCborArray {
                add("dcapi")
                add(Crypto.digest(Algorithm.SHA256, Cbor.encode(dcapiInfo)))
            }
        }
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
    vctFilter.put("const", request.jsonRequest!!.vct)
    val vctField = JSONObject()
    vctField.put("path", vctArray)
    vctField.put("filter", vctFilter)
    fields.add(vctField)
    for (claim in request.jsonRequest!!.claimsToRequest) {
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
        buildCborMap {
            put("cat", 1)
            put("type", 1)
            putCborMap("details") {
                put("baseUrl", origin)
            }
        }
    )
    return Cbor.encode(
        buildCborArray {
            add(Simple.NULL) // DeviceEngagementBytes
            add(Simple.NULL) // EReaderKeyBytes
            addCborArray {
                add(BROWSER_HANDOVER_V1)
                add(nonce.toByteArray())
                add(originInfoBytes)
                add(requesterIdHash)
            }
        }
    )
}
