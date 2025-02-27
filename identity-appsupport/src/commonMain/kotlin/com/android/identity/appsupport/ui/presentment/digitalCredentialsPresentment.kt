package com.android.identity.appsupport.ui.presentment

import com.android.identity.request.Requester
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.Simple
import com.android.identity.cbor.Tstr
import com.android.identity.credential.Credential
import com.android.identity.credential.SecureAreaBoundCredential
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
import com.android.identity.mdoc.issuersigned.IssuerNamespaces
import com.android.identity.mdoc.mso.MobileSecurityObjectParser
import com.android.identity.mdoc.request.DeviceRequestParser
import com.android.identity.mdoc.response.DeviceResponseGenerator
import com.android.identity.mdoc.response.DocumentGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.mdoc.util.toMdocRequest
import com.android.identity.request.MdocRequest
import com.android.identity.request.MdocRequestedClaim
import com.android.identity.request.Request
import com.android.identity.request.VcRequest
import com.android.identity.request.VcRequestedClaim
import com.android.identity.sdjwt.SdJwtVerifiableCredential
import com.android.identity.sdjwt.credential.SdJwtVcCredential
import com.android.identity.sdjwt.util.JsonWebKey
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.KeyUnlockInteractive
import com.android.identity.trustmanagement.TrustPoint
import com.android.identity.util.Constants
import com.android.identity.util.Logger
import com.android.identity.util.fromBase64Url
import com.android.identity.util.toBase64Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.decodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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
                presentmentMechanism = mechanism,
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
        ByteString(readerPublicKeyBase64.fromBase64Url())
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
            requestedClaims = MdocUtil.generateRequestedClaims(
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
                .add(Crypto.digest(Algorithm.SHA256, readerPublicKey.asUncompressedPointEncoding.toByteArray()))
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
                .add(Crypto.digest(Algorithm.SHA256, readerPublicKey.asUncompressedPointEncoding.toByteArray()))
                .end()
                .end()
                .build()
        )
    }

    val request = MdocRequest(
        requester = Requester(websiteOrigin = presentationMechanism.webOrigin),
        requestedClaims = MdocUtil.generateRequestedClaims(
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
        requestedClaims = request.requestedClaims,
        encodedSessionTranscript = encodedSessionTranscript,
    ))
    val deviceResponse = deviceResponseGenerator.generate()

    val (cipherText, encapsulatedPublicKey) = Crypto.hpkeEncrypt(
        Algorithm.HPKE_BASE_P256_SHA256_AES128GCM,
        readerPublicKey,
        deviceResponse.toByteArray(),
        encodedSessionTranscript.toByteArray()
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
    mdocCredential.increaseUsageCount()
    presentmentModel.setCompleted()
}

@OptIn(ExperimentalEncodingApi::class)
private suspend fun digitalCredentialsOpenID4VPProtocol(
    documentTypeRepository: DocumentTypeRepository,
    presentmentModel: PresentmentModel,
    presentmentMechanism: DigitalCredentialsPresentmentMechanism,
    source: PresentmentSource,
    showConsentPrompt: suspend (
        document: Document,
        request: Request,
        trustPoint: TrustPoint?
    ) -> Boolean
) {
    var requesterCertChain: X509CertChain? = null
    val preReq = Json.parseToJsonElement(presentmentMechanism.request).jsonObject

    val signedRequest = preReq["request"]
    val req = if (signedRequest != null) {
        val jws = Json.parseToJsonElement(signedRequest.jsonPrimitive.content)
        val info = JsonWebSignature.getInfo(jws)
        check(info.x5c != null) { "x5c missing in JWS" }
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

    val origin = presentmentMechanism.webOrigin
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
            Logger.w(
                TAG, "Ignoring client_id value of ${req["client_id"]} for an unsigned request, " +
                        "using synthetic origin $syntheticOrigin instead"
            )
        }
        syntheticOrigin
    }

    // TODO: this is a simple minimal non-conforming implementation of DCQL. Will be ported soon to use our new
    //   DCQL library, see https://github.com/openwallet-foundation-labs/identity-credential/tree/dcql-library
    val dcqlQuery = req["dcql_query"]!!.jsonObject
    val credentials = dcqlQuery["credentials"]!!.jsonArray
    val credential = credentials[0].jsonObject
    val dcqlId = credential["id"]!!.jsonPrimitive.content

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

            // For now, just use the first key as encryption key (there should be only one). This
            // will probably be specced out in OpenID4VP.
            val jwks = clientMetadata["jwks"]!!.jsonObject
            val keys = jwks["keys"]!!.jsonArray
            val encKey = keys[0]
            val x = ByteString(encKey.jsonObject["x"]!!.jsonPrimitive.content.fromBase64Url())
            val y = ByteString(encKey.jsonObject["y"]!!.jsonPrimitive.content.fromBase64Url())
            EcPublicKeyDoubleCoordinate(EcCurve.P256, x, y)
        }

        else -> throw IllegalStateException("Unexpected response_mode $responseMode")
    }

    val format = credential["format"]!!.jsonPrimitive.content
    val response = if (format == "mso_mdoc") {
        openID4VPMsoMdoc(
            credential = credential,
            source = source,
            presentmentModel = presentmentModel,
            presentmentMechanism = presentmentMechanism,
            documentTypeRepository = documentTypeRepository,
            showConsentPrompt = showConsentPrompt,
            origin = origin,
            clientId = clientId,
            nonce = nonce,
            requesterCertChain = requesterCertChain,
            reReaderPublicKey = reReaderPublicKey,
            reEncAlg = reEncAlg
        )
    } else if (format == "dc+sd-jwt") {
        openID4VPSdJwt(
            credential = credential,
            source = source,
            presentmentModel = presentmentModel,
            presentmentMechanism = presentmentMechanism,
            documentTypeRepository = documentTypeRepository,
            showConsentPrompt = showConsentPrompt,
            origin = origin,
            clientId = clientId,
            nonce = nonce,
            requesterCertChain = requesterCertChain,
            reReaderPublicKey = reReaderPublicKey,
            reEncAlg = reEncAlg
        )
    } else {
        throw IllegalArgumentException("Unsupported format $format")
    }
    if (response == null) {
        return
    }

    val vpToken = buildJsonObject {
        putJsonObject("vp_token") {
            put(dcqlId, response)
        }
    }

    val walletGeneratedNonce = Random.nextBytes(16).toBase64Url()
    val responseJson = if (reReaderPublicKey != null) {
        buildJsonObject {
            put("response", JsonWebEncryption.encrypt(
                claimsSet = vpToken.jsonObject,
                recipientPublicKey = reReaderPublicKey,
                encAlg = reEncAlg,
                apu = nonce,
                apv = walletGeneratedNonce
            ))
        }
    } else {
        vpToken
    }
    presentmentMechanism.sendResponse(responseJson.toString())
    presentmentModel.setCompleted()
}

private suspend fun openID4VPMsoMdoc(
    credential: JsonObject,
    source: PresentmentSource,
    presentmentModel: PresentmentModel,
    presentmentMechanism: DigitalCredentialsPresentmentMechanism,
    documentTypeRepository: DocumentTypeRepository,
    showConsentPrompt: suspend (
        document: Document,
        request: Request,
        trustPoint: TrustPoint?
    ) -> Boolean,
    origin: String,
    clientId: String,
    nonce: String,
    requesterCertChain: X509CertChain?,
    reReaderPublicKey: EcPublicKey?,
    reEncAlg: Algorithm,
): String? {
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
            requester = Requester(
                certChain = requesterCertChain,
                websiteOrigin = origin
            ),
            requestedClaims = MdocUtil.generateRequestedClaims(
                docType = docType,
                requestedData = requestedData,
                documentTypeRepository = documentTypeRepository,
                mdocCredential = null
            ),
            docType = docType
        ),
        preSelectedDocument = presentmentMechanism.document
    )
    if (mdocCredentials.isEmpty()) {
        Logger.w(TAG, "No credential found for docType ${docType}, expected exactly one")
        return null
    }
    if (mdocCredentials.size > 1) {
        Logger.w(
            TAG,
            "Unexpected ${mdocCredentials.size} credentials returned, expected exactly one"
        )
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
            .add(Crypto.digest(Algorithm.SHA256, handoverInfo.toByteArray()))
            .end()
            .end()
            .build()
    )
    Logger.iCbor(TAG, "handoverInfo", handoverInfo)
    Logger.iCbor(TAG, "encodedSessionTranscript", encodedSessionTranscript)

    val request = MdocRequest(
        requester = Requester(
            certChain = requesterCertChain,
            websiteOrigin = origin
        ),
        requestedClaims = MdocUtil.generateRequestedClaims(
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
        if (!showConsentPrompt(presentmentMechanism.document, request, trustPoint)) {
            return null
        }
    }

    val deviceResponseGenerator = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)
    deviceResponseGenerator.addDocument(
        calcDocument(
            credential = mdocCredential,
            requestedClaims = request.requestedClaims,
            encodedSessionTranscript = encodedSessionTranscript,
        )
    )
    val deviceResponse = deviceResponseGenerator.generate()
    mdocCredential.increaseUsageCount()
    return deviceResponse.toBase64Url()
}

private suspend fun openID4VPSdJwt(
    credential: JsonObject,
    source: PresentmentSource,
    presentmentModel: PresentmentModel,
    presentmentMechanism: DigitalCredentialsPresentmentMechanism,
    documentTypeRepository: DocumentTypeRepository,
    showConsentPrompt: suspend (
        document: Document,
        request: Request,
        trustPoint: TrustPoint?
    ) -> Boolean,
    origin: String,
    clientId: String,
    nonce: String,
    requesterCertChain: X509CertChain?,
    reReaderPublicKey: EcPublicKey?,
    reEncAlg: Algorithm,
): String? {
    val meta = credential["meta"]!!.jsonObject
    val vctValues = meta["vct_values"]!!.jsonArray
    // TODO: handle multiple VCT values...
    val vct = vctValues[0].jsonPrimitive.content

    val requestedData = mutableMapOf<String, MutableList<Pair<String, Boolean>>>()

    val requestedClaims = mutableListOf<VcRequestedClaim>()
    val claims = credential["claims"]!!.jsonArray
    val documentType = documentTypeRepository.getDocumentTypeForVc(vct)
    for (n in 0 until claims.size) {
        val claim = claims[n].jsonObject
        val path = claim["path"]!!.jsonArray
        // TODO: support path with more than one element
        val claimName = path.get(0).jsonPrimitive.content
        val attribute = documentType?.vcDocumentType?.claims?.get(claimName)
        requestedClaims.add(
            VcRequestedClaim(
                displayName = attribute?.displayName ?: claimName,
                attribute = attribute,
                claimName = claimName
            )
        )
    }

    val request = VcRequest(
        requester = Requester(
            certChain = requesterCertChain,
            websiteOrigin = presentmentMechanism.webOrigin
        ),
        requestedClaims = requestedClaims,
        vct = vct
    )
    val vcCredentials = source.selectCredentialForPresentment(
        request = request,
        preSelectedDocument = presentmentMechanism.document
    )
    if (vcCredentials.isEmpty()) {
        Logger.w(TAG, "No credential found for vct ${vct}, expected exactly one")
        return null
    }
    if (vcCredentials.size > 1) {
        Logger.w(TAG, "Unexpected ${vcCredentials.size} credentials returned, expected exactly one")
    }
    val sdjwtVcCredential = vcCredentials[0] as SdJwtVcCredential

    // Consent prompt..
    val shouldShowConsentPrompt = source.shouldShowConsentPrompt(
        credential = sdjwtVcCredential as Credential,
        request = request,
    )
    val trustPoint = source.findTrustPoint(request)
    if (shouldShowConsentPrompt) {
        if (!showConsentPrompt(presentmentMechanism.document, request, trustPoint)) {
            return null
        }
    }

    val attributesToDisclose = requestedClaims.map { it.claimName }.toSet()
    val sdJwt = SdJwtVerifiableCredential.fromString(sdjwtVcCredential.issuerProvidedData.decodeToString())
    val filteredSdJwt = sdJwt.discloseOnly(attributesToDisclose)

    val secureAreaBoundCredential = if (sdjwtVcCredential is SecureAreaBoundCredential) {
        sdjwtVcCredential as SecureAreaBoundCredential
    } else {
        null
    }
    sdjwtVcCredential.increaseUsageCount()
    return filteredSdJwt.createPresentation(
        secureArea = secureAreaBoundCredential?.secureArea,
        alias = secureAreaBoundCredential?.alias,
        keyUnlockData = KeyUnlockInteractive(),
        nonce = nonce,
        audience = clientId,
        creationTime = Clock.System.now(),
    ).toString()
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

    val encryptionInfo = Cbor.decode(ByteString(encryptionInfoBase64.fromBase64Url()))
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
        ByteString(deviceRequestBase64.fromBase64Url()),
        encodedSessionTranscript,
    ).parse().docRequests.first()

    val mdocCredentials = source.selectCredentialForPresentment(
        request = docRequest.toMdocRequest(
            documentTypeRepository = documentTypeRepository,
            mdocCredential = null,
            requesterAppId = presentationMechanism.appId,
            requesterWebsiteOrigin = presentationMechanism.webOrigin,
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
        mdocCredential = mdocCredential,
        requesterAppId = presentationMechanism.appId,
        requesterWebsiteOrigin = presentationMechanism.webOrigin,
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
        requestedClaims = request.requestedClaims,
        encodedSessionTranscript = encodedSessionTranscript,
    ))
    val deviceResponse = deviceResponseGenerator.generate()

    val (cipherText, encapsulatedPublicKey) = Crypto.hpkeEncrypt(
        Algorithm.HPKE_BASE_P256_SHA256_AES128GCM,
        readerPublicKey,
        deviceResponse.toByteArray(),
        encodedSessionTranscript.toByteArray()
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
    mdocCredential.increaseUsageCount()
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

    val encryptionInfo = Cbor.decode(ByteString(encryptionInfoBase64.fromBase64Url()))
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
            .add(Crypto.digest(Algorithm.SHA256, Cbor.encode(dcapiInfo).toByteArray()))
            .end()
            .end()
            .build()
    )

    // TODO: For now we only consider the first document request. The standard
    //  is not clear if it's permissible to have multiple document requests.
    //
    val docRequest = DeviceRequestParser(
        ByteString(deviceRequestBase64.fromBase64Url()),
        encodedSessionTranscript,
    ).parse().docRequests.first()

    val mdocCredentials = source.selectCredentialForPresentment(
        request = docRequest.toMdocRequest(
            documentTypeRepository = documentTypeRepository,
            mdocCredential = null,
            requesterAppId = presentationMechanism.appId,
            requesterWebsiteOrigin = presentationMechanism.webOrigin,
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
        mdocCredential = mdocCredential,
        requesterAppId = presentationMechanism.appId,
        requesterWebsiteOrigin = presentationMechanism.webOrigin,
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
        requestedClaims = request.requestedClaims,
        encodedSessionTranscript = encodedSessionTranscript,
    ))
    val deviceResponse = deviceResponseGenerator.generate()

    val (cipherText, encapsulatedPublicKey) = Crypto.hpkeEncrypt(
        Algorithm.HPKE_BASE_P256_SHA256_AES128GCM,
        recipientPublicKey,
        deviceResponse.toByteArray(),
        encodedSessionTranscript.toByteArray()
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
    mdocCredential.increaseUsageCount()
    presentmentModel.setCompleted()
}

private suspend fun calcDocument(
    credential: MdocCredential,
    requestedClaims: List<MdocRequestedClaim>,
    encodedSessionTranscript: ByteString
): ByteString {
    val issuerSigned = Cbor.decode(credential.issuerProvidedData)
    val issuerNamespaces = IssuerNamespaces.fromDataItem(issuerSigned["nameSpaces"])
    val issuerAuthCoseSign1 = issuerSigned["issuerAuth"].asCoseSign1
    val encodedMsoBytes = Cbor.decode(issuerAuthCoseSign1.payload!!)
    val encodedMso = Cbor.encode(encodedMsoBytes.asTaggedEncodedCbor)
    val mso = MobileSecurityObjectParser(encodedMso).parse()

    val documentGenerator = DocumentGenerator(
        mso.docType,
        Cbor.encode(issuerSigned["issuerAuth"]),
        encodedSessionTranscript,
    )

    documentGenerator.setIssuerNamespaces(issuerNamespaces.filter(requestedClaims))

    // TODO: support MAC keys from v1.1 request and use setDeviceNamespacesMac() when possible
    //   depending on the value of PresentmentSource.preferSignatureToKeyAgreement(). See also
    //   calcDocument in mdocPresentment.kt.
    //
    val keyInfo = credential.secureArea.getKeyInfo(credential.alias)
    if (!keyInfo.keyPurposes.contains(KeyPurpose.SIGN)) {
        throw IllegalStateException("KeyPurpose.SIGN is required for W3C DC API and key has purposes ${keyInfo.keyPurposes}")
    } else {
        documentGenerator.setDeviceNamespacesSignature(
            dataElements = NameSpacedData.Builder().build(),
            secureArea = credential.secureArea,
            keyAlias = credential.alias,
            keyUnlockData = KeyUnlockInteractive(),
        )
    }

    return documentGenerator.generate()
}
