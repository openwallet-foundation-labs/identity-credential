package com.android.identity.wallet_wear.presentation

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper
import com.android.identity.android.mdoc.transport.DataTransport
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.credential.CredentialStore
import com.android.identity.credential.NameSpacedData
import com.android.identity.credentialtype.CredentialTypeRepository
import com.android.identity.credentialtype.knowntypes.DrivingLicense
import com.android.identity.mdoc.mso.StaticAuthDataParser
import com.android.identity.mdoc.request.DeviceRequestParser
import com.android.identity.mdoc.response.DeviceResponseGenerator
import com.android.identity.mdoc.response.DocumentGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.storage.StorageEngine
import com.android.identity.util.Constants
import com.android.identity.util.Logger
import com.android.identity.util.Timestamp
import java.io.File
import java.security.KeyPair
import java.security.PublicKey
import java.util.OptionalLong

class TransferHelper private constructor(private val context: Context) {
    private val TAG = "TransferHelper"

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: TransferHelper? = null

        fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: TransferHelper(context).also { instance = it }
            }
    }


    private var storageEngine: StorageEngine
    private var secureAreaRepository: SecureAreaRepository
    private var androidKeystoreSecureArea: AndroidKeystoreSecureArea
    private var credentialStore: CredentialStore
    private var deviceRetrievalHelper: DeviceRetrievalHelper? = null
    private var deviceRequest: ByteArray? = null

    var credentialTypeRepository: CredentialTypeRepository

    private var state = MutableLiveData<State>()

    enum class State {
        NOT_CONNECTED,
        CONNECTING,
        CONNECTED,
        REQUEST_AVAILABLE,
        RESPONSE_SENT
    }

    init {
        val storageDir = File(context.noBackupFilesDir, "identity")
        storageEngine = AndroidStorageEngine.Builder(context, storageDir).build()
        secureAreaRepository = SecureAreaRepository();
        androidKeystoreSecureArea = AndroidKeystoreSecureArea(context, storageEngine);
        secureAreaRepository.addImplementation(androidKeystoreSecureArea);
        credentialStore = CredentialStore(storageEngine, secureAreaRepository)

        credentialTypeRepository = CredentialTypeRepository()
        credentialTypeRepository.addCredentialType(DrivingLicense.getCredentialType())

        state.value = State.NOT_CONNECTED
    }

    fun getState(): LiveData<State> {
        return state
    }

    fun setConnecting() {
        state.value = State.CONNECTING
    }

    fun setConnected(
        eDeviceKeyPair: KeyPair,
        transport: DataTransport,
        deviceEngagement: ByteArray,
        handover: ByteArray
    ) {
        check(state.value == State.CONNECTING) { "Not in CONNECTING state"}
        deviceRetrievalHelper = DeviceRetrievalHelper.Builder(
            context,
            object : DeviceRetrievalHelper.Listener {
                override fun onEReaderKeyReceived(eReaderKey: PublicKey) {
                    Logger.d(TAG, "onEReaderKeyReceived")
                }

                override fun onDeviceRequest(deviceRequestBytes: ByteArray) {
                    Logger.dCbor(TAG, "onDeviceRequest", deviceRequestBytes)
                    deviceRequest = deviceRequestBytes
                    state.value = State.REQUEST_AVAILABLE
                }

                override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {
                    Logger.d(TAG, "onDeviceDisconnected $transportSpecificTermination")
                    deviceRetrievalHelper?.disconnect()
                    deviceRetrievalHelper = null
                    state.value = State.NOT_CONNECTED
                }

                override fun onError(error: Throwable) {
                    Logger.d(TAG, "onError", error)
                    deviceRetrievalHelper?.disconnect()
                    deviceRetrievalHelper = null
                    state.value = State.NOT_CONNECTED
                }

            },
            context.mainExecutor,
            eDeviceKeyPair)
            .useForwardEngagement(transport, deviceEngagement, handover)
            .build()
        state.value = State.CONNECTED
    }

    fun getDeviceRequest(): ByteArray {
        check(state.value == State.REQUEST_AVAILABLE) { "Not in REQUEST_AVAILABLE state"}
        check(deviceRequest != null) { "No request available "}
        return deviceRequest as ByteArray
    }

    fun getSessionTranscript(): ByteArray {
        return deviceRetrievalHelper!!.sessionTranscript
    }

    fun sendResponse(deviceResponseBytes: ByteArray) {
        check(state.value == State.REQUEST_AVAILABLE) { "Not in REQUEST_AVAILABLE state"}
        deviceRetrievalHelper!!.sendDeviceResponse(deviceResponseBytes, OptionalLong.of(Constants.SESSION_DATA_STATUS_SESSION_TERMINATION))
        state.value = State.RESPONSE_SENT
    }

    fun disconnect() {
        Logger.d(TAG, "disconnect")
        if (deviceRetrievalHelper == null) {
            Logger.d(TAG, "already closed")
            return
        }
        if (state.value == State.REQUEST_AVAILABLE) {
            val deviceResponseGenerator = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_GENERAL_ERROR)
            sendResponse(deviceResponseGenerator.generate())
        }
        deviceRetrievalHelper?.disconnect()
        deviceRetrievalHelper = null
        state.value = State.NOT_CONNECTED
    }

}