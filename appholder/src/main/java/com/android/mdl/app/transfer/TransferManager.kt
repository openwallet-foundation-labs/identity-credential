package com.android.mdl.app.transfer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color.BLACK
import android.graphics.Color.WHITE
import android.nfc.cardemulation.HostApduService
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.identity.*
import com.android.identity.android.legacy.*
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.securearea.AndroidKeystoreSecureArea.USER_AUTHENTICATION_TYPE_BIOMETRIC
import com.android.identity.android.securearea.AndroidKeystoreSecureArea.USER_AUTHENTICATION_TYPE_LSKF
import com.android.identity.credential.CredentialRequest
import com.android.identity.credential.NameSpacedData
import com.android.identity.mdoc.mso.StaticAuthDataParser
import com.android.identity.mdoc.origininfo.OriginInfo
import com.android.identity.mdoc.request.DeviceRequestParser
import com.android.identity.mdoc.response.DeviceResponseGenerator
import com.android.identity.mdoc.response.DocumentGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.securearea.SecureArea
import com.android.identity.util.Timestamp
import com.android.mdl.app.document.DocumentManager
import com.android.mdl.app.documentdata.DocumentDataReader
import com.android.mdl.app.documentdata.DocumentElements
import com.android.mdl.app.selfsigned.AddSelfSignedScreenState
import com.android.mdl.app.util.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import java.util.*

class TransferManager private constructor(private val context: Context) {

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: TransferManager? = null

        fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: TransferManager(context).also { instance = it }
            }
    }

    private var reversedQrCommunicationSetup: ReverseQrCommunicationSetup? = null
    private var qrCommunicationSetup: QrCommunicationSetup? = null
    private var hostApduService: HostApduService? = null
    private var hasStarted = false

    private lateinit var communication: Communication

    private var transferStatusLd = MutableLiveData<TransferStatus>()

    fun setCommunication(session: PresentationSession, communication: Communication) {
        this.communication = communication
    }

    fun getTransferStatus(): LiveData<TransferStatus> = transferStatusLd

    fun updateStatus(status: TransferStatus) {
        transferStatusLd.value = status
    }

    fun documentRequests(): Collection<DeviceRequestParser.DocumentRequest> {
        return communication.getDeviceRequest().documentRequests
    }

    fun startPresentationReverseEngagement(
        reverseEngagementUri: String,
        origins: List<OriginInfo>
    ) {
        if (hasStarted) {
            throw IllegalStateException("Transfer has already started.")
        }
        communication = Communication.getInstance(context)
        reversedQrCommunicationSetup = ReverseQrCommunicationSetup(
            context = context,
            onPresentationReady = { session, presentation ->
                communication.setupPresentation(presentation)
            },
            onNewRequest = { request ->
                communication.setDeviceRequest(request)
                transferStatusLd.value = TransferStatus.REQUEST
            },
            onDisconnected = { transferStatusLd.value = TransferStatus.DISCONNECTED },
            onCommunicationError = { error ->
                log("onError: ${error.message}")
                transferStatusLd.value = TransferStatus.ERROR
            }
        ).apply {
            configure(reverseEngagementUri, origins)
        }
        hasStarted = true
    }

    fun startQrEngagement() {
        if (hasStarted) {
            throw IllegalStateException("Transfer has already started.")
        }
        communication = Communication.getInstance(context)
        qrCommunicationSetup = QrCommunicationSetup(
            context = context,
            onConnecting = { transferStatusLd.value = TransferStatus.CONNECTING },
            onQrEngagementReady = { transferStatusLd.value = TransferStatus.QR_ENGAGEMENT_READY },
            onDeviceRetrievalHelperReady = { session, deviceRetrievalHelper ->
                communication.setupPresentation(deviceRetrievalHelper)
                transferStatusLd.value = TransferStatus.CONNECTED
            },
            onNewDeviceRequest = { deviceRequest ->
                communication.setDeviceRequest(deviceRequest)
                transferStatusLd.value = TransferStatus.REQUEST
            },
            onSendResponseApdu = { responseApdu -> hostApduService?.sendResponseApdu(responseApdu) },
            onDisconnected = { transferStatusLd.value = TransferStatus.DISCONNECTED },
            onCommunicationError = { error ->
                log("onError: ${error.message}")
                transferStatusLd.value = TransferStatus.ERROR
            }
        ).apply {
            configure()
        }
        hasStarted = true
    }

    fun getDeviceEngagementQrCode(): View {
        val deviceEngagementForQrCode = qrCommunicationSetup!!.deviceEngagementUriEncoded
        val qrCodeBitmap = encodeQRCodeAsBitmap(deviceEngagementForQrCode)
        val qrCodeView = ImageView(context)
        qrCodeView.setImageBitmap(qrCodeBitmap)

        return qrCodeView
    }

    private fun encodeQRCodeAsBitmap(str: String): Bitmap {
        val width = 800
        val result: BitMatrix = try {
            MultiFormatWriter().encode(
                str,
                BarcodeFormat.QR_CODE, width, width, null
            )
        } catch (e: WriterException) {
            throw java.lang.IllegalArgumentException(e)
        }
        val w = result.width
        val h = result.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] = if (result[x, y]) BLACK else WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, w, h)
        return bitmap
    }

    @Throws(IllegalStateException::class)
    fun addDocumentToResponse(
        credentialName: String,
        docType: String,
        issuerSignedEntriesToRequest: MutableMap<String, Collection<String>>,
        deviceResponseGenerator: DeviceResponseGenerator,
        keyUnlockData: SecureArea.KeyUnlockData?
    ): AddDocumentToResponseResult {
        var signingKeyUsageLimitPassed = false
        val documentManager = DocumentManager.getInstance(context)
        val documentInformation = documentManager.getDocumentInformation(credentialName)
        requireValidProperty(documentInformation) { "Document not found!" }

        val credential = requireNotNull(documentManager.getCredentialByName(credentialName))
        val dataElements = issuerSignedEntriesToRequest.keys.flatMap { key ->
            issuerSignedEntriesToRequest.getOrDefault(key, emptyList()).map { value ->
                CredentialRequest.DataElement(key, value, false)
            }
        }

        val request = CredentialRequest(dataElements)
        val authKey = credential.findAuthenticationKey(Timestamp.now())
            ?: throw IllegalStateException("No auth key available")
        if (authKey.usageCount >= documentInformation.maxUsagesPerKey) {
            logWarning("Using Auth Key previously used ${authKey.usageCount} times, and maxUsagesPerKey is ${documentInformation.maxUsagesPerKey}")
            signingKeyUsageLimitPassed = true
        }

        val staticAuthData = StaticAuthDataParser(authKey.issuerProvidedData).parse()
        val mergedIssuerNamespaces = MdocUtil.mergeIssuerNamesSpaces(
            request, credential.nameSpacedData, staticAuthData
        )

        val transcript = communication.getSessionTranscript() ?: byteArrayOf()
        val authOption =
            AddSelfSignedScreenState.MdocAuthStateOption.valueOf(documentInformation.mDocAuthOption)
        try {
            val generator = DocumentGenerator(docType, staticAuthData.issuerAuth, transcript)
                .setIssuerNamespaces(mergedIssuerNamespaces)
            if (authOption == AddSelfSignedScreenState.MdocAuthStateOption.ECDSA) {
                generator.setDeviceNamespacesSignature(
                    NameSpacedData.Builder().build(),
                    authKey.secureArea,
                    authKey.alias,
                    keyUnlockData,
                    SecureArea.ALGORITHM_ES256
                )
            } else {
                generator.setDeviceNamespacesMac(
                    NameSpacedData.Builder().build(),
                    authKey.secureArea,
                    authKey.alias,
                    keyUnlockData,
                    authKey.attestation.first().publicKey
                )
            }
            val data = generator.generate()
            deviceResponseGenerator.addDocument(data)
            authKey.increaseUsageCount()
            ProvisioningUtil.getInstance(context).trackUsageTimestamp(credential)
        } catch (lockedException: SecureArea.KeyLockedException) {
            return if (credential.credentialSecureArea is AndroidKeystoreSecureArea) {
                val keyInfo =
                    credential.credentialSecureArea.getKeyInfo(authKey.alias) as AndroidKeystoreSecureArea.KeyInfo
                val allowLskf = keyInfo.userAuthenticationType == USER_AUTHENTICATION_TYPE_LSKF
                val allowBiometric =
                    keyInfo.userAuthenticationType == USER_AUTHENTICATION_TYPE_BIOMETRIC
                val allowBoth =
                    keyInfo.userAuthenticationType == USER_AUTHENTICATION_TYPE_LSKF or USER_AUTHENTICATION_TYPE_BIOMETRIC
                AddDocumentToResponseResult.UserAuthRequired(
                    keyAlias = authKey.alias,
                    allowLSKFUnlocking = allowLskf || allowBoth,
                    allowBiometricUnlocking = allowBiometric || allowBoth
                )
            } else {
                AddDocumentToResponseResult.PassphraseRequired(
                    attemptedWithIncorrectPassword = keyUnlockData != null
                )
            }
        }
        return AddDocumentToResponseResult.DocumentAdded(signingKeyUsageLimitPassed)
    }

    fun stopPresentation(
        sendSessionTerminationMessage: Boolean,
        useTransportSpecificSessionTermination: Boolean
    ) {
        communication.stopPresentation(
            sendSessionTerminationMessage,
            useTransportSpecificSessionTermination
        )
        disconnect()
    }

    fun disconnect() {
        communication.disconnect()
        qrCommunicationSetup?.close()
        transferStatusLd = MutableLiveData<TransferStatus>()
        destroy()
    }

    fun destroy() {
        qrCommunicationSetup = null
        reversedQrCommunicationSetup = null
        hasStarted = false
    }

    fun sendResponse(deviceResponse: ByteArray, closeAfterSending: Boolean) {
        communication.sendResponse(deviceResponse, closeAfterSending)
        if (closeAfterSending) {
            disconnect()
        }
    }

    fun readDocumentEntries(documentName: String): DocumentElements {
        val documentManager = DocumentManager.getInstance(context)
        val documentInformation = documentManager.getDocumentInformation(documentName)

        val credential = requireNotNull(documentManager.getCredentialByName(documentName))
        val nameSpacedData = credential.nameSpacedData
        return DocumentDataReader(documentInformation?.docType ?: "").read(nameSpacedData)
    }

    fun setResponseServed() {
        transferStatusLd.value = TransferStatus.REQUEST_SERVED
    }
}