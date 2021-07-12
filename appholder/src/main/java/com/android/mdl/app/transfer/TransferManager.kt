package com.android.mdl.app.transfer

import android.annotation.SuppressLint
import android.content.Context
import android.nfc.cardemulation.HostApduService
import android.os.Build
import android.util.Log
import android.view.View
import androidx.biometric.BiometricPrompt.CryptoObject
import androidx.core.content.ContextCompat
import androidx.core.util.component1
import androidx.core.util.component2
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.security.identity.*
import com.android.mdl.app.document.Document
import com.android.mdl.app.util.TransferStatus
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
    private var presentation: IdentityCredentialPresentation? = null
    private var document: Document? = null
    private var hasStarted = false

    var request: IdentityCredentialPresentation.DeviceRequest? = null
        private set
    private var transferStatusLd = MutableLiveData<TransferStatus>()

    fun getTransferStatus(): LiveData<TransferStatus> = transferStatusLd

    private fun initiate(document: Document) {
        this.document = document

        // Create identity credential store from hardware or software implementation depending on
        // what was used to store this specific document.
        store = if (document.hardwareBacked)
            IdentityCredentialStore.getHardwareInstance(context)
                ?: IdentityCredentialStore.getSoftwareInstance(context)
        else
            IdentityCredentialStore.getSoftwareInstance(context)

        presentation = store?.getPresentation(
            IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256
        )
    }

    @Throws(IllegalStateException::class)
    fun startPresentation(document: Document) {
        if (hasStarted)
            throw IllegalStateException("Transfer has already started. It is necessary to stop current presentation before starting a new one.")

        // Get an instance of the presentation based on the document
        initiate(document)

        // Setup and begin presentation
        presentation?.let {
            it.setDeviceRequestListener(requestListener, context.mainExecutor())
            it.addTransport(IdentityCredentialPresentation.TRANSPORT_BLUETOOTH_LE)
            it.presentationBegin()
            hasStarted = true
        }
    }

    private val requestListener: IdentityCredentialPresentation.DeviceRequestListener = object :
        IdentityCredentialPresentation.DeviceRequestListener {
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

        override fun onDeviceRequest(
            deviceEngagementMethod: Int,
            deviceRequest: IdentityCredentialPresentation.DeviceRequest
        ) {
            request = deviceRequest
            transferStatusLd.value = TransferStatus.REQUEST
        }

        override fun onDeviceDisconnected() {
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

    fun getDeviceEngagementQrCode(): View? {
        return presentation?.getDeviceEngagementQrCode(800)
    }

    fun nfcEngagementProcessCommandApdu(service: HostApduService, commandApdu: ByteArray) {
        presentation?.nfcEngagementProcessCommandApdu(service, commandApdu)
    }

    fun nfcEngagementOnDeactivated(service: HostApduService, reason: Int) {
        presentation?.nfcEngagementOnDeactivated(service, reason)
    }

    @Throws(IllegalStateException::class)
    fun sendCredential(
        docRequest: IdentityCredentialPresentation.DocumentRequest,
        issuerSignedEntriesToRequest: MutableMap<String, Collection<String>>
    ): CryptoObject? {
        val credentialName = document?.identityCredentialName
            ?: throw IllegalStateException("Error recovering credential name from document")

        presentation?.let {
            it.getCredentialForPresentation(credentialName)?.let { c ->


                val deviceSignedEntriesToRequest: Map<String, Collection<String>> = HashMap()
                try {
                    val deviceSignedResult: ResultData = c.getEntries(
                        null, deviceSignedEntriesToRequest, null
                    )
                    val issuerSignedResult: ResultData = c.getEntries(
                        null, issuerSignedEntriesToRequest, null
                    )
                    if (checkAuthenticationRequired(deviceSignedResult) ||
                        checkAuthenticationRequired(issuerSignedResult)
                    ) {
                        return c.cryptoObject
                    }
                    it.deviceResponseBegin()

                    val staticAuthData = issuerSignedResult.staticAuthenticationData
                    val (first, second) = Helpers.decodeStaticAuthData(staticAuthData)
                    it.deviceResponseAddDocument(
                        docRequest,
                        deviceSignedResult,
                        issuerSignedResult,
                        first,
                        second
                    )
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
            it.deviceResponseSend()
        }
        return null
    }

    // If an access control check fails for one of the requested entries or if the entry
    // doesn't exist, the entry is simply not returned.
    // TODO: store if authentication is required for each document, than this will be not necessary
    private fun checkAuthenticationRequired(resultData: ResultData): Boolean {
        resultData.namespaces.forEach { namespace ->
            resultData.getEntryNames(namespace)?.forEach { entryName ->
                if (resultData.getEntry(namespace, entryName) == null)
                    return true
            }
        }
        return false
    }

    private fun destroy() {
        request = null
        document = null
        store = null
        presentation = null
    }

    fun stopPresentation() {
        presentation?.setDeviceRequestListener(null, null)
        presentation?.presentationEnd()
        transferStatusLd = MutableLiveData<TransferStatus>()
        destroy()
        hasStarted = false
    }
}