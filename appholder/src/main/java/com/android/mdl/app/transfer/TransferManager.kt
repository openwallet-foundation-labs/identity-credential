package com.android.mdl.app.transfer

import android.content.Context
import android.nfc.cardemulation.HostApduService
import android.os.Build
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.util.component1
import androidx.core.util.component2
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.security.identity.*
import com.android.mdl.app.util.TransferStatus
import java.util.concurrent.Executor

class TransferManager private constructor(private val context: Context) {

    companion object {
        private const val LOG_TAG = "TransferManager"

        @Volatile
        private var instance: TransferManager? = null

        fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: TransferManager(context).also { instance = it }
            }
    }

    private var presentation: IdentityCredentialPresentation? = null
    private var hasStarted = false

    //    private val store: IdentityCredentialStore =
//        IdentityCredentialStore.getSoftwareInstance(context.applicationContext)
    var request: IdentityCredentialPresentation.DeviceRequest? = null
        private set
    private var transferStatusLd = MutableLiveData<TransferStatus>()

    fun getTransferStatus(): LiveData<TransferStatus> = transferStatusLd

    @Throws(CipherSuiteNotSupportedException::class)
    fun startPresentation(store: IdentityCredentialStore) {
        if (!hasStarted) {
            try {
                presentation = store.getPresentation(
                    IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256
                )
            } catch (e: CipherSuiteNotSupportedException) {
                Log.e(LOG_TAG, "Error creating IdentityCredentialPresentation ${e.message}")
                throw e
            }

            presentation?.let {
                it.setDeviceRequestListener(requestListener, context.mainExecutor())
                it.addTransport(IdentityCredentialPresentation.TRANSPORT_BLUETOOTH_LE)
                it.presentationBegin()
            }
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

    fun sendCredential(
        docRequest: IdentityCredentialPresentation.DocumentRequest,
        credentialName: String,
        issuerSignedEntriesToRequest: MutableMap<String, Collection<String>>
    ) {
        presentation?.let {
            it.deviceResponseBegin()
            it.getCredentialForPresentation(credentialName)?.let { c ->


                val deviceSignedEntriesToRequest: Map<String, Collection<String>> = HashMap()
                try {
                    val deviceSignedResult: ResultData = c.getEntries(
                        null, deviceSignedEntriesToRequest, null
                    )
                    val issuerSignedResult: ResultData = c.getEntries(
                        null, issuerSignedEntriesToRequest, null
                    )
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
    }

    fun stopPresentation() {
        request = null
        presentation?.setDeviceRequestListener(null, null)
        presentation?.presentationEnd()
        presentation = null
        transferStatusLd = MutableLiveData<TransferStatus>()
        hasStarted = false
    }

}