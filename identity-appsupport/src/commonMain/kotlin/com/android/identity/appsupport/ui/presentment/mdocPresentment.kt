package com.android.identity.appsupport.ui.presentment

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.Tagged
import com.android.identity.credential.Credential
import com.android.identity.crypto.EcPublicKey
import com.android.identity.document.Document
import com.android.identity.document.NameSpacedData
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.mdoc.issuersigned.IssuerNamespaces
import com.android.identity.mdoc.mso.MobileSecurityObjectParser
import com.android.identity.mdoc.request.DeviceRequestParser
import com.android.identity.mdoc.response.DeviceResponseGenerator
import com.android.identity.mdoc.response.DocumentGenerator
import com.android.identity.mdoc.sessionencryption.SessionEncryption
import com.android.identity.mdoc.transport.MdocTransport
import com.android.identity.mdoc.transport.MdocTransportClosedException
import com.android.identity.mdoc.util.toMdocRequest
import com.android.identity.request.MdocRequestedClaim
import com.android.identity.request.Request
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.KeyUnlockInteractive
import com.android.identity.trustmanagement.TrustPoint
import com.android.identity.util.Constants
import com.android.identity.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

private const val TAG = "mdocPresentment"

internal suspend fun mdocPresentment(
    documentTypeRepository: DocumentTypeRepository,
    source: PresentmentSource,
    model: PresentmentModel,
    mechanism: MdocPresentmentMechanism,
    dismissable: MutableStateFlow<Boolean>,
    numRequestsServed: MutableStateFlow<Int>,
    showCredentialPicker: suspend (
        documents: List<Credential>,
    ) -> Credential?,
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
        model.setCompleted(Error("Expected state CONNECTED but found ${transport.state.value}"))
        return
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
                model.setCompleted()
                break
            }

            if (sessionEncryption == null) {
                val eReaderKey = SessionEncryption.getEReaderKey(sessionData)
                encodedSessionTranscript =
                    Cbor.encode(
                        CborArray.builder()
                            .add(Tagged(24, Bstr(mechanism.encodedDeviceEngagement.toByteArray())))
                            .add(Tagged(24, Bstr(Cbor.encode(eReaderKey.toCoseKey().toDataItem()))))
                            .add(mechanism.handover)
                            .end()
                            .build()
                    )
                sessionEncryption = SessionEncryption(
                    SessionEncryption.Role.MDOC,
                    mechanism.eDeviceKey,
                    eReaderKey,
                    encodedSessionTranscript,
                )
            }
            val (encodedDeviceRequest, status) = sessionEncryption.decryptMessage(sessionData)

            if (status == Constants.SESSION_DATA_STATUS_SESSION_TERMINATION) {
                Logger.i(TAG, "Received session termination message from reader")
                model.setCompleted()
                break
            }

            val deviceResponseGenerator = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)

            val deviceRequest = DeviceRequestParser(
                encodedDeviceRequest!!,
                encodedSessionTranscript!!,
            ).parse()
            for (docRequest in deviceRequest.docRequests) {
                val mdocCredentials = source.selectCredentialForPresentment(
                    request = docRequest.toMdocRequest(
                        documentTypeRepository = documentTypeRepository,
                        mdocCredential = null
                    ),
                    preSelectedDocument = null
                )
                if (mdocCredentials.isEmpty()) {
                    Logger.w(TAG, "No credential found for docType ${docRequest.docType}")
                    // No credential was found
                    continue
                }
                val mdocCredential = if (mdocCredentials.size == 1) {
                    mdocCredentials[0]
                } else {
                    // Returns null if user opted to not present credential.
                    showCredentialPicker(mdocCredentials)
                        ?: continue
                } as MdocCredential

                val request = docRequest.toMdocRequest(
                    documentTypeRepository = documentTypeRepository,
                    mdocCredential = mdocCredential
                )
                // TODO: deal with request.requestedClaims.size == 0, probably tell the user there is no
                // credential that can satisfy the request...
                //
                val trustPoint = source.findTrustPoint(request)
                val shouldShowConsentPrompt = source.shouldShowConsentPrompt(
                    credential = mdocCredential,
                    request = request,
                )
                if (shouldShowConsentPrompt) {
                    if (!showConsentPrompt(mdocCredential.document, request, trustPoint)) {
                        continue
                    }
                }

                deviceResponseGenerator.addDocument(calcDocument(
                    credential = mdocCredential,
                    requestedClaims = request.requestedClaims,
                    encodedSessionTranscript = encodedSessionTranscript,
                    eReaderKey = SessionEncryption.getEReaderKey(sessionData),
                    source = source,
                    request = request
                ))
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
                model.setCompleted()
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
        model.setCompleted()
    } catch (error: Throwable) {
        Logger.e(TAG, "Caught exception", error)
        error.printStackTrace()
        model.setCompleted(error)
    }
}

private suspend fun calcDocument(
    credential: MdocCredential,
    requestedClaims: List<MdocRequestedClaim>,
    encodedSessionTranscript: ByteArray,
    eReaderKey: EcPublicKey,
    source: PresentmentSource,
    request: Request
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

    // Prefer MACing if possible... this requires that we can do key agreement (AGREE_KEY purpose)
    // and that the curve of DeviceKey matches EReaderKey...
    //
    // TODO: support MAC keys from v1.1 request.
    //
    val keyInfo = credential.secureArea.getKeyInfo(credential.alias)
    val preferSignature = source.shouldPreferSignatureToKeyAgreement(credential, request)
    val keyAgreementPossible = keyInfo.keyPurposes.contains(KeyPurpose.AGREE_KEY) && eReaderKey.curve == keyInfo.publicKey.curve
    if (!preferSignature && keyAgreementPossible) {
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
