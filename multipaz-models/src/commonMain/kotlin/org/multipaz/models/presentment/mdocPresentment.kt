package org.multipaz.models.presentment

import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.Tagged
import org.multipaz.crypto.EcPublicKey
import org.multipaz.document.Document
import org.multipaz.document.NameSpacedData
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.mdoc.issuersigned.IssuerNamespaces
import org.multipaz.mdoc.mso.MobileSecurityObjectParser
import org.multipaz.mdoc.request.DeviceRequestParser
import org.multipaz.mdoc.response.DeviceResponseGenerator
import org.multipaz.mdoc.response.DocumentGenerator
import org.multipaz.mdoc.sessionencryption.SessionEncryption
import org.multipaz.mdoc.transport.MdocTransport
import org.multipaz.mdoc.transport.MdocTransportClosedException
import org.multipaz.mdoc.util.toMdocRequest
import org.multipaz.request.MdocRequestedClaim
import org.multipaz.request.Request
import org.multipaz.securearea.KeyUnlockInteractive
import org.multipaz.trustmanagement.TrustPoint
import org.multipaz.util.Constants
import org.multipaz.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.buildCborArray
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.mdoc.zkp.ZkSystem
import org.multipaz.mdoc.zkp.ZkSystemSpec

private const val TAG = "mdocPresentment"

internal suspend fun mdocPresentment(
    documentTypeRepository: DocumentTypeRepository,
    source: PresentmentSource,
    mechanism: MdocPresentmentMechanism,
    dismissable: MutableStateFlow<Boolean>,
    numRequestsServed: MutableStateFlow<Int>,
    showDocumentPicker: suspend (
        documents: List<Document>,
    ) -> Document?,
    showConsentPrompt: suspend (
        document: Document,
        request: Request,
        trustPoint: TrustPoint?
    ) -> Boolean,
) {
    val transport = mechanism.transport
    // Wait until state changes to CONNECTED, FAILED, or CLOSED
    transport.state.first {
        it == MdocTransport.State.CONNECTED ||
                it == MdocTransport.State.FAILED ||
                it == MdocTransport.State.CLOSED
    }
    if (transport.state.value != MdocTransport.State.CONNECTED) {
        throw Error("Expected state CONNECTED but found ${transport.state.value}")
    }

    try {
        var sessionEncryption: SessionEncryption? = null
        var encodedSessionTranscript: ByteArray? = null
        while (true) {
            Logger.i(TAG, "Waiting for message from reader...")
            dismissable.value = true
            val sessionData = transport.waitForMessage()
            dismissable.value = false
            if (sessionData.isEmpty()) {
                Logger.i(TAG, "Received transport-specific session termination message from reader")
                break
            }

            if (sessionEncryption == null) {
                val eReaderKey = SessionEncryption.getEReaderKey(sessionData)
                encodedSessionTranscript =
                    Cbor.encode(
                        buildCborArray {
                            add(Tagged(24, Bstr(mechanism.encodedDeviceEngagement.toByteArray())))
                            add(Tagged(24, Bstr(Cbor.encode(eReaderKey.toCoseKey().toDataItem()))))
                            add(mechanism.handover)
                        }
                    )
                sessionEncryption = SessionEncryption(
                    MdocRole.MDOC,
                    mechanism.eDeviceKey,
                    eReaderKey,
                    encodedSessionTranscript,
                )
            }
            val (encodedDeviceRequest, status) = sessionEncryption.decryptMessage(sessionData)

            if (status == Constants.SESSION_DATA_STATUS_SESSION_TERMINATION) {
                Logger.i(TAG, "Received session termination message from reader")
                break
            }

            val deviceResponseGenerator = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)

            val deviceRequest = DeviceRequestParser(
                encodedDeviceRequest!!,
                encodedSessionTranscript!!,
            ).parse()
            for (docRequest in deviceRequest.docRequests) {
                val zkRequested = docRequest.zkSystemSpecs.isNotEmpty()

                val requestWithoutFiltering = docRequest.toMdocRequest(
                    documentTypeRepository = documentTypeRepository,
                    mdocCredential = null
                )
                val documents = source.getDocumentsMatchingRequest(
                    request = requestWithoutFiltering,
                )
                if (documents.isEmpty()) {
                    Logger.w(TAG, "No document found for docType ${docRequest.docType}")
                    // No document was found
                    continue
                }
                val document = if (documents.size == 1) {
                    documents[0]
                } else {
                    // Returns null if user opted to not present credential.
                    showDocumentPicker(documents)
                        ?: continue
                }

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
                                requestWithoutFiltering
                            )
                            if (matchingZkSystemSpec != null) {
                                zkSystemMatch = zkSystem
                                zkSystemSpec = matchingZkSystemSpec
                                break
                            }
                        }
                    }
                }

                if (zkRequested && zkSystemSpec == null) {
                    Logger.w(TAG, "Reader requested ZK proof but no compatible ZkSpec was found.")
                }

                val mdocCredential = source.selectCredential(
                    document = document,
                    request = requestWithoutFiltering,
                    // Check is zk is requested and a compatible ZK system spec was found
                    keyAgreementPossible = if (zkRequested && zkSystemSpec != null) {
                        listOf()
                    } else {
                        listOf(mechanism.eDeviceKey.curve)
                    }
                ) as MdocCredential?
                if (mdocCredential == null) {
                    Logger.w(TAG, "No credential found")
                    return
                }

                val request = docRequest.toMdocRequest(
                    documentTypeRepository = documentTypeRepository,
                    mdocCredential = mdocCredential
                )

                // TODO: deal with request.requestedClaims.size == 0, probably tell the user there is no
                // credential that can satisfy the request...
                //
                val trustPoint = source.findTrustPoint(request)
                if (!showConsentPrompt(mdocCredential.document, request, trustPoint)) {
                    continue
                }

                val documentBytes = calcDocument(
                    credential = mdocCredential,
                    requestedClaims = request.requestedClaims,
                    encodedSessionTranscript = encodedSessionTranscript,
                    eReaderKey = SessionEncryption.getEReaderKey(sessionData),
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

            val encodedDeviceResponse = deviceResponseGenerator.generate()
            transport.sendMessage(
                sessionEncryption.encryptMessage(
                    encodedDeviceResponse,
                    if (!mechanism.allowMultipleRequests) {
                        Constants.SESSION_DATA_STATUS_SESSION_TERMINATION
                    } else {
                        null
                    }
                )
            )
            numRequestsServed.value = numRequestsServed.value + 1
            if (!mechanism.allowMultipleRequests) {
                Logger.i(TAG, "Response sent, closing connection")
                break
            } else {
                Logger.i(TAG, "Response sent, keeping connection open")
            }
        }
    } catch (err: MdocTransportClosedException) {
        // Nothing to do, this is thrown when transport.close() is called from another coroutine, that
        // is, the X in the top-right
        err.printStackTrace()
        Logger.i(TAG, "Ending holderJob due to MdocTransportClosedException")
    }
}

private suspend fun calcDocument(
    credential: MdocCredential,
    requestedClaims: List<MdocRequestedClaim>,
    encodedSessionTranscript: ByteArray,
    eReaderKey: EcPublicKey,
): ByteArray {
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

    if (credential.secureArea.getKeyInfo(credential.alias).algorithm.isKeyAgreement) {
        documentGenerator.setDeviceNamespacesMac(
            dataElements = NameSpacedData.Builder().build(),
            secureArea = credential.secureArea,
            keyAlias = credential.alias,
            keyUnlockData = KeyUnlockInteractive(),
            eReaderKey = eReaderKey
        )
    } else {
        documentGenerator.setDeviceNamespacesSignature(
            NameSpacedData.Builder().build(),
            credential.secureArea,
            credential.alias,
            KeyUnlockInteractive(),
        )
    }

    return documentGenerator.generate()
}
