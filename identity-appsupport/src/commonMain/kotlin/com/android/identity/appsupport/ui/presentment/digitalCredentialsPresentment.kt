package com.android.identity.appsupport.ui.presentment

import com.android.identity.request.Requester
import com.android.identity.request.MdocClaim
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.Simple
import com.android.identity.cbor.Tstr
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.EcPublicKeyDoubleCoordinate
import com.android.identity.crypto.JsonWebEncryption
import com.android.identity.crypto.JsonWebSignature
import com.android.identity.crypto.X509CertChain
import com.android.identity.document.Document
import com.android.identity.document.NameSpacedData
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.mdoc.mso.MobileSecurityObjectParser
import com.android.identity.mdoc.mso.StaticAuthDataParser
import com.android.identity.mdoc.request.DeviceRequestParser
import com.android.identity.mdoc.response.DeviceResponseGenerator
import com.android.identity.mdoc.response.DocumentGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.mdoc.util.toMdocRequest
import com.android.identity.request.MdocRequest
import com.android.identity.request.Request
import com.android.identity.securearea.KeyUnlockInteractive
import com.android.identity.trustmanagement.TrustPoint
import com.android.identity.util.Constants
import com.android.identity.util.Logger
import com.android.identity.util.fromBase64Url
import com.android.identity.util.toBase64Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

private const val TAG = "digitalCredentialsPresentment"

internal suspend fun digitalCredentialsPresentment(
    documentTypeRepository: DocumentTypeRepository,
    source: PresentmentSource,
    model: PresentmentModel,
    mechanism: DigitalCredentialsPresentmentMechanism,
    dismissable: MutableStateFlow<Boolean>,
    showConsentPrompt: suspend (
        document: Document,
        request: Request,
        trustPoint: TrustPoint?
    ) -> Boolean
) {
    dismissable.value = false
    try {
        when (mechanism.protocol) {
            "preview" -> digitalCredentialsPreviewProtocol(
                documentTypeRepository = documentTypeRepository,
                presentmentModel = model,
                presentationMechanism = mechanism,
                source = source,
                showConsentPrompt = showConsentPrompt
            )
            "openid4vp" -> digitalCredentialsOpenID4VPProtocol(
                documentTypeRepository = documentTypeRepository,
                presentmentModel = model,
                presentationMechanism = mechanism,
                source = source,
                showConsentPrompt = showConsentPrompt
            )
            "austroads-request-forwarding-v2" -> digitalCredentialsArfProtocol(
                documentTypeRepository = documentTypeRepository,
                presentmentModel = model,
                presentationMechanism = mechanism,
                source = source,
                showConsentPrompt = showConsentPrompt
            )
            "org.iso.mdoc" -> digitalCredentialsMdocApiProtocol(
                documentTypeRepository = documentTypeRepository,
                presentmentModel = model,
                presentationMechanism = mechanism,
                source = source,
                showConsentPrompt = showConsentPrompt
            )
            else -> throw Error("Protocol ${mechanism.protocol} is not supported")
        }

    } catch (error: Throwable) {
        Logger.e(TAG, "Caught exception", error)
        error.printStackTrace()
        model.setCompleted(error)
    }
}

@OptIn(ExperimentalEncodingApi::class)
private suspend fun digitalCredentialsPreviewProtocol(
    documentTypeRepository: DocumentTypeRepository,
    presentmentModel: PresentmentModel,
    presentationMechanism: DigitalCredentialsPresentmentMechanism,
    source: PresentmentSource,
    showConsentPrompt: suspend (
        document: Document,
        request: Request,
        trustPoint: TrustPoint?
    ) -> Boolean
) {
    val previewRequest = Json.parseToJsonElement(presentationMechanism.request).jsonObject
    val selector = previewRequest["selector"]!!.jsonObject
    val nonceBase64 = previewRequest["nonce"]!!.jsonPrimitive.content
    val readerPublicKeyBase64 = previewRequest["readerPublicKey"]!!.jsonPrimitive.content
    val docType = selector["doctype"]!!.jsonPrimitive.content

    val nonce = nonceBase64.fromBase64Url()
    val readerPublicKey = EcPublicKeyDoubleCoordinate.fromUncompressedPointEncoding(
        EcCurve.P256,
        readerPublicKeyBase64.fromBase64Url()
    )

    val requestedData = mutableMapOf<String, MutableList<Pair<String, Boolean>>>()
    val fields = selector["fields"]!!.jsonArray
    for (n in 0 until fields.size) {
        val field = fields[n].jsonObject
        val name = field["name"]!!.jsonPrimitive.content
        val namespace = field["namespace"]!!.jsonPrimitive.content
        val intentToRetain = field["intentToRetain"]!!.jsonPrimitive.boolean
        requestedData.getOrPut(namespace) { mutableListOf() }
            .add(Pair(name, intentToRetain))
    }

    val mdocCredentials = source.selectCredentialForPresentment(
        request = MdocRequest(
            requester = Requester(websiteOrigin = presentationMechanism.webOrigin),
            claims = MdocUtil.generateClaims(
                docType = docType,
                requestedData = requestedData,
                documentTypeRepository = documentTypeRepository,
                mdocCredential = null
            ),
            docType = docType
        ),
        preSelectedDocument = presentationMechanism.document
    )
    if (mdocCredentials.isEmpty()) {
        Logger.w(TAG, "No credential found for docType ${docType}, expected exactly one")
        return
    }
    if (mdocCredentials.size > 1) {
        Logger.w(TAG, "Unexpected ${mdocCredentials.size} credentials returned, expected exactly one")
    }
    val mdocCredential = mdocCredentials[0] as MdocCredential

    val encodedSessionTranscript = if (presentationMechanism.webOrigin == null) {
        Cbor.encode(
            CborArray.builder()
                .add(Simple.NULL) // DeviceEngagementBytes
                .add(Simple.NULL) // EReaderKeyBytes
                .addArray()
                .add("AndroidHandoverv1")
                .add(nonce)
                .add(presentationMechanism.appId.encodeToByteArray())
                .add(Crypto.digest(Algorithm.SHA256, readerPublicKey.asUncompressedPointEncoding))
                .end()
                .end()
                .build()
        )
    } else {
        val originInfoBytes = Cbor.encode(
            CborMap.builder()
                .put("cat", 1)
                .put("type", 1)
                .putMap("details")
                .put("baseUrl", presentationMechanism.webOrigin)
                .end()
                .end()
                .build()
        )
        Cbor.encode(
            CborArray.builder()
                .add(Simple.NULL) // DeviceEngagementBytes
                .add(Simple.NULL) // EReaderKeyBytes
                .addArray()
                .add("BrowserHandoverv1")
                .add(nonce)
                .add(originInfoBytes)
                .add(Crypto.digest(Algorithm.SHA256, readerPublicKey.asUncompressedPointEncoding))
                .end()
                .end()
                .build()
        )
    }

    val request = MdocRequest(
        requester = Requester(websiteOrigin = presentationMechanism.webOrigin),
        claims = MdocUtil.generateClaims(
            docType = docType,
            requestedData = requestedData,
            documentTypeRepository = documentTypeRepository,
            mdocCredential = mdocCredential
        ),
        docType = docType
    )
    val shouldShowConsentPrompt = source.shouldShowConsentPrompt(
        credential = mdocCredential,
        request = request,
    )
    if (shouldShowConsentPrompt) {
        if (!showConsentPrompt(presentationMechanism.document, request, null)) {
            return
        }
    }

    val deviceResponseGenerator = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)
    deviceResponseGenerator.addDocument(calcDocument(
        credential = mdocCredential,
        claims = request.claims,
        encodedSessionTranscript = encodedSessionTranscript,
    ))
    val deviceResponse = deviceResponseGenerator.generate()

    val (cipherText, encapsulatedPublicKey) = Crypto.hpkeEncrypt(
        Algorithm.HPKE_BASE_P256_SHA256_AES128GCM,
        readerPublicKey,
        deviceResponse,
        encodedSessionTranscript
    )

    val encodedCredentialDocument = Cbor.encode(
        CborMap.builder()
            .put("version", "ANDROID-HPKE-v1")
            .putMap("encryptionParameters")
            .put("pkEm", (encapsulatedPublicKey  as EcPublicKeyDoubleCoordinate).asUncompressedPointEncoding)
            .end()
            .put("cipherText", cipherText)
            .end()
            .build()
    )

    val responseJson = buildJsonObject {
        put("token", encodedCredentialDocument.toBase64Url())
    }
    presentationMechanism.sendResponse(responseJson.toString())
    presentmentModel.setCompleted()
}

@OptIn(ExperimentalEncodingApi::class)
private suspend fun digitalCredentialsOpenID4VPProtocol(
    documentTypeRepository: DocumentTypeRepository,
    presentmentModel: PresentmentModel,
    presentationMechanism: DigitalCredentialsPresentmentMechanism,
    source: PresentmentSource,
    showConsentPrompt: suspend (
        document: Document,
        request: Request,
        trustPoint: TrustPoint?
    ) -> Boolean
) {
    var requesterCertChain: X509CertChain? = null
    val preReq = Json.parseToJsonElement(presentationMechanism.request).jsonObject
    Logger.i(TAG, "req: $preReq")

    val signedRequest = preReq["request"]
    val req = if (signedRequest != null) {
        val jws = Json.parseToJsonElement(signedRequest.jsonPrimitive.content)
        Logger.i(TAG, "signedRequest: $jws")
        val info = JsonWebSignature.getInfo(jws)
        check(info.x5c != null) { "x5c missing in JWS" }
        Logger.i(TAG, "key: ${info.x5c!!.certificates.first().ecPublicKey.toPem()}")
        JsonWebSignature.verify(jws, info.x5c!!.certificates.first().ecPublicKey)
        requesterCertChain = info.x5c
        info.claimsSet
    } else {
        preReq
    }

    val nonce = req["nonce"]!!.jsonPrimitive.content
    val responseMode = req["response_mode"]!!.jsonPrimitive.content
    if (!(responseMode == "dc_api" || responseMode == "dc_api.jwt")) {
        // TODO: in the future, flat out reject requests that doesn't use encrypted response
        throw IllegalArgumentException("Unexpected response_mode $responseMode")
    }

    val origin = presentationMechanism.webOrigin
        ?: throw IllegalArgumentException("origin is not available")
    // TODO: handle expected_origins

    // For unsigned requests, we must ignore client_id as per OpenID4VP section A.2. Request:
    //
    //   The client_id parameter MUST be omitted in unsigned requests defined in Appendix A.3.1. The Wallet
    //   determines the effective Client Identifier from the Origin. The effective Client Identifier is
    //   composed of a synthetic Client Identifier Scheme of web-origin and the Origin itself. For example,
    //   an Origin of https://verifier.example.com would result in an effective Client Identifier of
    //   web-origin:https://verifier.example.com. The transport of the request and Origin to the Wallet
    //   is platform-specific and is out of scope of OpenID4VP over the W3C Digital Credentials API. The
    //   Wallet MUST ignore any client_id parameter that is present in an unsigned request.
    //
    val clientId = if (signedRequest != null) {
        req["client_id"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("client_id not set on a signed request")
    } else {
        val syntheticOrigin = "web-origin:$origin"
        if (req.containsKey("client_id")) {
            Logger.w(TAG, "Ignoring client_id value of ${req["client_id"]} for an unsigned request, " +
                    "using synthetic origin $syntheticOrigin instead")
        }
        syntheticOrigin
    }

    // TODO: this is a simple minimal non-conforming implementation of DCQL. Will be ported soon to use our new
    //   DCQL library, see https://github.com/openwallet-foundation-labs/identity-credential/tree/dcql-library
    val dcqlQuery = req["dcql_query"]!!.jsonObject
    val credentials = dcqlQuery["credentials"]!!.jsonArray
    val credential = credentials[0].jsonObject

    Logger.i(TAG, "responseMode: $responseMode")

    // Get public key to encrypt response against...
    var reEncAlg: Algorithm = Algorithm.UNSET
    val reReaderPublicKey: EcPublicKey? = when (responseMode) {
        "dc_api" -> null
        "dc_api.jwt" -> {
            val clientMetadata = req["client_metadata"]!!.jsonObject
            val reAlg = clientMetadata["authorization_encrypted_response_alg"]!!.jsonPrimitive.content
            if (reAlg != "ECDH-ES") {
                throw IllegalStateException("Only ECDH-ES is supported for authorization_encrypted_response_alg")
            }
            val reEnc = clientMetadata["authorization_encrypted_response_enc"]!!.jsonPrimitive.content
            reEncAlg = when (reEnc) {
                "A128GCM" -> Algorithm.A128GCM
                "A192GCM" -> Algorithm.A192GCM
                "A256GCM" -> Algorithm.A256GCM
                else -> {
                    throw IllegalStateException("Only A128GCM, A192GCM, A256GCM is supported for authorization_encrypted_response_enc")
                }
            }
            val jwks = clientMetadata["jwks"]!!.jsonObject
            val keys = jwks["keys"]!!.jsonArray
            val encKey = keys.find { it.jsonObject["use"]?.jsonPrimitive?.content == "enc" }
            if (encKey == null) {
                throw IllegalStateException("Encryption key not found")
            }
            // TODO: use JsonWebKey once identity-sdjwt is merged to identity, instead of assuming curve P-256 here
            val x = encKey.jsonObject["x"]!!.jsonPrimitive.content.fromBase64Url()
            val y = encKey.jsonObject["y"]!!.jsonPrimitive.content.fromBase64Url()
            EcPublicKeyDoubleCoordinate(EcCurve.P256, x, y)
        }
        else -> throw IllegalStateException("Unexpected response_mode $responseMode")
    }

    // TODO: check format

    val dcqlId = credential["id"]!!.jsonPrimitive.content
    val meta = credential["meta"]!!.jsonObject
    val docType = meta["doctype_value"]!!.jsonPrimitive.content

    val requestedData = mutableMapOf<String, MutableList<Pair<String, Boolean>>>()
    val claims = credential["claims"]!!.jsonArray
    for (n in 0 until claims.size) {
        val claim = claims[n].jsonObject
        val path = claim["path"]!!.jsonArray
        val namespace = path.get(0).jsonPrimitive.content
        val name = path.get(1).jsonPrimitive.content
        val intentToRetain = claim["intent_to_retain"]?.jsonPrimitive?.boolean ?: false
        requestedData.getOrPut(namespace) { mutableListOf() }
            .add(Pair(name, intentToRetain))
    }

    val mdocCredentials = source.selectCredentialForPresentment(
        request = MdocRequest(
            requester = Requester(websiteOrigin = origin),
            claims = MdocUtil.generateClaims(
                docType = docType,
                requestedData = requestedData,
                documentTypeRepository = documentTypeRepository,
                mdocCredential = null
            ),
            docType = docType
        ),
        preSelectedDocument = presentationMechanism.document
    )
    if (mdocCredentials.isEmpty()) {
        Logger.w(TAG, "No credential found for docType ${docType}, expected exactly one")
        return
    }
    if (mdocCredentials.size > 1) {
        Logger.w(TAG, "Unexpected ${mdocCredentials.size} credentials returned, expected exactly one")
    }
    val mdocCredential = mdocCredentials[0] as MdocCredential

    val handoverInfo = Cbor.encode(
        CborArray.builder()
            .add(origin)
            .add(clientId)
            .add(nonce)
            .end()
            .build()
    )
    val encodedSessionTranscript = Cbor.encode(
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
    Logger.iCbor(TAG, "encodedSessionTranscript", encodedSessionTranscript)

    val request = MdocRequest(
        requester = Requester(
            certChain = requesterCertChain,
            websiteOrigin = presentationMechanism.webOrigin
        ),
        claims = MdocUtil.generateClaims(
            docType = docType,
            requestedData = requestedData,
            documentTypeRepository = documentTypeRepository,
            mdocCredential = mdocCredential
        ),
        docType = docType
    )
    val shouldShowConsentPrompt = source.shouldShowConsentPrompt(
        credential = mdocCredential,
        request = request,
    )
    val trustPoint = source.findTrustPoint(request)
    if (shouldShowConsentPrompt) {
        if (!showConsentPrompt(presentationMechanism.document, request, trustPoint)) {
            return
        }
    }

    val deviceResponseGenerator = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)
    deviceResponseGenerator.addDocument(calcDocument(
        credential = mdocCredential,
        claims = request.claims,
        encodedSessionTranscript = encodedSessionTranscript,
    ))
    val deviceResponse = deviceResponseGenerator.generate()
    val vpToken = buildJsonObject {
        put(dcqlId, deviceResponse.toBase64Url())
    }

    Logger.i(TAG, "before encryption: `${vpToken.toString()}`")
    val walletGeneratedNonce = Random.nextBytes(16)
    val maybeEncryptedVpToken = if (reReaderPublicKey != null) {
        JsonWebEncryption.encrypt(
            claimsSet = vpToken.jsonObject,
            recipientPublicKey = reReaderPublicKey,
            encAlg = reEncAlg,
            apu = nonce.encodeToByteString(),
            apv = ByteString(walletGeneratedNonce)
        )
    } else {
        vpToken
    }
    Logger.i(TAG, "after encryption: `${maybeEncryptedVpToken.toString()}`")

    val responseJson = buildJsonObject {
        put("vp_token", maybeEncryptedVpToken)
    }
    Logger.i(TAG, "response: `${responseJson}`")

    presentationMechanism.sendResponse(responseJson.toString())
    presentmentModel.setCompleted()
}

@OptIn(ExperimentalEncodingApi::class)
private suspend fun digitalCredentialsArfProtocol(
    documentTypeRepository: DocumentTypeRepository,
    presentmentModel: PresentmentModel,
    presentationMechanism: DigitalCredentialsPresentmentMechanism,
    source: PresentmentSource,
    showConsentPrompt: suspend (
        document: Document,
        request: Request,
        trustPoint: TrustPoint?
    ) -> Boolean
) {
    val arfRequest = Json.parseToJsonElement(presentationMechanism.request).jsonObject
    val deviceRequestBase64 = arfRequest["deviceRequest"]!!.jsonPrimitive.content
    val encryptionInfoBase64 = arfRequest["encryptionInfo"]!!.jsonPrimitive.content

    val encryptionInfo = Cbor.decode(encryptionInfoBase64.fromBase64Url())
    if (encryptionInfo.asArray.get(0).asTstr != "ARFEncryptionv2") {
        throw IllegalArgumentException("Malformed EncryptionInfo")
    }
    val readerPublicKey = encryptionInfo.asArray.get(1).asMap.get(Tstr
        ("readerPublicKey"))!!.asCoseKey.ecPublicKey

    val encodedSessionTranscript =
        Cbor.encode(
            CborArray.builder()
                .add(Simple.NULL) // DeviceEngagementBytes
                .add(Simple.NULL) // EReaderKeyBytes
                .addArray() // BrowserHandover
                .add("ARFHandoverv2")
                .add(encryptionInfoBase64)
                .add(presentationMechanism.webOrigin!!)
                .end()
                .end()
                .build()
        )

    // TODO: For now we only consider the first document request. The standard
    //  is not clear if it's permissible to have multiple document requests.
    //
    val docRequest = DeviceRequestParser(
        deviceRequestBase64.fromBase64Url(),
        encodedSessionTranscript,
    ).parse().docRequests.first()

    val mdocCredentials = source.selectCredentialForPresentment(
        request = docRequest.toMdocRequest(
            documentTypeRepository = documentTypeRepository,
            mdocCredential = null
        ),
        preSelectedDocument = presentationMechanism.document
    )
    if (mdocCredentials.isEmpty()) {
        Logger.w(TAG, "No credential found for docType ${docRequest.docType}, expected exactly one")
        return
    }
    if (mdocCredentials.size > 1) {
        Logger.w(TAG, "Unexpected ${mdocCredentials.size} credentials returned, expected exactly one")
    }
    val mdocCredential = mdocCredentials[0] as MdocCredential

    val request = docRequest.toMdocRequest(
        documentTypeRepository = documentTypeRepository,
        mdocCredential = mdocCredential
    )
    val trustPoint = source.findTrustPoint(request)
    val shouldShowConsentPrompt = source.shouldShowConsentPrompt(
        credential = mdocCredential,
        request = request,
    )
    if (shouldShowConsentPrompt) {
        if (!showConsentPrompt(presentationMechanism.document, request, trustPoint)) {
            return
        }
    }

    val deviceResponseGenerator = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)
    deviceResponseGenerator.addDocument(calcDocument(
        credential = mdocCredential,
        claims = request.claims,
        encodedSessionTranscript = encodedSessionTranscript,
    ))
    val deviceResponse = deviceResponseGenerator.generate()

    val (cipherText, encapsulatedPublicKey) = Crypto.hpkeEncrypt(
        Algorithm.HPKE_BASE_P256_SHA256_AES128GCM,
        readerPublicKey,
        deviceResponse,
        encodedSessionTranscript
    )
    val encryptedResponse =
        Cbor.encode(
            CborArray.builder()
                .add("ARFencryptionv2")
                .addMap()
                .put("pkEM", encapsulatedPublicKey.toCoseKey().toDataItem())
                .put("cipherText", cipherText)
                .end()
                .end()
                .build()
        )

    val responseJson = buildJsonObject {
        put("encryptedResponse", encryptedResponse.toBase64Url())
    }
    presentationMechanism.sendResponse(responseJson.toString())
    presentmentModel.setCompleted()
}

@OptIn(ExperimentalEncodingApi::class)
private suspend fun digitalCredentialsMdocApiProtocol(
    documentTypeRepository: DocumentTypeRepository,
    presentmentModel: PresentmentModel,
    presentationMechanism: DigitalCredentialsPresentmentMechanism,
    source: PresentmentSource,
    showConsentPrompt: suspend (
        document: Document,
        request: Request,
        trustPoint: TrustPoint?
    ) -> Boolean
) {
    val arfRequest = Json.parseToJsonElement(presentationMechanism.request).jsonObject
    val deviceRequestBase64 = arfRequest["deviceRequest"]!!.jsonPrimitive.content
    val encryptionInfoBase64 = arfRequest["encryptionInfo"]!!.jsonPrimitive.content

    val encryptionInfo = Cbor.decode(encryptionInfoBase64.fromBase64Url())
    if (encryptionInfo.asArray.get(0).asTstr != "dcapi") {
        throw IllegalArgumentException("Malformed EncryptionInfo")
    }
    val recipientPublicKey = encryptionInfo.asArray.get(1).asMap.get(Tstr
        ("recipientPublicKey"))!!.asCoseKey.ecPublicKey

    val dcapiInfo = CborArray.builder()
        .add(encryptionInfoBase64)
        .add(presentationMechanism.webOrigin!!)
        .end()
        .build()

    val encodedSessionTranscript = Cbor.encode(
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

    // TODO: For now we only consider the first document request. The standard
    //  is not clear if it's permissible to have multiple document requests.
    //
    val docRequest = DeviceRequestParser(
        deviceRequestBase64.fromBase64Url(),
        encodedSessionTranscript,
    ).parse().docRequests.first()

    val mdocCredentials = source.selectCredentialForPresentment(
        request = docRequest.toMdocRequest(
            documentTypeRepository = documentTypeRepository,
            mdocCredential = null
        ),
        preSelectedDocument = presentationMechanism.document
    )
    if (mdocCredentials.isEmpty()) {
        Logger.w(TAG, "No credential found for docType ${docRequest.docType}, expected exactly one")
        return
    }
    if (mdocCredentials.size > 1) {
        Logger.w(TAG, "Unexpected ${mdocCredentials.size} credentials returned, expected exactly one")
    }
    val mdocCredential = mdocCredentials[0] as MdocCredential

    val request = docRequest.toMdocRequest(
        documentTypeRepository = documentTypeRepository,
        mdocCredential = mdocCredential
    )
    val trustPoint = source.findTrustPoint(request)
    val shouldShowConsentPrompt = source.shouldShowConsentPrompt(
        credential = mdocCredential,
        request = request,
    )
    if (shouldShowConsentPrompt) {
        if (!showConsentPrompt(presentationMechanism.document, request, trustPoint)) {
            return
        }
    }

    val deviceResponseGenerator = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)
    deviceResponseGenerator.addDocument(calcDocument(
        credential = mdocCredential,
        claims = request.claims,
        encodedSessionTranscript = encodedSessionTranscript,
    ))
    val deviceResponse = deviceResponseGenerator.generate()

    val (cipherText, encapsulatedPublicKey) = Crypto.hpkeEncrypt(
        Algorithm.HPKE_BASE_P256_SHA256_AES128GCM,
        recipientPublicKey,
        deviceResponse,
        encodedSessionTranscript
    )
    val enc = (encapsulatedPublicKey as EcPublicKeyDoubleCoordinate).asUncompressedPointEncoding
    val encryptedResponse =
        Cbor.encode(
            CborArray.builder()
                .add("dcapi")
                .addMap()
                .put("enc", enc)
                .put("cipherText", cipherText)
                .end()
                .end()
                .build()
        )

    val responseJson = buildJsonObject {
        put("Response", encryptedResponse.toBase64Url())
    }
    presentationMechanism.sendResponse(responseJson.toString())
    presentmentModel.setCompleted()
}

private suspend fun calcDocument(
    credential: MdocCredential,
    claims: List<MdocClaim>,
    encodedSessionTranscript: ByteArray
): ByteArray {
    val nsAndDataElements = mutableMapOf<String, MutableList<String>>()
    claims.forEach {
        nsAndDataElements.getOrPut(it.namespaceName, { mutableListOf() }).add(it.dataElementName)
    }

    val staticAuthData = StaticAuthDataParser(credential.issuerProvidedData).parse()

    val documentData = credential.document.metadata.nameSpacedData
    val mergedIssuerNamespaces = MdocUtil.mergeIssuerNamesSpaces(
        nsAndDataElements,
        documentData,
        staticAuthData
    )
    val issuerAuthCoseSign1 = Cbor.decode(staticAuthData.issuerAuth).asCoseSign1
    val encodedMsoBytes = Cbor.decode(issuerAuthCoseSign1.payload!!)
    val encodedMso = Cbor.encode(encodedMsoBytes.asTaggedEncodedCbor)
    val mso = MobileSecurityObjectParser(encodedMso).parse()

    val documentGenerator = DocumentGenerator(
        mso.docType,
        staticAuthData.issuerAuth,
        encodedSessionTranscript,
    )
    documentGenerator.setIssuerNamespaces(mergedIssuerNamespaces)

    val keyInfo = credential.secureArea.getKeyInfo(credential.alias)
    documentGenerator.setDeviceNamespacesSignature(
        NameSpacedData.Builder().build(),
        credential.secureArea,
        credential.alias,
        KeyUnlockInteractive(),
        keyInfo.publicKey.curve.defaultSigningAlgorithm,
    )
    return documentGenerator.generate()
}
