package com.android.mdl.app.transfer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color.BLACK
import android.graphics.Color.WHITE
import android.net.Uri
import android.nfc.cardemulation.HostApduService
import android.os.Build
import android.util.Log
import android.util.Base64
import android.view.View
import android.widget.ImageView
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.util.component1
import androidx.core.util.component2
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.identity.*
import com.android.identity.DeviceRequestParser.DeviceRequest
import com.android.mdl.app.document.Document
import com.android.mdl.app.documentdata.RequestMdl
import com.android.mdl.app.documentdata.RequestMicovAtt
import com.android.mdl.app.documentdata.RequestMicovVtr
import com.android.mdl.app.documentdata.RequestMvr
import com.android.mdl.app.util.DocumentData
import com.android.mdl.app.util.FormatUtil
import com.android.mdl.app.util.PreferencesHelper
import com.android.mdl.app.util.TransferStatus
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import java.util.*
import java.util.concurrent.Executor
import kotlin.collections.ArrayList


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

    private var store: IdentityCredentialStore? = null
    private var session: PresentationSession? = null
    private var qrEngagement: QrEngagementHelper? = null
    private var nfcEngagement: NfcEngagementHelper? = null
    private var presentation: PresentationHelper? = null
    private var hasStarted = false
    private var isTranscriptSet: Boolean = false

    private var hostApduService : HostApduService? = null

    private val nfcApduRouter : NfcApduRouter = object :
        NfcApduRouter() {
        override fun sendResponseApdu(responseApdu: ByteArray) {
            hostApduService!!.sendResponseApdu(responseApdu)
        }
    }


    var requestBytes: ByteArray? = null
        private set
    private var transferStatusLd = MutableLiveData<TransferStatus>()

    fun getTransferStatus(): LiveData<TransferStatus> = transferStatusLd

    fun initiate() {

        // Create identity credential store from hardware or software implementation depending on
        // what was used to store the first document on this device.
        store = if (PreferencesHelper.isHardwareBacked(context))
            IdentityCredentialStore.getHardwareInstance(context)
                ?: IdentityCredentialStore.getKeystoreInstance(context,
                    PreferencesHelper.getKeystoreBackedStorageLocation(context))
        else
            IdentityCredentialStore.getKeystoreInstance(context,
                PreferencesHelper.getKeystoreBackedStorageLocation(context))

        session = store?.createPresentationSession(
            IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256)
    }

    @Throws(IllegalStateException::class)
    fun startPresentation() {
        if (hasStarted)
            throw IllegalStateException("Transfer has already started.")

        // Get an instance of the presentation based on the document
        initiate()

        nfcEngagement = NfcEngagementHelper(context,
            session!!,
            getConnectionMethods(),
            getConnectionOptions(),
            nfcApduRouter,
            nfcEngagementListener, context.mainExecutor())
    }

    fun startPresentationReverseEngagement(reverseEngagementUri: String,
                                           originInfos : List<OriginInfo>) {
        if (hasStarted) {
            throw IllegalStateException("Transfer has already started.")
        }

        initiate()

        val uri = Uri.parse(reverseEngagementUri)
        if (!uri.scheme.equals("mdoc")) {
            throw IllegalStateException("Only supports mdoc URIs")
        }
        val encodedReaderEngagement = Base64.decode(uri.encodedSchemeSpecificPart,
            Base64.URL_SAFE or Base64.NO_PADDING)
        val engagement = EngagementParser(encodedReaderEngagement).parse()
        if (engagement.connectionMethods.size == 0) {
            throw IllegalStateException("No connection methods in engagement")
        }

        // For now, just pick the first transport
        val connectionMethod = engagement.connectionMethods[0]
        Log.d(LOG_TAG, "Using connection method " + connectionMethod)

        val transport = connectionMethod.createDataTransport(context, getConnectionOptions())

        val builder = PresentationHelper.Builder(context,
            presentationListener,
            context.mainExecutor(),
            session!!)
            .useReverseEngagement(transport, encodedReaderEngagement, originInfos)
        presentation = builder.build()
        presentation?.setSendSessionTerminationMessage(true)
        hasStarted = true

    }

    fun startQrEngagement() {
        qrEngagement = QrEngagementHelper(context,
            session!!,
            getConnectionMethods(),
            getConnectionOptions(),
            nfcApduRouter,
            qrEngagementListener, context.mainExecutor())
    }

    private fun getConnectionOptions(): DataTransportOptions {
        val builder = DataTransportOptions.Builder()
            .setBleUseL2CAP(PreferencesHelper.isBleL2capEnabled(context))
            .setBleClearCache(PreferencesHelper.isBleClearCacheEnabled(context))
        return builder.build()
    }

    private fun getConnectionMethods(): List<ConnectionMethod> {
        var connectionMethods = ArrayList<ConnectionMethod>()
        if (PreferencesHelper.isBleDataRetrievalEnabled(context)) {
            connectionMethods.add(ConnectionMethodBle(
                false,
                true,
                null,
                UUID.randomUUID()))
        }
        if (PreferencesHelper.isBleDataRetrievalPeripheralModeEnabled(context)) {
            connectionMethods.add(ConnectionMethodBle(
                true,
                false,
                UUID.randomUUID(),
                null))
        }
        if (PreferencesHelper.isWifiDataRetrievalEnabled(context)) {
            connectionMethods.add(ConnectionMethodWifiAware(
                null,
                OptionalLong.empty(),
                OptionalLong.empty(),
                null))
        }
        if (PreferencesHelper.isNfcDataRetrievalEnabled(context)) {
            // TODO: Add API to ConnectionMethodNfc to get sizes appropriate for the device
            connectionMethods.add(ConnectionMethodNfc(
                0xffff,
                0x10000));
        }
        return connectionMethods
    }

    private val qrEngagementListener: QrEngagementHelper.Listener = object :
        QrEngagementHelper.Listener {

        override fun onDeviceEngagementReady() {
            transferStatusLd.value = TransferStatus.QR_ENGAGEMENT_READY
        }

        override fun onDeviceConnecting() {
            transferStatusLd.value = TransferStatus.CONNECTING
        }

        override fun onDeviceConnected(transport: DataTransport) {
            if (presentation != null) {
                Log.i(LOG_TAG, "OnDeviceConnected for QR engagement: ignoring since we already have a presentation")
                return
            }

            // OK, we got a connection via a QR transport, fire up PresentationHelper!
            Log.d(LOG_TAG, "onDeviceConnected via QR: nfcEngagement=${nfcEngagement} qrEngagement=${qrEngagement}")
            val builder = PresentationHelper.Builder(context,
                presentationListener,
                context.mainExecutor(),
                session!!)
            builder.useForwardEngagement(transport, qrEngagement!!.deviceEngagement, qrEngagement!!.handover)
            presentation = builder.build()
            presentation?.setSendSessionTerminationMessage(true)
            hasStarted = true

            // Shut down all engagement
            qrEngagement?.close()
            qrEngagement = null
            nfcEngagement?.close()
            nfcEngagement = null

            transferStatusLd.value = TransferStatus.CONNECTED
        }

        override fun onError(error: Throwable) {
            Log.e(LOG_TAG, "QR onError: ${error.message}")
            transferStatusLd.value = TransferStatus.ERROR
        }
    }

    private val nfcEngagementListener: NfcEngagementHelper.Listener = object :
        NfcEngagementHelper.Listener {

        override fun onDeviceConnecting() {
            transferStatusLd.value = TransferStatus.CONNECTING
        }

        override fun onDeviceConnected(transport: DataTransport) {
            if (presentation != null) {
                Log.i(LOG_TAG, "OnDeviceConnected for NFC engagement: ignoring since we already have a presentation")
                return
            }

            // OK, we got a connection via a NFC transport, fire up PresentationHelper!
            Log.d(LOG_TAG, "onDeviceConnected via NFC: nfcEngagement=${nfcEngagement} qrEngagement=${qrEngagement}")
            val builder = PresentationHelper.Builder(context,
                presentationListener,
                context.mainExecutor(),
                session!!)
            builder.useForwardEngagement(transport, nfcEngagement!!.deviceEngagement, nfcEngagement!!.handover)
            // This _could_ be NFC data retrieval using QR code engagement, so also pass QR
            // engagement as an alternative way to engage.
            builder.addAlternateForwardEngagement(qrEngagement?.deviceEngagement, qrEngagement?.handover)
            presentation = builder.build()
            presentation?.setSendSessionTerminationMessage(true)
            hasStarted = true

            // Shut down all engagement
            qrEngagement?.close()
            qrEngagement = null
            nfcEngagement?.close()
            nfcEngagement = null

            transferStatusLd.value = TransferStatus.CONNECTED
        }

        override fun onError(error: Throwable) {
            Log.e(LOG_TAG, "NFC onError: ${error.message}")
            transferStatusLd.value = TransferStatus.ERROR
        }
    }

    private val presentationListener: PresentationHelper.Listener = object :
        PresentationHelper.Listener {

        override fun onDeviceRequest(deviceRequestBytes: ByteArray) {
            requestBytes = deviceRequestBytes
            transferStatusLd.value = TransferStatus.REQUEST
        }

        override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {
            transferStatusLd.value = TransferStatus.DISCONNECTED
        }

        override fun onError(error: Throwable) {
            Log.e(LOG_TAG, "onError: ${error.message}")
            transferStatusLd.value = TransferStatus.ERROR
        }
    }

    private fun Context.mainExecutor(): Executor {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mainExecutor
        } else {
            ContextCompat.getMainExecutor(context)
        }
    }

    fun getDeviceEngagementQrCode(): View {
        val deviceEngagementForQrCode = qrEngagement!!.deviceEngagementUriEncoded
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

    fun nfcOnDeactivated(service: HostApduService, aid: ByteArray, reason: Int) {
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

    fun destroy() {
        requestBytes = null
        store = null
        qrEngagement = null
        nfcEngagement = null
        presentation = null
        session = null
        isTranscriptSet = false
    }

    fun stopPresentation(
        sendSessionTerminationMessage: Boolean,
        useTransportSpecificSessionTermination: Boolean
    ) {
        presentation?.setSendSessionTerminationMessage(sendSessionTerminationMessage)
        try {
            if (presentation?.isTransportSpecificTerminationSupported == true && useTransportSpecificSessionTermination) {
                presentation?.setUseTransportSpecificSessionTermination(true)
            }
        } catch (e: IllegalStateException) {
            Log.e(LOG_TAG, "Error ignored.", e)
        }
        disconnect()
    }

    fun disconnect() {
        try {
            qrEngagement?.close()
        } catch (e: RuntimeException) {
            Log.e(LOG_TAG, "Error ignored closing qrEngagement", e)
        }
        try {
            nfcEngagement?.close()
        } catch (e: RuntimeException) {
            Log.e(LOG_TAG, "Error ignored closing nfcEngagement", e)
        }
        try {
            presentation?.disconnect()
        } catch (e: RuntimeException) {
            Log.e(LOG_TAG, "Error ignored closing presentation", e)
        }
        transferStatusLd = MutableLiveData<TransferStatus>()
        destroy()
        hasStarted = false
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
        val progressListener: (Long, Long) -> Unit = { progress, max ->
            Log.d(LOG_TAG, "Progress: $progress of $max")
            if (progress == max) {
                Log.d(LOG_TAG, "Completed...")
                // We could for example force a disconnect here
                //presentation?.setSendSessionTerminationMessage(true)
                //presentation?.disconnect()
            }
        }
        presentation?.sendDeviceResponse(deviceResponse, progressListener, context.mainExecutor())
    }

    fun getDeviceRequest(): DeviceRequest {
        requestBytes?.let { rb ->
            presentation?.let { p ->
                val parser = DeviceRequestParser()
                parser.setSessionTranscript(p.sessionTranscript)
                parser.setDeviceRequest(rb)
                return parser.parse()
            } ?: throw IllegalStateException("Presentation is null")
        } ?: throw IllegalStateException("Request not received")
    }

    fun readDocumentEntries(document: Document): CredentialDataResult.Entries? {
        // Request all data items based on doctype
        val entriesToRequest = if (DocumentData.MDL_DOCTYPE == document.docType) {
            RequestMdl.getFullItemsToRequest()
        } else if (DocumentData.MVR_DOCTYPE == document.docType) {
            RequestMvr.getFullItemsToRequest()
        } else if (DocumentData.MICOV_DOCTYPE == document.docType) {
            RequestMicovAtt.getFullItemsToRequest().plus(RequestMicovVtr.getFullItemsToRequest())
        } else {
            throw IllegalArgumentException("Invalid docType to create request details ${document.docType}")
        }

        val credentialRequest = CredentialDataRequest.Builder()
            .setIncrementUseCount(false)
            .setIssuerSignedEntriesToRequest(entriesToRequest)
            .build()

        if (!isTranscriptSet) {
            session?.setSessionTranscript(byteArrayOf(0))
            isTranscriptSet = true
        }

        // It can display data if user consent is not required
        val credentialData = session?.getCredentialData(document.identityCredentialName, credentialRequest)
        return credentialData?.issuerSignedEntries
    }
}