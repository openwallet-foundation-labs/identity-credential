package org.multipaz.models.presentment

import org.multipaz.request.Requester
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.Simple
import org.multipaz.cbor.Tstr
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPublicKeyDoubleCoordinate
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
import org.multipaz.securearea.KeyUnlockInteractive
import org.multipaz.trustmanagement.TrustPoint
import org.multipaz.util.Constants
import org.multipaz.util.Logger
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.multipaz.cbor.addCborArray
import org.multipaz.cbor.addCborMap
import org.multipaz.cbor.buildCborArray
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.putCborMap
import org.multipaz.mdoc.zkp.ZkSystem
import org.multipaz.mdoc.zkp.ZkSystemSpec
import org.multipaz.models.openid.OpenID4VP
import kotlin.io.encoding.ExperimentalEncodingApi

private const val TAG = "digitalCredentialsPresentment"

internal suspend fun digitalCredentialsPresentment(
    documentTypeRepository: DocumentTypeRepository,
    source: PresentmentSource,
    mechanism: DigitalCredentialsPresentmentMechanism,
    dismissable: MutableStateFlow<Boolean>,
    showConsentPrompt: suspend (
        document: Document,
        request: Request,
        trustPoint: TrustPoint?
    ) -> Boolean
) {
    Logger.i(TAG, "mechanism.protocol: ${mechanism.protocol}")
    Logger.i(TAG, "mechanism.request: ${mechanism.data}")
    dismissable.value = false
    when (mechanism.protocol) {
        "preview" -> digitalCredentialsPreviewProtocol(
            documentTypeRepository = documentTypeRepository,
            presentmentMechanism = mechanism,
            source = source,
            showConsentPrompt = showConsentPrompt
        )
        "openid4vp", "openid4vp-v1-unsigned", "openid4vp-v1-signed" -> digitalCredentialsOpenID4VPProtocol(
            documentTypeRepository = documentTypeRepository,
            presentmentMechanism = mechanism,
            source = source,
            showConsentPrompt = showConsentPrompt
        )
        "austroads-request-forwarding-v2" -> digitalCredentialsArfProtocol(
            documentTypeRepository = documentTypeRepository,
            presentmentMechanism = mechanism,
            source = source,
            showConsentPrompt = showConsentPrompt
        )
        "org.iso.mdoc", "org-iso-mdoc" -> digitalCredentialsMdocApiProtocol(
            documentTypeRepository = documentTypeRepository,
            presentmentMechanism = mechanism,
            source = source,
            showConsentPrompt = showConsentPrompt
        )
        else -> throw Error("Protocol ${mechanism.protocol} is not supported")
    }
}

@OptIn(ExperimentalEncodingApi::class)
private suspend fun digitalCredentialsPreviewProtocol(
    documentTypeRepository: DocumentTypeRepository,
    presentmentMechanism: DigitalCredentialsPresentmentMechanism,
    source: PresentmentSource,
    showConsentPrompt: suspend (
        document: Document,
        request: Request,
        trustPoint: TrustPoint?
    ) -> Boolean
) {
    val previewRequest = presentmentMechanism.data
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
    val mdocCredential = source.selectCredential(
        document = presentmentMechanism.document,
        request = requestBeforeFiltering,
        keyAgreementPossible = emptyList(),
    ) as MdocCredential?
    if (mdocCredential == null) {
        Logger.w(TAG, "No credential found for docType ${docType}")
        return
    }

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
    if (!showConsentPrompt(mdocCredential.document, request, null)) {
        throw PresentmentCanceled("The user did not consent")
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

    val data = buildJsonObject {
        put("token", encodedCredentialDocument.toBase64Url())
    }
    presentmentMechanism.sendResponse(
        protocol = presentmentMechanism.protocol,
        data = data
    )
    mdocCredential.increaseUsageCount()
}

@OptIn(ExperimentalEncodingApi::class)
private suspend fun digitalCredentialsOpenID4VPProtocol(
    documentTypeRepository: DocumentTypeRepository,
    presentmentMechanism: DigitalCredentialsPresentmentMechanism,
    source: PresentmentSource,
    showConsentPrompt: suspend (
        document: Document,
        request: Request,
        trustPoint: TrustPoint?
    ) -> Boolean
) {
    val version = when (presentmentMechanism.protocol) {
        "openid4vp" -> OpenID4VP.Version.DRAFT_24
        "openid4vp-v1-unsigned", "openid4vp-v1-signed" -> OpenID4VP.Version.DRAFT_29
        else -> throw IllegalStateException("Unexpected protocol ${presentmentMechanism.protocol}")
    }
    var requesterCertChain: X509CertChain? = null
    val preReq = presentmentMechanism.data

    val signedRequest = preReq["request"]
    val req = if (signedRequest != null) {
        val jws = Json.parseToJsonElement(signedRequest.jsonPrimitive.content)
        val info = JsonWebSignature.getInfo(jws.jsonPrimitive.content)
        check(info.x5c != null) { "x5c missing in JWS" }
        JsonWebSignature.verify(jws.jsonPrimitive.content, info.x5c!!.certificates.first().ecPublicKey)
        requesterCertChain = info.x5c
        for (cert in requesterCertChain!!.certificates) {
            println("cert: ${cert.toPem()}")
        }
        info.claimsSet
    } else {
        preReq
    }

    val origin = presentmentMechanism.webOrigin
        ?: throw IllegalArgumentException("origin is not available")

    val response = OpenID4VP.generateResponse(
        version = version,
        document = presentmentMechanism.document,
        source = source,
        showDocumentPicker = null,
        showConsentPrompt = showConsentPrompt,
        origin = origin,
        request = req,
        requesterCertChain = requesterCertChain,
    )
    presentmentMechanism.sendResponse(
        protocol = presentmentMechanism.protocol,
        data = response
    )
}

@OptIn(ExperimentalEncodingApi::class)
private suspend fun digitalCredentialsArfProtocol(
    documentTypeRepository: DocumentTypeRepository,
    presentmentMechanism: DigitalCredentialsPresentmentMechanism,
    source: PresentmentSource,
    showConsentPrompt: suspend (
        document: Document,
        request: Request,
        trustPoint: TrustPoint?
    ) -> Boolean
) {
    val arfRequest = presentmentMechanism.data
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
    val mdocCredential = source.selectCredential(
        document = presentmentMechanism.document,
        request = requestBeforeFiltering,
        keyAgreementPossible = emptyList()
    ) as MdocCredential?
    if (mdocCredential == null) {
        Logger.w(TAG, "No credential found for docType ${docRequest.docType}")
        return
    }

    val request = docRequest.toMdocRequest(
        documentTypeRepository = documentTypeRepository,
        mdocCredential = mdocCredential,
        requesterAppId = presentmentMechanism.appId,
        requesterWebsiteOrigin = presentmentMechanism.webOrigin,
    )
    val trustPoint = source.findTrustPoint(request)
    if (!showConsentPrompt(mdocCredential.document, request, trustPoint)) {
        throw PresentmentCanceled("The user did not consent")
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

    val data = buildJsonObject {
        put("encryptedResponse", encryptedResponse.toBase64Url())
    }
    presentmentMechanism.sendResponse(
        protocol = presentmentMechanism.protocol,
        data = data
    )
    mdocCredential.increaseUsageCount()
}

@OptIn(ExperimentalEncodingApi::class)
private suspend fun digitalCredentialsMdocApiProtocol(
    documentTypeRepository: DocumentTypeRepository,
    presentmentMechanism: DigitalCredentialsPresentmentMechanism,
    source: PresentmentSource,
    showConsentPrompt: suspend (
        document: Document,
        request: Request,
        trustPoint: TrustPoint?
    ) -> Boolean
) {
    val arfRequest = presentmentMechanism.data
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
    val zkRequested = docRequest.zkSystemSpecs.isNotEmpty()

    val requestBeforeFiltering = docRequest.toMdocRequest(
        documentTypeRepository = documentTypeRepository,
        mdocCredential = null,
        requesterAppId = presentmentMechanism.appId,
        requesterWebsiteOrigin = presentmentMechanism.webOrigin,
    )

    var zkSystemMatch: ZkSystem? = null
    var zkSystemSpec: ZkSystemSpec? = null
    if (zkRequested) {
        val requesterSupportedZkSpecs = docRequest.zkSystemSpecs
        val zkSystemRepository = source.zkSystemRepository
        if (zkSystemRepository != null) {
            // Find the first ZK System that the requester supports and matches the document
            for (zkSpec in requesterSupportedZkSpecs) {
                val zkSystem = zkSystemRepository.lookup(zkSpec.system)
                if (zkSystem == null) {
                    continue
                }

                val matchingZkSystemSpec = zkSystem.getMatchingSystemSpec(
                    requesterSupportedZkSpecs,
                    requestBeforeFiltering
                )
                if (matchingZkSystemSpec != null) {
                    zkSystemMatch = zkSystem
                    zkSystemSpec = matchingZkSystemSpec
                    break
                }
            }
        }
    }

    val mdocCredential = source.selectCredential(
        document = presentmentMechanism.document,
        request = requestBeforeFiltering,
        // TODO: when we start supporting KeyAgreement for DC API note that it won't work with ZKP
        //   and we will need to do ECDSA there.
        keyAgreementPossible = emptyList()
    ) as MdocCredential?
    if (mdocCredential == null) {
        Logger.w(TAG, "No credential found for docType ${docRequest.docType}")
        return
    }

    val request = docRequest.toMdocRequest(
        documentTypeRepository = documentTypeRepository,
        mdocCredential = mdocCredential,
        requesterAppId = presentmentMechanism.appId,
        requesterWebsiteOrigin = presentmentMechanism.webOrigin,
    )
    val trustPoint = source.findTrustPoint(request)
    if (!showConsentPrompt(mdocCredential.document, request, trustPoint)) {
        throw PresentmentCanceled("The user did not consent")
    }

    val documentBytes = calcDocument(
        credential = mdocCredential,
        requestedClaims = request.requestedClaims,
        encodedSessionTranscript = encodedSessionTranscript,
    )

    val deviceResponseGenerator = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)
    if (zkSystemMatch != null) {
        val zkDocument = zkSystemMatch.generateProof(
            zkSystemSpec!!,
            ByteString(documentBytes),
            ByteString(encodedSessionTranscript)
        )
        deviceResponseGenerator.addZkDocument(zkDocument)
    } else {
        deviceResponseGenerator.addDocument(documentBytes)
    }
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

    val data = buildJsonObject {
        put("response", encryptedResponse.toBase64Url())
    }
    presentmentMechanism.sendResponse(
        protocol = presentmentMechanism.protocol,
        data = data
    )
    mdocCredential.increaseUsageCount()
}

private suspend fun calcDocument(
    credential: MdocCredential,
    requestedClaims: List<MdocRequestedClaim>,
    encodedSessionTranscript: ByteArray
): ByteArray {
    // TODO: support MAC keys from v1.1 request and use setDeviceNamespacesMac() when possible
    //   depending on the value of PresentmentSource.preferSignatureToKeyAgreement(). See also
    //   calcDocument in mdocPresentment.kt.
    //

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
