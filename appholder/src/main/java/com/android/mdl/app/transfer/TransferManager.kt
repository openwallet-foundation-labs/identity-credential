package com.android.mdl.app.transfer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color.BLACK
import android.graphics.Color.WHITE
import android.nfc.cardemulation.HostApduService
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.biometric.BiometricPrompt
import androidx.core.util.component1
import androidx.core.util.component2
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.identity.*
import com.android.mdl.app.document.Document
import com.android.mdl.app.documentdata.RequestMdl
import com.android.mdl.app.documentdata.RequestMicovAtt
import com.android.mdl.app.documentdata.RequestMicovVtr
import com.android.mdl.app.documentdata.RequestMvr
import com.android.mdl.app.util.DocumentData
import com.android.mdl.app.util.FormatUtil
import com.android.mdl.app.util.PreferencesHelper
import com.android.mdl.app.util.TransferStatus
import com.android.mdl.app.util.log
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import java.util.*

class TransferManager private constructor(private val context: Context) {

    companion object {
        private const val LOG_TAG = "TransferManager"

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
    private var session: PresentationSession? = null
    private var hasStarted = false

    private lateinit var communication: Communication

    private var transferStatusLd = MutableLiveData<TransferStatus>()

    private val nfcApduRouter: NfcApduRouter = object : NfcApduRouter() {
        override fun sendResponseApdu(responseApdu: ByteArray) {
            hostApduService!!.sendResponseApdu(responseApdu)
        }
    }

    fun setCommunication(session: PresentationSession, communication: Communication) {
        this.session = session
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
                this.session = session
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
            onPresentationReady = { session, presentation ->
                this.session = session
                communication.setupPresentation(presentation)
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

    fun nfcProcessCommandApdu(service: HostApduService, aid: ByteArray, commandApdu: ByteArray) {
        hostApduService = service
        nfcApduRouter.addReceivedApdu(aid, commandApdu)
    }

    fun nfcOnDeactivated(aid: ByteArray, reason: Int) {
        nfcApduRouter.addDeactivated(aid, reason)
    }

    @Throws(IllegalStateException::class)
    fun addDocumentToResponse(
        credentialName: String,
        docType: String,
        issuerSignedEntriesToRequest: MutableMap<String, Collection<String>>,
        response: DeviceResponseGenerator,
        readerAuth: ByteArray?,
        requestMessage: ByteArray?
    ): Boolean {
        session?.let {
            val credentialDataRequestBuilder = CredentialDataRequest.Builder()
                .setIssuerSignedEntriesToRequest(issuerSignedEntriesToRequest)
                .setAllowUsingExhaustedKeys(true)
                .setAllowUsingExpiredKeys(true)
            if (readerAuth != null && requestMessage != null) {
                credentialDataRequestBuilder.setReaderSignature(readerAuth)
                credentialDataRequestBuilder.setRequestMessage(requestMessage)
            }
            it.getCredentialData(
                credentialName,
                credentialDataRequestBuilder.build()
            )?.let { c ->
                try {
                    if (c.deviceSignedEntries.isUserAuthenticationNeeded ||
                        c.issuerSignedEntries.isUserAuthenticationNeeded
                    ) {
                        return true
                    }
                    val staticAuthData: ByteArray = c.staticAuthenticationData
                    val (first1, second1) = Utility.decodeStaticAuthData(staticAuthData)

                    Log.d(LOG_TAG, "StaticAuthData " + FormatUtil.encodeToString(staticAuthData))
                    response.addDocument(
                        docType,
                        c,
                        first1,
                        second1
                    )
                } catch (e: IllegalArgumentException) {
                    e.printStackTrace()
                } catch (e: NoAuthenticationKeyAvailableException) {
                    e.printStackTrace()
                } catch (e: InvalidReaderSignatureException) {
                    e.printStackTrace()
                } catch (e: EphemeralPublicKeyNotFoundException) {
                    e.printStackTrace()
                } catch (e: InvalidRequestMessageException) {
                    e.printStackTrace()
                }
            }
        }
        return false
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
        hasStarted = false
    }

    fun destroy() {
        qrCommunicationSetup = null
        reversedQrCommunicationSetup = null
        session = null
    }

    fun getCryptoObject(): BiometricPrompt.CryptoObject? {
        try {
            return session?.cryptoObject
        } catch (e: RuntimeException) {
            // Error when device doesn't have secure unlock
            Log.e(LOG_TAG, "getCryptoObject: ${e.message}")
        }
        return null
    }

    fun sendResponse(deviceResponse: ByteArray) {
        communication.sendResponse(deviceResponse)
    }

    fun readDocumentEntries(document: Document): CredentialDataResult.Entries? {
        // Request all data items based on docType
        val entriesToRequest = if (DocumentData.MDL_DOCTYPE == document.docType) {
            RequestMdl.getFullItemsToRequest()
        } else if (DocumentData.MVR_DOCTYPE == document.docType) {
            RequestMvr.getFullItemsToRequest()
        } else if (DocumentData.MICOV_DOCTYPE == document.docType) {
            RequestMicovAtt.getFullItemsToRequest().plus(RequestMicovVtr.getFullItemsToRequest())
        } else {
            throw IllegalArgumentException("Invalid docType to create request details ${document.docType}")
        }

        // Create identity credential store from hardware or software implementation depending on
        // what was used on each document provisioned
        val mStore = if (document.hardwareBacked)
            IdentityCredentialStore.getHardwareInstance(context)
                ?: IdentityCredentialStore.getKeystoreInstance(
                    context,
                    PreferencesHelper.getKeystoreBackedStorageLocation(context)
                )
        else
            IdentityCredentialStore.getKeystoreInstance(
                context,
                PreferencesHelper.getKeystoreBackedStorageLocation(context)
            )

        try {
            val mSession = mStore.createPresentationSession(
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256
            )

            val credentialRequest = CredentialDataRequest.Builder()
                .setIncrementUseCount(false)
                .setIssuerSignedEntriesToRequest(entriesToRequest)
                .build()

            // It can display data if user consent is not required
            val credentialData =
                mSession.getCredentialData(document.identityCredentialName, credentialRequest)
            return credentialData?.issuerSignedEntries
        } catch (e: UnsupportedOperationException) {
            Log.e(LOG_TAG, "Presentation session not supported in this device - ${e.message}", e)
            return null
        }
    }

    fun setResponseServed() {
        transferStatusLd.value = TransferStatus.REQUEST_SERVED
    }
}