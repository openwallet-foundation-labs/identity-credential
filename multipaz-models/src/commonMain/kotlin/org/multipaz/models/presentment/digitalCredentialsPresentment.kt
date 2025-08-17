package org.multipaz.models.presentment

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.Simple
import org.multipaz.cbor.Tstr
import org.multipaz.cbor.addCborArray
import org.multipaz.cbor.addCborMap
import org.multipaz.cbor.buildCborArray
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
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
import org.multipaz.mdoc.util.toMdocRequest
import org.multipaz.mdoc.zkp.ZkSystem
import org.multipaz.mdoc.zkp.ZkSystemSpec
import org.multipaz.models.openid.OpenID4VP
import org.multipaz.presentment.CredentialPresentmentData
import org.multipaz.presentment.CredentialPresentmentSelection
import org.multipaz.request.MdocRequestedClaim
import org.multipaz.request.Requester
import org.multipaz.securearea.KeyUnlockInteractive
import org.multipaz.trustmanagement.TrustPoint
import org.multipaz.util.Constants
import org.multipaz.util.Logger
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
import kotlin.io.encoding.ExperimentalEncodingApi

private const val TAG = "digitalCredentialsPresentment"

internal suspend fun digitalCredentialsPresentment(
    documentTypeRepository: DocumentTypeRepository,
    source: PresentmentSource,
    mechanism: DigitalCredentialsPresentmentMechanism,
    dismissable: MutableStateFlow<Boolean>,
    showConsentPrompt: suspend (
        credentialPresentmentData: CredentialPresentmentData,
        preselectedDocuments: List<Document>,
        requester: Requester,
        trustPoint: TrustPoint?
    ) -> CredentialPresentmentSelection?,
) {
    Logger.i(TAG, "mechanism.protocol: ${mechanism.protocol}")
    Logger.i(TAG, "mechanism.request: ${mechanism.data}")
    dismissable.value = false
    when (mechanism.protocol) {
        "openid4vp", "openid4vp-v1-unsigned", "openid4vp-v1-signed" -> digitalCredentialsOpenID4VPProtocol(
            presentmentMechanism = mechanism,
            source = source,
            showConsentPrompt = showConsentPrompt,
        )
        "org.iso.mdoc", "org-iso-mdoc" -> digitalCredentialsMdocApiProtocol(
            documentTypeRepository = documentTypeRepository,
            presentmentMechanism = mechanism,
            source = source,
            showConsentPrompt = showConsentPrompt,
        )
        else -> throw Error("Protocol ${mechanism.protocol} is not supported")
    }
}

@OptIn(ExperimentalEncodingApi::class)
private suspend fun digitalCredentialsOpenID4VPProtocol(
    presentmentMechanism: DigitalCredentialsPresentmentMechanism,
    source: PresentmentSource,
    showConsentPrompt: suspend (
        credentialPresentmentData: CredentialPresentmentData,
        preselectedDocuments: List<Document>,
        requester: Requester,
        trustPoint: TrustPoint?
    ) -> CredentialPresentmentSelection?,
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
        preselectedDocuments = presentmentMechanism.preselectedDocuments,
        source = source,
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
private suspend fun digitalCredentialsMdocApiProtocol(
    documentTypeRepository: DocumentTypeRepository,
    presentmentMechanism: DigitalCredentialsPresentmentMechanism,
    source: PresentmentSource,
    showConsentPrompt: suspend (
        credentialPresentmentData: CredentialPresentmentData,
        preselectedDocuments: List<Document>,
        requester: Requester,
        trustPoint: TrustPoint?
    ) -> CredentialPresentmentSelection?,
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

    val deviceResponseGenerator = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)

    val deviceRequest = DeviceRequestParser(
        deviceRequestBase64.fromBase64Url(),
        encodedSessionTranscript,
    ).parse()
    for (docRequest in deviceRequest.docRequests) {
        val zkRequested = docRequest.zkSystemSpecs.isNotEmpty()

        val request = docRequest.toMdocRequest(
            documentTypeRepository = documentTypeRepository,
            mdocCredential = null,
            requesterWebsiteOrigin = presentmentMechanism.webOrigin,
            requesterAppId = presentmentMechanism.appId
        )
        val trustPoint = source.findTrustPoint(request.requester)

        val presentmentData = docRequest.getPresentmentData(
            documentTypeRepository = documentTypeRepository,
            source = source,
            keyAgreementPossible = listOf()
        )
        if (presentmentData == null) {
            Logger.w(TAG, "No document found for docType ${docRequest.docType}")
            // No document was found
            continue
        }

        val selection = if (source.skipConsentPrompt) {
            presentmentData.select(presentmentMechanism.preselectedDocuments)
        } else {
            showConsentPrompt(
                presentmentData,
                presentmentMechanism.preselectedDocuments,
                request.requester,
                trustPoint
            )
        }
        if (selection == null) {
            throw PresentmentCanceled("User canceled at document selection time")
        }

        val match = selection.matches[0]
        val mdocCredential = match.credential as MdocCredential

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
                        zkSystemSpecs = requesterSupportedZkSpecs,
                        requestedClaims = request.requestedClaims
                    )
                    if (matchingZkSystemSpec != null) {
                        zkSystemMatch = zkSystem
                        zkSystemSpec = matchingZkSystemSpec
                        break
                    }
                }
            }
        }

        val documentBytes = calcDocument(
            credential = mdocCredential,
            requestedClaims = request.requestedClaims,
            encodedSessionTranscript = encodedSessionTranscript,
        )

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
        mdocCredential.increaseUsageCount()
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
        val authMessage = credential.document.metadata.displayName?.let {
            "Authentication is required to share $it"
        } ?: "Authentication is required to share the document"
        documentGenerator.setDeviceNamespacesSignature(
            dataElements = NameSpacedData.Builder().build(),
            secureArea = credential.secureArea,
            keyAlias = credential.alias,
            keyUnlockData = KeyUnlockInteractive(
                title = "Verify it's you",
                subtitle = authMessage
            ),
        )
    }

    return documentGenerator.generate()
}
