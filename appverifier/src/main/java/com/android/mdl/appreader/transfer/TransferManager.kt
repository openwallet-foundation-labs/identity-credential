package com.android.mdl.appreader.transfer

import android.annotation.SuppressLint
import android.content.Context
import android.nfc.tech.IsoDep
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.security.identity.IdentityCredentialVerification
import androidx.security.identity.IdentityCredentialVerification.DeviceResponseListener
import com.android.mdl.appreader.util.TransferStatus
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


    var deviceRetrievalMethod: ByteArray? = null
        private set
    private var isoDep: IsoDep? = null
    private var hasStarted = false
    var response: IdentityCredentialVerification.DeviceResponse? = null
        private set
    private var verification: IdentityCredentialVerification? = null
    var availableTransferMethods: Collection<ByteArray>? = null
        private set

    private var transferStatusLd = MutableLiveData<TransferStatus>()

    fun getTransferStatus(): LiveData<TransferStatus> = transferStatusLd

    fun startEngagement(deviceEngagement: ByteArray, handover: ByteArray) {
        verification = IdentityCredentialVerification(context)
        verification?.setDeviceEngagement(deviceEngagement, handover)
    }

    fun setAvailableTransferMethods(availableTransferMethods: Collection<ByteArray>) {
        this.availableTransferMethods = availableTransferMethods
        // Select the first method as default, let the user select other transfer method
        // if there are more than one
        if (availableTransferMethods.isNotEmpty()) {
            this.deviceRetrievalMethod = availableTransferMethods.first()
        }
    }

    fun connect() {
        if (hasStarted)
            throw IllegalStateException("Connection has already started. It is necessary to stop verification before starting a new one.")

        if (verification == null)
            throw IllegalStateException("It is necessary to start a new engagement.")

        if (deviceRetrievalMethod == null)
            throw IllegalStateException("No retrieval method select.")

        // Start connection
        verification?.let {
            it.setDeviceResponseListener(responseListener, context.mainExecutor())
            deviceRetrievalMethod?.let { dr ->
                it.connect(dr, isoDep)
            }
            hasStarted = true
        }
    }

    fun stopVerification() {
        verification?.setDeviceResponseListener(null, null)
        verification?.disconnect()
        transferStatusLd = MutableLiveData<TransferStatus>()
        destroy()
        hasStarted = false
    }

    private fun destroy() {
        response = null
        verification = null
    }


    private val responseListener = object : DeviceResponseListener {
        override fun onDeviceConnected() {
            transferStatusLd.value = TransferStatus.CONNECTED
        }

        override fun onResponseReceived(deviceResponse: IdentityCredentialVerification.DeviceResponse) {
            response = deviceResponse
            transferStatusLd.value = TransferStatus.RESPONSE
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

    fun sendRequest(deviceRequest: IdentityCredentialVerification.DeviceRequest) {
        if (verification == null)
            throw IllegalStateException("It is necessary to start a new engagement.")

        verification?.sendRequest(deviceRequest)
    }

    fun setDeviceRetrievalMethod(deviceRetrievalMethod: ByteArray) {
        this.deviceRetrievalMethod = deviceRetrievalMethod
    }
}