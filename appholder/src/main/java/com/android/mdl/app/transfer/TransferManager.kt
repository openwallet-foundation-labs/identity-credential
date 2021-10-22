package com.android.mdl.app.transfer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color.BLACK
import android.graphics.Color.WHITE
import android.nfc.cardemulation.HostApduService
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.util.component1
import androidx.core.util.component2
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.security.identity.*
import androidx.security.identity.DeviceRequestParser.DeviceRequest
import com.android.mdl.app.util.FormatUtil
import com.android.mdl.app.util.PreferencesHelper
import com.android.mdl.app.util.TransferStatus
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import java.util.concurrent.Executor


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
    private var presentation: PresentationHelper? = null
    private var hasStarted = false

    var requestBytes: ByteArray? = null
        private set
    private var transferStatusLd = MutableLiveData<TransferStatus>()

    fun getTransferStatus(): LiveData<TransferStatus> = transferStatusLd

    private fun initiate() {

        // Create identity credential store from hardware or software implementation depending on
        // what was used to store the first document on this device.
        store = if (PreferencesHelper.isHardwareBacked(context))
            IdentityCredentialStore.getHardwareInstance(context)
                ?: IdentityCredentialStore.getSoftwareInstance(context)
        else
            IdentityCredentialStore.getSoftwareInstance(context)

        session = store?.createPresentationSession(
            IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256
        )?.also {
            presentation = PresentationHelper(context, it)
            presentation?.setLoggingFlags(PreferencesHelper.getLoggingFlags(context))
            presentation?.setSendSessionTerminationMessage(true)
        }
    }

    @Throws(IllegalStateException::class)
    fun startPresentation() {
        if (hasStarted)
            throw IllegalStateException("Transfer has already started. It is necessary to stop current presentation before starting a new one.")

        // Get an instance of the presentation based on the document
        initiate()

        var bleOptions = 0
        if (PreferencesHelper.isBleDataRetrievalEnabled(context)) {
            bleOptions += Constants.BLE_DATA_RETRIEVAL_OPTION_MDOC_CENTRAL_CLIENT_MODE
        }
        if (PreferencesHelper.isBleDataRetrievalPeripheralModeEnabled(context)) {
            bleOptions += Constants.BLE_DATA_RETRIEVAL_OPTION_MDOC_PERIPHERAL_SERVER_MODE
        }

        val dataRetrievalConfiguration = DataRetrievalListenerConfiguration.Builder()
            .setBleEnabled(bleOptions != 0)
            .setBleDataRetrievalOptions(bleOptions)
            .setWifiAwareEnabled(PreferencesHelper.isWifiDataRetrievalEnabled(context))
            .setNfcEnabled(PreferencesHelper.isNfcDataRetrievalEnabled(context))
            .build()

        // Setup and begin presentation
        presentation?.let {
            it.setListener(requestListener, context.mainExecutor())
            it.startListening(dataRetrievalConfiguration)
            hasStarted = true
        }
    }

    private val requestListener: PresentationHelper.Listener = object :
        PresentationHelper.Listener {
        override fun onDeviceEngagementReady() {
            transferStatusLd.value = TransferStatus.ENGAGEMENT_READY
        }

        override fun onEngagementDetected() {
            transferStatusLd.value = TransferStatus.ENGAGEMENT_DETECTED
        }

        override fun onDeviceConnecting() {
            transferStatusLd.value = TransferStatus.CONNECTING
        }

        override fun onDeviceConnected() {
            transferStatusLd.value = TransferStatus.CONNECTED
        }

        override fun onDeviceRequest(deviceEngagementMethod: Int, deviceRequestBytes: ByteArray) {
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
        val deviceEngagementForQrCode = presentation?.deviceEngagementForQrCode
            ?: throw IllegalArgumentException("Device engagement not ready")

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

    fun nfcEngagementProcessCommandApdu(service: HostApduService, commandApdu: ByteArray) {
        presentation?.nfcProcessCommandApdu(service, commandApdu)
    }

    fun nfcEngagementOnDeactivated(service: HostApduService, reason: Int) {
        presentation?.nfcOnDeactivated(service, reason)
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

    private fun destroy() {
        requestBytes = null
        store = null
        presentation = null
    }

    fun stopPresentation(
        sendSessionTerminationMessage: Boolean,
        useTransportSpecificSessionTermination: Boolean
    ) {
        presentation?.setSendSessionTerminationMessage(sendSessionTerminationMessage)
        presentation?.setUseTransportSpecificSessionTermination(
            useTransportSpecificSessionTermination
        )
        presentation?.setListener(null, null)
        try {
            presentation?.disconnect()
        } catch (e: RuntimeException) {
            Log.e(LOG_TAG, "Error ignored.", e)
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
        presentation?.sendDeviceResponse(deviceResponse)
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
}