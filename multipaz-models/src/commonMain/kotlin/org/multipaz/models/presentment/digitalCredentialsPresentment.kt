package org.multipaz.models.presentment

import org.multipaz.request.Requester
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.CborMap
import org.multipaz.cbor.Simple
import org.multipaz.cbor.Tstr
import org.multipaz.credential.Credential
import org.multipaz.credential.SecureAreaBoundCredential
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.EcPublicKeyDoubleCoordinate
import org.multipaz.crypto.JsonWebEncryption
import org.multipaz.crypto.JsonWebSignature
import org.multipaz.crypto.X509CertChain
import org.multipaz.document.Document
import org.multipaz.document.NameSpacedData
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.mdoc.issuersigned.IssuerNamespaces
import org.multipaz.mdoc.mso.MobileSecurityObjectParser
import org.multipaz.mdoc.request.DeviceRequestParser
import org.multipaz.mdoc.response.DeviceResponseGenerator
import org.multipaz.mdoc.response.DocumentGenerator
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.mdoc.util.toMdocRequest
import org.multipaz.request.MdocRequest
import org.multipaz.request.MdocRequestedClaim
import org.multipaz.request.Request
import org.multipaz.request.VcRequest
import org.multipaz.request.VcRequestedClaim
import org.multipaz.sdjwt.SdJwtVerifiableCredential
import org.multipaz.sdjwt.credential.SdJwtVcCredential
import org.multipaz.securearea.KeyUnlockInteractive
import org.multipaz.trustmanagement.TrustPoint
import org.multipaz.util.Constants
import org.multipaz.util.Logger
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.multipaz.cbor.addCborArray
import org.multipaz.cbor.addCborMap
import org.multipaz.cbor.buildCborArray
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.putCborMap
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
                presentmentMechanism = mechanism,
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
                presentmentMechanism = mechanism,
                source = source,
                showConsentPrompt = showConsentPrompt
            )
            "org.iso.mdoc" -> digitalCredentialsMdocApiProtocol(
                documentTypeRepository = documentTypeRepository,
                presentmentModel = model,
                presentmentMechanism = mechanism,
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
    presentmentMechanism: DigitalCredentialsPresentmentMechanism,
    source: PresentmentSource,
    showConsentPrompt: suspend (
        document: Document,
        request: Request,
        trustPoint: TrustPoint?
    ) -> Boolean
) {
    val previewRequest = Json.parseToJsonElement(presentmentMechanism.request).jsonObject
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

    val requestBeforeFiltering = MdocRequest(
        requester = Requester(websiteOrigin = presentmentMechanism.webOrigin),
        requestedClaims = MdocUtil.generateRequestedClaims(
            docType = docType,
            requestedData = requestedData,
            documentTypeRepository = documentTypeRepository,
            mdocCredential = null
        ),
        docType = docType
    )
    val credentialsForPresentment = source.getCredentialForPresentment(
        request = requestBeforeFiltering,
        document = presentmentMechanism.document
    )
    if (credentialsForPresentment.credential == null) {
        Logger.w(TAG, "No credential found for docType ${docType}")
        return
    }
    val mdocCredential = credentialsForPresentment.credential as MdocCredential

    val encodedSessionTranscript = if (presentmentMechanism.webOrigin == null) {
        Cbor.encode(
            buildCborArray {
                add(Simple.NULL) // DeviceEngagementBytes
                add(Simple.NULL) // EReaderKeyBytes
                addCborArray {
                    add("AndroidHandoverv1")
                    add(nonce)
                    add(presentmentMechanism.appId.encodeToByteArray())
                    add(
                        Crypto.digest(
                            Algorithm.SHA256,
                            readerPublicKey.asUncompressedPointEncoding
                        )
                    )
                }
            }
        )
    } else {
        val originInfoBytes = Cbor.encode(
            buildCborMap {
                put("cat", 1)
                put("type", 1)
                putCborMap("details") {
                    put("baseUrl", presentmentMechanism.webOrigin)
                }
            }
        )
        Cbor.encode(
            buildCborArray {
                add(Simple.NULL) // DeviceEngagementBytes
                add(Simple.NULL) // EReaderKeyBytes
                addCborArray {
                    add("BrowserHandoverv1")
                    add(nonce)
                    add(originInfoBytes)
                    add(Crypto.digest(Algorithm.SHA256, readerPublicKey.asUncompressedPointEncoding))
                }
            }
        )
    }

    val request = MdocRequest(
        requester = Requester(websiteOrigin = presentmentMechanism.webOrigin),
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
        if (!showConsentPrompt(presentmentMechanism.document, request, null)) {
            return
        }
    }

    val deviceResponseGenerator = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)
    deviceResponseGenerator.addDocument(calcDocument(
        credentialForPresentment = credentialsForPresentment,
        requestedClaims = request.requestedClaims,
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
        buildCborMap {
            put("version", "ANDROID-HPKE-v1")
            putCborMap("encryptionParameters") {
                put(
                    "pkEm",
                    (encapsulatedPublicKey as EcPublicKeyDoubleCoordinate).asUncompressedPointEncoding
                )
            }
            put("cipherText", cipherText)
        }
    )

    val responseJson = buildJsonObject {
        put("token", encodedCredentialDocument.toBase64Url())
    }
    presentmentMechanism.sendResponse(responseJson.toString())
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
            val x = encKey.jsonObject["x"]!!.jsonPrimitive.content.fromBase64Url()
            val y = encKey.jsonObject["y"]!!.jsonPrimitive.content.fromBase64Url()
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

    val requestBeforeFiltering = MdocRequest(
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
    )
    val credentialsForPresentment = source.getCredentialForPresentment(
        request = requestBeforeFiltering,
        document = presentmentMechanism.document
    )
    if (credentialsForPresentment.credential == null) {
        Logger.w(TAG, "No credential found for docType ${docType}")
        return null
    }
    val mdocCredential = credentialsForPresentment.credential as MdocCredential

    val handoverInfo = Cbor.encode(
        buildCborArray {
            add(origin)
            add(clientId)
            add(nonce)
        }
    )
    val encodedSessionTranscript = Cbor.encode(
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
            credentialForPresentment = credentialsForPresentment,
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
    val credentialsForPresentment = source.getCredentialForPresentment(
        request = request,
        document = presentmentMechanism.document
    )
    if (credentialsForPresentment.credential == null) {
        Logger.w(TAG, "No credential found for vct ${vct}")
        return null
    }
    val sdjwtVcCredential = credentialsForPresentment.credential as SdJwtVcCredential

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
    presentmentMechanism: DigitalCredentialsPresentmentMechanism,
    source: PresentmentSource,
    showConsentPrompt: suspend (
        document: Document,
        request: Request,
        trustPoint: TrustPoint?
    ) -> Boolean
) {
    val arfRequest = Json.parseToJsonElement(presentmentMechanism.request).jsonObject
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
            buildCborArray {
                add(Simple.NULL) // DeviceEngagementBytes
                add(Simple.NULL) // EReaderKeyBytes
                addCborArray {
                    add("ARFHandoverv2")
                    add(encryptionInfoBase64)
                    add(presentmentMechanism.webOrigin!!)
                }
            }
        )

    // TODO: For now we only consider the first document request. The standard
    //  is not clear if it's permissible to have multiple document requests.
    //
    val docRequest = DeviceRequestParser(
        deviceRequestBase64.fromBase64Url(),
        encodedSessionTranscript,
    ).parse().docRequests.first()

    val requestBeforeFiltering = docRequest.toMdocRequest(
        documentTypeRepository = documentTypeRepository,
        mdocCredential = null,
        requesterAppId = presentmentMechanism.appId,
        requesterWebsiteOrigin = presentmentMechanism.webOrigin,
    )
    val credentialsForPresentment = source.getCredentialForPresentment(
        request = requestBeforeFiltering,
        document = presentmentMechanism.document
    )
    if (credentialsForPresentment.credential == null) {
        Logger.w(TAG, "No credential found for docType ${docRequest.docType}")
        return
    }
    val mdocCredential = credentialsForPresentment.credential as MdocCredential
    
    val request = docRequest.toMdocRequest(
        documentTypeRepository = documentTypeRepository,
        mdocCredential = mdocCredential,
        requesterAppId = presentmentMechanism.appId,
        requesterWebsiteOrigin = presentmentMechanism.webOrigin,
    )
    val trustPoint = source.findTrustPoint(request)
    val shouldShowConsentPrompt = source.shouldShowConsentPrompt(
        credential = mdocCredential,
        request = request,
    )
    if (shouldShowConsentPrompt) {
        if (!showConsentPrompt(presentmentMechanism.document, request, trustPoint)) {
            return
        }
    }

    val deviceResponseGenerator = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)
    deviceResponseGenerator.addDocument(calcDocument(
        credentialForPresentment = credentialsForPresentment,
        requestedClaims = request.requestedClaims,
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
            buildCborArray {
                add("ARFencryptionv2")
                addCborMap {
                    put("pkEM", encapsulatedPublicKey.toCoseKey().toDataItem())
                    put("cipherText", cipherText)
                }
            }
        )

    val responseJson = buildJsonObject {
        put("encryptedResponse", encryptedResponse.toBase64Url())
    }
    presentmentMechanism.sendResponse(responseJson.toString())
    mdocCredential.increaseUsageCount()
    presentmentModel.setCompleted()
}

@OptIn(ExperimentalEncodingApi::class)
private suspend fun digitalCredentialsMdocApiProtocol(
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
    val arfRequest = Json.parseToJsonElement(presentmentMechanism.request).jsonObject
    val deviceRequestBase64 = arfRequest["deviceRequest"]!!.jsonPrimitive.content
    val encryptionInfoBase64 = arfRequest["encryptionInfo"]!!.jsonPrimitive.content

    val encryptionInfo = Cbor.decode(encryptionInfoBase64.fromBase64Url())
    if (encryptionInfo.asArray.get(0).asTstr != "dcapi") {
        throw IllegalArgumentException("Malformed EncryptionInfo")
    }
    val recipientPublicKey = encryptionInfo.asArray.get(1).asMap.get(Tstr
        ("recipientPublicKey"))!!.asCoseKey.ecPublicKey

    val dcapiInfo = buildCborArray {
        add(encryptionInfoBase64)
        add(presentmentMechanism.webOrigin!!)
    }

    val encodedSessionTranscript = Cbor.encode(
        buildCborArray {
            add(Simple.NULL) // DeviceEngagementBytes
            add(Simple.NULL) // EReaderKeyBytes
            addCborArray {
                add("dcapi")
                add(Crypto.digest(Algorithm.SHA256, Cbor.encode(dcapiInfo)))
            }
        }
    )

    // TODO: For now we only consider the first document request. The standard
    //  is not clear if it's permissible to have multiple document requests.
    //
    val docRequest = DeviceRequestParser(
        deviceRequestBase64.fromBase64Url(),
        encodedSessionTranscript,
    ).parse().docRequests.first()

    val requestBeforeFiltering = docRequest.toMdocRequest(
        documentTypeRepository = documentTypeRepository,
        mdocCredential = null,
        requesterAppId = presentmentMechanism.appId,
        requesterWebsiteOrigin = presentmentMechanism.webOrigin,
    )
    val credentialsForPresentment = source.getCredentialForPresentment(
        request = requestBeforeFiltering,
        document = presentmentMechanism.document
    )
    if (credentialsForPresentment.credential == null) {
        Logger.w(TAG, "No credential found for docType ${docRequest.docType}")
        return
    }
    val mdocCredential = credentialsForPresentment.credential as MdocCredential

    val request = docRequest.toMdocRequest(
        documentTypeRepository = documentTypeRepository,
        mdocCredential = mdocCredential,
        requesterAppId = presentmentMechanism.appId,
        requesterWebsiteOrigin = presentmentMechanism.webOrigin,
    )
    val trustPoint = source.findTrustPoint(request)
    val shouldShowConsentPrompt = source.shouldShowConsentPrompt(
        credential = mdocCredential,
        request = request,
    )
    if (shouldShowConsentPrompt) {
        if (!showConsentPrompt(presentmentMechanism.document, request, trustPoint)) {
            return
        }
    }

    val deviceResponseGenerator = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)
    deviceResponseGenerator.addDocument(calcDocument(
        credentialForPresentment = credentialsForPresentment,
        requestedClaims = request.requestedClaims,
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
            buildCborArray {
                add("dcapi")
                addCborMap {
                    put("enc", enc)
                    put("cipherText", cipherText)
                }
            }
        )

    val responseJson = buildJsonObject {
        put("Response", encryptedResponse.toBase64Url())
    }
    presentmentMechanism.sendResponse(responseJson.toString())
    mdocCredential.increaseUsageCount()
    presentmentModel.setCompleted()
}

private suspend fun calcDocument(
    credentialForPresentment: CredentialForPresentment,
    requestedClaims: List<MdocRequestedClaim>,
    encodedSessionTranscript: ByteArray
): ByteArray {
    // TODO: support MAC keys from v1.1 request and use setDeviceNamespacesMac() when possible
    //   depending on the value of PresentmentSource.preferSignatureToKeyAgreement(). See also
    //   calcDocument in mdocPresentment.kt.
    //
    val credential = credentialForPresentment.credential as MdocCredential

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
    val keyInfo = credential.secureArea.getKeyInfo(credential.alias)
    if (!keyInfo.algorithm.isSigning) {
        throw IllegalStateException(
            "Signing is required for W3C DC API but its algorithm ${keyInfo.algorithm.name} is not for signing"
        )
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
