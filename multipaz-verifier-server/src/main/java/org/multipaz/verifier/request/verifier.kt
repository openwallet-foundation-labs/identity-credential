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
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509CertChain
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
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
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
import kotlinx.serialization.json.put
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
import org.multipaz.mdoc.zkp.ZkSystemRepository
import org.multipaz.mdoc.zkp.ZkSystemSpec
import org.multipaz.mdoc.zkp.longfellow.LongfellowZkSystem
import org.multipaz.models.openid.OpenID4VP
import org.multipaz.rpc.backend.Resources
import org.multipaz.rpc.handler.InvalidRequestException
import org.multipaz.sdjwt.SdJwt
import org.multipaz.sdjwt.SdJwtKb
import org.multipaz.server.ServerIdentity
import org.multipaz.server.baseUrl
import org.multipaz.server.getBaseUrl
import org.multipaz.server.getServerIdentity
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.trustmanagement.TrustManagerLocal
import org.multipaz.trustmanagement.TrustMetadata
import org.multipaz.util.fromHex
import java.net.URLEncoder
import kotlin.IllegalArgumentException
import kotlin.IllegalStateException
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
    URI_SCHEME_OPENID4VP_29,
}

@Serializable
private data class OpenID4VPBeginRequest(
    val format: String,
    val docType: String,
    val requestId: String,
    val protocol: String,
    val origin: String,
    val host: String,
    val scheme: String,
    val signRequest: Boolean,
    val encryptResponse: Boolean,
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
    val lines: List<ResultLine>
)

@Serializable
private data class ResultLine(
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


private var zkRepo: ZkSystemRepository? = null

private suspend fun getZkSystemRepository(): ZkSystemRepository {
    if (zkRepo != null) {
        return zkRepo!!
    }
    val repo = ZkSystemRepository()
    val circuitsToAdd = listOf(
        "longfellow-libzk-v1/3_1_bd3168ea0a9096b4f7b9b61d1c210dac1b7126a9ec40b8bc770d4d485efce4e9",
        "longfellow-libzk-v1/3_2_40b2b68088f1d4c93a42edf01330fed8cac471cdae2b192b198b4d4fc41c9083",
        "longfellow-libzk-v1/3_3_99a5da3739df68c87c7a380cc904bb275dbd4f1b916c3d297ba9d15ee86dd585",
        "longfellow-libzk-v1/3_4_5249dac202b61e03361a2857867297ee7b1d96a8a4c477d15a4560bde29f704f",
    )
    val longfellowSystem = LongfellowZkSystem()
    val resources = BackendEnvironment.getInterface(Resources::class)!!
    for (circuit in circuitsToAdd) {
        val circuitBytes = resources.getRawResource(circuit)!!
        val pathParts = circuit.split("/")
        longfellowSystem.addCircuit(pathParts[pathParts.size - 1], circuitBytes)
    }
    repo.add(longfellowSystem)
    zkRepo = repo
    return zkRepo!!
}

private suspend fun clientId(): String {
    val configuration = BackendEnvironment.getInterface(Configuration::class)!!
    var ret = configuration.getValue("verifier_client_id")
    if (ret.isNullOrEmpty()) {
        // Remove the http:// or https:// from the baseUrl.
        val baseUrl = configuration.baseUrl
        val startIndex = baseUrl.findAnyOf(listOf("://"))?.first
        ret = if (startIndex == null) baseUrl else baseUrl.removeRange(0, startIndex+3)
    }
    return "x509_san_dns:$ret"
}

private suspend fun getReaderIdentity(): ServerIdentity =
    BackendEnvironment.getServerIdentity("reader_root_identity") {
        val subjectAndIssuer = X500Name.fromName("CN=Multipaz TEST Reader CA")

        val validFrom = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)
        val validUntil = validFrom.plus(DateTimePeriod(years = 5), TimeZone.currentSystemDefault())
        val serial = ASN1Integer.fromRandom(128)

        val readerRootKey = Crypto.createEcPrivateKey(EcCurve.P384)
        val readerRootCertificate =
            MdocUtil.generateReaderRootCertificate(
                readerRootKey = readerRootKey,
                subject = subjectAndIssuer,
                serial = serial,
                validFrom = validFrom,
                validUntil = validUntil,
                crlUrl = "https://github.com/openwallet-foundation-labs/identity-credential/crl"
            )
        ServerIdentity(readerRootKey, X509CertChain(listOf(readerRootCertificate)))
    }

private suspend fun createSingleUseReaderKey(dnsName: String): Pair<EcPrivateKey, X509CertChain> {
    val now = Clock.System.now()
    val validFrom = now.plus(DateTimePeriod(minutes = -10), TimeZone.currentSystemDefault())
    val validUntil = now.plus(DateTimePeriod(minutes = 10), TimeZone.currentSystemDefault())
    val readerKey = Crypto.createEcPrivateKey(EcCurve.P256)
    val readerKeySubject = "CN=OWF Multipaz Online Verifier Single-Use Reader Key"

    val readerIdentity = getReaderIdentity()

    val cert = readerIdentity.certificateChain.certificates.first()
    val readerKeyCertificate = X509Cert.Builder(
        publicKey = readerKey.publicKey,
        signingKey = readerIdentity.privateKey,
        signatureAlgorithm = readerIdentity.privateKey.curve.defaultSigningAlgorithm,
        serialNumber = ASN1Integer(1L),
        subject = X500Name.fromName(readerKeySubject),
        issuer = cert.subject,
        validFrom = validFrom,
        validUntil = validUntil
    )
        .includeSubjectKeyIdentifier()
        .setAuthorityKeyIdentifierToCertificate(cert)
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
        X509CertChain(listOf(readerKeyCertificate) + readerIdentity.certificateChain.certificates)
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
        "uri_scheme_openid4vp_29" -> Protocol.URI_SCHEME_OPENID4VP_29
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
        "w3c_dc_openid4vp_24" -> Pair(Protocol.W3C_DC_OPENID4VP_24, OpenID4VP.Version.DRAFT_24)
        "w3c_dc_openid4vp_29" -> Pair(Protocol.W3C_DC_OPENID4VP_29, OpenID4VP.Version.DRAFT_29)
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
        dcql = Json.decodeFromString(JsonObject.serializer(), request.rawDcql),
        responseMode = OpenID4VP.ResponseMode.DC_API,
        responseUri = null
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
    val encryptedResponseBase64 = response["response"]!!.jsonPrimitive.content

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
        "uri_scheme_openid4vp_29" -> Protocol.URI_SCHEME_OPENID4VP_29
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
    val baseUrl = BackendEnvironment.getBaseUrl()
    val clientId = clientId()
    val sessionId = verifierSessionTable.insert(
        key = null,
        data = ByteString(session.toCbor()),
        expiration = Clock.System.now() + SESSION_EXPIRATION_INTERVAL
    )

    val uriScheme = when (session.protocol) {
        Protocol.URI_SCHEME_OPENID4VP_29 -> request.scheme + "://"
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
    val baseUrl = BackendEnvironment.getBaseUrl()

    val (readerAuthKey, readerAuthKeyCertification) = createSingleUseReaderKey(session.host)

    val request = lookupWellknownRequest(session.requestFormat, session.requestDocType, session.requestId)

    // We'll need responseUri later (to calculate sessionTranscript)
    val responseUri = baseUrl + "/verifier/openid4vpResponse?sessionId=${sessionId}"

    val requestString = calcDcRequestStringOpenID4VP(
        version = OpenID4VP.Version.DRAFT_29,
        documentTypeRepository = documentTypeRepo,
        format = session.requestFormat,
        session = session,
        request = request,
        nonce = session.nonce,
        origin = session.origin,
        readerKey =  session.encryptionKey,
        readerPublicKey = session.encryptionKey.publicKey as EcPublicKeyDoubleCoordinate,
        readerAuthKey = readerAuthKey,
        readerAuthKeyCertification = readerAuthKeyCertification,
        signRequest = session.signRequest,
        encryptResponse = session.encryptResponse,
        responseMode = OpenID4VP.ResponseMode.DIRECT_POST,
        responseUri = responseUri
    )
    val requestObject = Json.decodeFromString<JsonObject>(requestString)
    val signedRequestCs = requestObject["request"]!!.jsonPrimitive.content
    Logger.i(TAG, "signedRequestCs: $signedRequestCs")

    call.respondText(
        contentType = OAUTH_AUTHZ_REQ_JWT,
        text = signedRequestCs
    )

    session.responseUri = responseUri
    runBlocking {
        verifierSessionTable.update(
            key = sessionId,
            data = ByteString(session.toCbor()),
            expiration = Clock.System.now() + SESSION_EXPIRATION_INTERVAL
        )
    }

}

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

    val responseString = requestData.decodeToString()
    Logger.i(TAG, "TODO: look at $responseString")

    val kvPairs = mutableMapOf<String, String>()
    for (part in responseString.split("&")) {
        val parts = part.split("=", limit = 2)
        kvPairs[parts[0]] = parts[1]
    }

    val responseJwtCs = kvPairs["response"]!!
    val splits = responseJwtCs.split(".")
    val responseObj = if (splits.size == 3) {
        // Unsecured JWT
        Json.decodeFromString(JsonObject.serializer(), splits[1].fromBase64Url().decodeToString())
    } else {
        session.responseWasEncrypted = true
        JsonWebEncryption.decrypt(responseJwtCs, session.encryptionKey)
    }
    Logger.iJson(TAG, "responseObj", responseObj)

    // We only support a simple cred so...
    val vpToken = responseObj["vp_token"]!!.jsonObject
    val vpTokenForCred = vpToken.values.first().jsonArray.first().jsonPrimitive.content

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
        val handoverInfo = Cbor.encode(
            buildCborArray {
                add("x509_san_dns:${session.host}")
                add(session.nonce.toByteArray().toBase64Url())
                if (session.encryptResponse) {
                    add(session.encryptionKey.publicKey.toJwkThumbprint(Algorithm.SHA256).toByteArray())
                } else {
                    add(Simple.NULL)
                }
                add(session.responseUri!!)
            }
        )
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

    // Save `deviceResponse` and `sessionTranscript`, for later
    verifierSessionTable.update(
        key = sessionId,
        data = ByteString(session.toCbor()),
        expiration = Clock.System.now() + SESSION_EXPIRATION_INTERVAL
    )

    val baseUrl = BackendEnvironment.getBaseUrl()
    val redirectUri = baseUrl + "/verifier_redirect.html?sessionId=${sessionId}"
    call.respondText(
        contentType = ContentType.Application.Json,
        text = prettyJson.encodeToString(
            buildJsonObject {
                put("redirect_uri", redirectUri)
            }
        )
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


val OAUTH_AUTHZ_REQ_JWT = ContentType.parse("application/oauth-authz-req+jwt")


private suspend fun handleGetReaderRootCert(
    call: ApplicationCall
) = call.respondText(
    contentType = ContentType.Text.Plain,
    text = getReaderIdentity().certificateChain.certificates.joinToString { it.toPem() }
)

private val issuerTrustManagerLock = Mutex()
private var issuerTrustManager: TrustManager? = null

private suspend fun getIssuerTrustManager(): TrustManager {
    issuerTrustManagerLock.withLock {
        issuerTrustManager?.let { return it }
        val trustManager = TrustManagerLocal(EphemeralStorage())
        // TODO: also include certs for issuers on https://issuer.multipaz.org
        trustManager.addX509Cert(
            certificate = X509Cert(encodedCertificate = "308202a83082022da003020102021036ead7e431722dbf66c76398266f8020300a06082a8648ce3d040303302e311f301d06035504030c164f5746204d756c746970617a20544553542049414341310b300906035504060c025553301e170d3234313230313030303030305a170d3334313230313030303030305a302e311f301d06035504030c164f5746204d756c746970617a20544553542049414341310b300906035504060c0255533076301006072a8648ce3d020106052b8104002203620004f900f27bbd26d8ed2594f5cc8d58f1559cf79b993a6a04fec2287e2fbf5bee3caa525f7db1b7949e9c5a2c3f9c981dc72b7b70900edf995252a1b05cfbd0838648779b1ea7f98a07e51ba569259385605f332463b1f54e0e4a2c1cb0839db3d5a382010e3082010a300e0603551d0f0101ff04040302010630120603551d130101ff040830060101ff020100304c0603551d1204453043864168747470733a2f2f6769746875622e636f6d2f6f70656e77616c6c65742d666f756e646174696f6e2d6c6162732f6964656e746974792d63726564656e7469616c30560603551d1f044f304d304ba049a047864568747470733a2f2f6769746875622e636f6d2f6f70656e77616c6c65742d666f756e646174696f6e2d6c6162732f6964656e746974792d63726564656e7469616c2f63726c301d0603551d0e04160414ab651be056c29053f1dd7f6ce487be68de60c9f5301f0603551d23041830168014ab651be056c29053f1dd7f6ce487be68de60c9f5300a06082a8648ce3d0403030369003066023100e5fec5304626e9ee0456c0421acffa40f38b1f75b7fec4779dea4dfc463ea1dd94d36b3cadec950e0c87f62e580703450231009ed622dee7f933898b37120a06a8362a6ebae99816c4e2d5f928ffbab4bc9f4591a85d526a90d67dafe8793c85d1a246".fromHex()),
            metadata = TrustMetadata(
                displayName = "OWF Multipaz TestApp",
                displayIcon = null,
                privacyPolicyUrl = "https://apps.multipaz.org",
                testOnly = true,
            )
        )
        issuerTrustManager = trustManager
        return issuerTrustManager!!
    }
}

private suspend fun handleGetDataMdoc(
    session: Session
): List<ResultLine> {
    val parser = DeviceResponseParser(session.deviceResponse!!, session.sessionTranscript!!)
    val deviceResponse = parser.parse()
    Logger.i(TAG, "Validated DeviceResponse!")

    val trustManager = getIssuerTrustManager()

    // TODO: Add more sophistication in how we convey the result to the webpage, for example
    //  support the following value types
    //  - textual string
    //  - images
    //  - etc/
    //
    val lines = mutableListOf<ResultLine>()
    lines.add(ResultLine("Response end-to-end encrypted", "${session.responseWasEncrypted}"))
    for (document in deviceResponse.documents) {

        val trustResult = trustManager.verify(document.issuerCertificateChain.certificates)

        lines.add(ResultLine("DeviceSigned Authenticated", "${document.deviceSignedAuthenticated}"))
        lines.add(ResultLine("IssuerSigned Authenticated", "${document.issuerSignedAuthenticated} " +
        "(${document.numIssuerEntryDigestMatchFailures} digest failures)"))
        if (trustResult.isTrusted) {
            val tp = trustResult.trustPoints[0]
            val name = tp.metadata.displayName ?: tp.certificate.subject.name
            lines.add(ResultLine("Issuer", "In trust list ($name)"))
        } else {
            val name = document.issuerCertificateChain.certificates.first().subject.name
            lines.add(ResultLine("Issuer", "Not in trust list ($name)"))
        }

        lines.add(ResultLine("DocType", document.docType))
        for (namespaceName in document.issuerNamespaces) {
            lines.add(ResultLine("Namespace", namespaceName))
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
                lines.add(ResultLine(dataElementName, renderedValue))
            }
        }
    }
    for (zkDocument in deviceResponse.zkDocuments) {
        val zkSystemRepository = getZkSystemRepository()
        val trustManager = getIssuerTrustManager()

        try {
            val zkSystemSpec = zkSystemRepository.getAllZkSystemSpecs().find {
                it.id == zkDocument.zkDocumentData.zkSystemSpecId
            }
            if (zkSystemSpec == null) {
                lines.add(ResultLine(
                    "ZK Verification",
                    "ZK System Spec ID ${zkDocument.zkDocumentData.zkSystemSpecId} was not found.")
                )
            } else {

                zkSystemRepository.lookup(zkSystemSpec.system)
                    ?.verifyProof(zkDocument, zkSystemSpec, ByteString(session.sessionTranscript!!))
                    ?: throw IllegalStateException("Zk System '${zkSystemSpec.system}' was not found.")
                lines.add(ResultLine(
                    "ZK Verification",
                    "Successfully validated proof"
                ))
            }

            if (zkDocument.zkDocumentData.msoX5chain == null) {
                lines.add(ResultLine("Issuer", "No msoX5chain in ZkDocumentData"))
            } else {
                val trustResult = trustManager.verify(zkDocument.zkDocumentData.msoX5chain!!.certificates)
                if (trustResult.isTrusted) {
                    val tp = trustResult.trustPoints[0]
                    val name = tp.metadata.displayName ?: tp.certificate.subject.name
                    lines.add(ResultLine("Issuer", "In trust list ($name)"))
                } else {
                    val name = zkDocument.zkDocumentData.msoX5chain!!.certificates.first().subject.name
                    lines.add(ResultLine("Issuer", "Not in trust list ($name)"))
                }
            }

            for ((nameSpaceName, dataElements) in zkDocument.zkDocumentData.issuerSigned) {
                lines.add(ResultLine("Namespace", nameSpaceName))
                for ((dataElementName, dataElementValue) in dataElements) {
                    val valueStr = Cbor.toDiagnostics(
                        dataElementValue,
                        setOf(
                            DiagnosticOption.PRETTY_PRINT,
                            DiagnosticOption.EMBEDDED_CBOR,
                            DiagnosticOption.BSTR_PRINT_LENGTH,
                        )
                    )
                    lines.add(
                        ResultLine(
                            dataElementName,
                            valueStr,
                        )
                    )
                }
            }
            // TODO: also iterate over DeviceSigned items

        } catch (e: Throwable) {
            e.printStackTrace()
            lines.add(ResultLine(
                "ZK Verification",
                "Failed with error $e"
            ))
        }


    }

    return lines.toList()
}

private suspend fun handleGetDataSdJwt(
    session: Session,
    clientIdToUse: String,
): List<ResultLine> {
    val lines = mutableListOf<ResultLine>()

    val trustManager = getIssuerTrustManager()

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
        lines.add(ResultLine("Error", "Issuer-signed key not in `x5c` in header"))
        return lines.toList()
    } else {
        val trustResult = trustManager.verify(sdJwt.x5c!!.certificates)
        if (trustResult.isTrusted) {
            val tp = trustResult.trustPoints[0]
            val name = tp.metadata.displayName ?: tp.certificate.subject.name
            lines.add(ResultLine("Issuer", "In trust list ($name)"))
        } else {
            val name = issuerCert.subject.name
            lines.add(ResultLine("Issuer", "Not in trust list ($name)"))
        }
    }
    if (sdJwtKb == null && sdJwt.jwtBody["cnf"] != null) {
        lines.add(ResultLine("Error", "`cnf` claim present but we got a SD-JWT, not a SD-JWT+KB"))
        return lines.toList()
    }

    val processedJwt = if (sdJwtKb != null) {
        // TODO: actually check nonce, audience, and creationTime
        try {
            var receivedAudience = ""
            val payload = sdJwtKb.verify(
                issuerKey = issuerCert.ecPublicKey,
                checkNonce = { nonce -> true },
                checkAudience = { audience -> receivedAudience = audience; true },
                checkCreationTime = { creationTime -> true },
            )
            lines.add(ResultLine("Key Binding", "Verified"))
            lines.add(ResultLine("Audience", receivedAudience))
            payload
        } catch (e: Throwable) {
            lines.add(ResultLine("Key Binding", "Error validating: $e"))
            return lines.toList()
        }
    } else {
        try {
            sdJwt.verify(issuerCert.ecPublicKey)
        } catch (e: Throwable) {
            lines.add(ResultLine("Error", "Error validating signature: $e"))
            return lines.toList()
        }
    }

    for ((claimName, claimValue) in processedJwt) {
        val claimValueStr = prettyJson.encodeToString(claimValue)
        lines.add(ResultLine(claimName, claimValueStr))
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

private suspend fun calcDcRequestString(
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
                OpenID4VP.Version.DRAFT_24,
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
                OpenID4VP.ResponseMode.DC_API,
                null
            )
        }
        Protocol.W3C_DC_OPENID4VP_29 -> {
            return calcDcRequestStringOpenID4VP(
                OpenID4VP.Version.DRAFT_29,
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
                OpenID4VP.ResponseMode.DC_API,
                null
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
    version: OpenID4VP.Version,
    session: Session,
    nonce: ByteString,
    readerPublicKey: EcPublicKeyDoubleCoordinate,
    readerAuthKey: EcPrivateKey,
    readerAuthKeyCertification: X509CertChain,
    signRequest: Boolean,
    encryptResponse: Boolean,
    dcql: JsonObject,
    responseMode: OpenID4VP.ResponseMode,
    responseUri: String?
): String {
    return OpenID4VP.generateRequest(
        version = version,
        origin = session.origin,
        clientId = "x509_san_dns:${session.host}",
        nonce = nonce.toByteArray().toBase64Url(),
        responseEncryptionKey = if (encryptResponse) readerPublicKey else null,
        requestSigningKey = if (signRequest) readerAuthKey else null,
        requestSigningKeyCertification = if (signRequest) readerAuthKeyCertification else null,
        responseMode = responseMode,
        responseUri = responseUri,
        dclqQuery = dcql
    ).toString()
}

private suspend fun calcDcRequestStringOpenID4VP(
    version: OpenID4VP.Version,
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
    encryptResponse: Boolean,
    responseMode: OpenID4VP.ResponseMode,
    responseUri: String?
): String {
    val zkSystemSpecs = if (request.mdocRequest?.useZkp == true) {
        getZkSystemRepository().getAllZkSystemSpecs()
    } else {
        emptyList()
    }

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
                    if (zkSystemSpecs.isNotEmpty()) {
                        put("format", JsonPrimitive("mso_mdoc_zk"))
                    } else {
                        put("format", JsonPrimitive("mso_mdoc"))
                    }
                    putJsonObject("meta") {
                        put("doctype_value", JsonPrimitive(request.mdocRequest!!.docType))
                        if (zkSystemSpecs.isNotEmpty()) {
                            putJsonArray("zk_system_type") {
                                for (spec in zkSystemSpecs) {
                                    addJsonObject {
                                        put("system", spec.system)
                                        put("id", spec.id)
                                        spec.params.forEach { param ->
                                            put(param.key, param.value.toJson())
                                        }
                                    }
                                }
                            }
                        }
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
        dcql = dcql,
        responseMode = responseMode,
        responseUri = responseUri
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

private suspend fun mdocCalcDcRequestStringMdocApi(
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

    val zkSystemSpecs: List<ZkSystemSpec> = if (request.mdocRequest!!.useZkp) {
        getZkSystemRepository().getAllZkSystemSpecs()
    } else {
        emptyList()
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
        zkSystemSpecs = zkSystemSpecs
    )
    val deviceRequest = generator.generate()
    val base64DeviceRequest = deviceRequest.toBase64Url()

    val top = JSONObject()
    top.put("deviceRequest", base64DeviceRequest)
    top.put("encryptionInfo", base64EncryptionInfo)
    return top.toString(JSONStyle.NO_COMPRESS)
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
