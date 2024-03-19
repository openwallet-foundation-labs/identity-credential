package com.android.identity.age_verifier_mdl

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.nfc.NfcAdapter
import android.preference.PreferenceManager
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.identity.android.mdoc.deviceretrieval.VerificationHelper
import com.android.identity.android.mdoc.transport.ConnectionMethodTcp
import com.android.identity.android.mdoc.transport.ConnectionMethodUdp
import com.android.identity.android.mdoc.transport.DataTransportOptions
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import com.android.identity.mdoc.connectionmethod.ConnectionMethodBle
import com.android.identity.mdoc.connectionmethod.ConnectionMethodNfc
import com.android.identity.mdoc.connectionmethod.ConnectionMethodWifiAware
import com.android.identity.storage.StorageEngine
import com.android.identity.util.Logger
import java.io.File
import java.util.OptionalLong
import java.util.UUID

class TransferHelper private constructor(
    private var context: Context,
    private var activity: Activity
) {

    companion object {
        private val TAG = "TransferHelper"

        val PREFERENCE_KEY_INCLUDE_PORTRAIT_IN_REQUEST = "include_portrait_in_request"

        val PREFERENCE_KEY_BLE_CENTRAL_CLIENT_DATA_TRANSFER_ENABLED =
            "ble_central_client_data_transfer_enabled"
        val PREFERENCE_KEY_BLE_PERIPHERAL_SERVER_DATA_TRANSFER_ENABLED =
            "ble_peripheral_server_data_transfer_enabled"
        val PREFERENCE_KEY_WIFI_AWARE_DATA_TRANSFER_ENABLED = "wifi_aware_data_transfer_enabled"
        val PREFERENCE_KEY_NFC_DATA_TRANSFER_ENABLED = "nfc_data_transfer_enabled"
        val PREFERENCE_KEY_TCP_DATA_TRANSFER_ENABLED = "tcp_data_transfer_enabled"
        val PREFERENCE_KEY_UDP_DATA_TRANSFER_ENABLED = "udp_data_transfer_enabled"

        val PREFERENCE_KEY_EXPERIMENTAL_PSM_ENABLED = "experimental_psm_enabled"
        val PREFERENCE_KEY_L2CAP_ENABLED = "l2cap_enabled"
        val PREFERENCE_KEY_DEBUG_ENABLED = "debug_enabled"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: TransferHelper? = null

        fun getInstance(context: Context, activity: Activity) =
            instance ?: synchronized(this) {
                instance ?: TransferHelper(context, activity).also { instance = it }
            }
    }

    private val sharedPreferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    private var storageEngine: StorageEngine
    var androidKeystoreSecureArea: AndroidKeystoreSecureArea
    var deviceResponseBytes: ByteArray? = null
    var error: Throwable? = null
    private var connectionMethodUsed: ConnectionMethod? = null
    private var state = MutableLiveData<State>()

    private var _verificationHelper: VerificationHelper? = null
    private val verificationHelper: VerificationHelper
        get() = _verificationHelper!!


    enum class State {
        IDLE,
        ENGAGING,
        CONNECTING,
        CONNECTED,
        REQUEST_SENT,
        TRANSACTION_COMPLETE,
    }

    private val listener = object : VerificationHelper.Listener {
        override fun onReaderEngagementReady(readerEngagement: ByteArray) {
            Logger.d(TAG, "onReaderEngagementReady")
        }

        override fun onDeviceEngagementReceived(connectionMethods: List<ConnectionMethod>) {
            Logger.d(TAG, "onDeviceEngagementReceived")
            connectionMethodUsed = connectionMethods.first()
            verificationHelper.connect(connectionMethods.first())
            state.value = State.CONNECTING
        }

        override fun onMoveIntoNfcField() {
            Logger.d(TAG, "onMoveIntoNfcField")
        }

        override fun onDeviceConnected() {
            Logger.d(TAG, "onDeviceConnected")
            state.value = State.CONNECTED
        }

        override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {
            Logger.d(TAG, "onDeviceDisconnected, $transportSpecificTermination")
            close()
        }

        override fun onResponseReceived(deviceResponseBytes: ByteArray) {
            Logger.d(TAG, "onResponseReceived")
            this@TransferHelper.deviceResponseBytes = deviceResponseBytes
            state.value = State.TRANSACTION_COMPLETE
        }

        override fun onError(error: Throwable) {
            Logger.d(TAG, "onError ${this@TransferHelper.error}")
            this@TransferHelper.error = error
            state.value = State.TRANSACTION_COMPLETE
        }
    }

    // Called when setings have changed
    fun reinitializeVerificationHelper() {
        initializeVerificationHelper()
    }


    private fun initializeVerificationHelper() {

        val builder =
            VerificationHelper.Builder(
                context = context,
                listener = listener,
                executor = context.mainExecutor
            )
        val options = DataTransportOptions.Builder()
            .setBleUseL2CAP(getL2CapEnabled())
            .setExperimentalBleL2CAPPsmInEngagement(getExperimentalPsmEnabled())
            .build()
        builder.setDataTransportOptions(options)

        val connectionMethods = mutableListOf<ConnectionMethod>()
            .apply {
                val bleUuid = UUID.randomUUID()
                if (getBleCentralClientDataTransferEnabled()) {
                    add(
                        ConnectionMethodBle(
                            false,
                            true,
                            null,
                            bleUuid
                        )
                    )
                }
                if (getBlePeripheralServerDataTransferEnabled()) {
                    add(
                        ConnectionMethodBle(
                            true,
                            false,
                            bleUuid,
                            null
                        )
                    )
                }
                if (getWifiAwareDataTransferEnabled()) {
                    add(
                        ConnectionMethodWifiAware(
                            null,
                            OptionalLong.empty(),
                            OptionalLong.empty(),
                            null
                        )
                    )
                }
                if (getNfcDataTransferEnabled()) {
                    add(ConnectionMethodNfc(4096, 32768))
                }
                if (getTcpDataTransferEnabled()) {
                    add(ConnectionMethodTcp("", 0))
                }
                if (getUdpDataTransferEnabled()) {
                    add(ConnectionMethodUdp("", 0))
                }

            }
        builder.setNegotiatedHandoverConnectionMethods(connectionMethods)

        verificationHelper.disconnect()
        _verificationHelper = builder.build()
        deviceResponseBytes = null
        connectionMethodUsed = null
        error = null
        Logger.d(TAG, "Initialized VerificationHelper")
    }

    fun getSessionTranscript(): ByteArray = verificationHelper.sessionTranscript

    fun sendRequest(deviceRequestBytes: ByteArray) {
        check(state.value == State.CONNECTED)
        verificationHelper.sendRequest(deviceRequestBytes)
        state.value = State.REQUEST_SENT
    }

    fun getTapToEngagementDurationMillis(): Long =
        verificationHelper.tapToEngagementDurationMillis

    fun getEngagementToRequestDurationMillis(): Long =
        verificationHelper.engagementToRequestDurationMillis

    fun getRequestToResponseDurationMillis(): Long =
        verificationHelper.requestToResponseDurationMillis

    fun getScanningDurationMillis(): Long = verificationHelper.scanningTimeMillis

    fun getEngagementMethod(): VerificationHelper.EngagementMethod =
        verificationHelper.engagementMethod

    init {
        val storageDir = File(context.noBackupFilesDir, "identity")
        storageEngine = AndroidStorageEngine.Builder(context, storageDir).build()
        androidKeystoreSecureArea = AndroidKeystoreSecureArea(context, storageEngine)
        state.value = State.IDLE

        initializeVerificationHelper()

        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        nfcAdapter.enableReaderMode(
            activity,
            { tag ->
                if (state.value == State.IDLE) {
                    verificationHelper.nfcProcessOnTagDiscovered(tag)
                }
                state.postValue(State.ENGAGING)
            },
            NfcAdapter.FLAG_READER_NFC_A + NfcAdapter.FLAG_READER_NFC_B
                    + NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK + NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null
        )
    }

    fun close() {
        if (state.value == State.IDLE) {
            return
        }
        Logger.i(TAG, "close")
        initializeVerificationHelper()
        state.value = State.IDLE
    }

    fun getState(): LiveData<State> = state

    fun getConnectionMethod(): String = connectionMethodUsed?.toString() ?: ""

    fun getIncludePortraitInRequest(): Boolean =
        sharedPreferences.getBoolean(PREFERENCE_KEY_INCLUDE_PORTRAIT_IN_REQUEST, true)

    fun setIncludePortraitInRequest(enabled: Boolean) =
        sharedPreferences.edit {
            putBoolean(PREFERENCE_KEY_INCLUDE_PORTRAIT_IN_REQUEST, enabled)
        }

    fun getWifiAwareDataTransferEnabled(): Boolean =
        sharedPreferences.getBoolean(PREFERENCE_KEY_WIFI_AWARE_DATA_TRANSFER_ENABLED, false)

    fun setWifiAwareDataTransferEnabled(enabled: Boolean) =
        sharedPreferences.edit {
            putBoolean(PREFERENCE_KEY_WIFI_AWARE_DATA_TRANSFER_ENABLED, enabled)
        }

    fun getNfcDataTransferEnabled(): Boolean =
        sharedPreferences.getBoolean(PREFERENCE_KEY_NFC_DATA_TRANSFER_ENABLED, false)

    fun setNfcDataTransferEnabled(enabled: Boolean) =
        sharedPreferences.edit {
            putBoolean(PREFERENCE_KEY_NFC_DATA_TRANSFER_ENABLED, enabled)
        }

    fun getTcpDataTransferEnabled(): Boolean =
        sharedPreferences.getBoolean(PREFERENCE_KEY_TCP_DATA_TRANSFER_ENABLED, false)

    fun setTcpDataTransferEnabled(enabled: Boolean) =
        sharedPreferences.edit {
            putBoolean(PREFERENCE_KEY_TCP_DATA_TRANSFER_ENABLED, enabled)
        }

    fun getUdpDataTransferEnabled(): Boolean =
        sharedPreferences.getBoolean(PREFERENCE_KEY_UDP_DATA_TRANSFER_ENABLED, false)

    fun setUdpDataTransferEnabled(enabled: Boolean) =
        sharedPreferences.edit {
            putBoolean(PREFERENCE_KEY_UDP_DATA_TRANSFER_ENABLED, enabled)
        }

    fun getBleCentralClientDataTransferEnabled(): Boolean =
        sharedPreferences.getBoolean(
            PREFERENCE_KEY_BLE_CENTRAL_CLIENT_DATA_TRANSFER_ENABLED,
            true
        )


    fun setBleCentralClientDataTransferEnabled(enabled: Boolean) =
        sharedPreferences.edit {
            putBoolean(PREFERENCE_KEY_BLE_CENTRAL_CLIENT_DATA_TRANSFER_ENABLED, enabled)
        }

    fun getBlePeripheralServerDataTransferEnabled(): Boolean =
        sharedPreferences.getBoolean(
            PREFERENCE_KEY_BLE_PERIPHERAL_SERVER_DATA_TRANSFER_ENABLED,
            false
        )

    fun setBlePeripheralServerDataTransferEnabled(enabled: Boolean) =
        sharedPreferences.edit {
            putBoolean(PREFERENCE_KEY_BLE_PERIPHERAL_SERVER_DATA_TRANSFER_ENABLED, enabled)
        }

    fun getExperimentalPsmEnabled(): Boolean =
        sharedPreferences.getBoolean(PREFERENCE_KEY_EXPERIMENTAL_PSM_ENABLED, false)

    fun setExperimentalPsmEnabled(enabled: Boolean) =
        sharedPreferences.edit {
            putBoolean(PREFERENCE_KEY_EXPERIMENTAL_PSM_ENABLED, enabled)
        }

    fun getL2CapEnabled(): Boolean =
        sharedPreferences.getBoolean(PREFERENCE_KEY_L2CAP_ENABLED, false)

    fun setL2CapEnabled(enabled: Boolean) =
        sharedPreferences.edit {
            putBoolean(PREFERENCE_KEY_L2CAP_ENABLED, enabled)
        }

    fun getDebugEnabled(): Boolean =
        sharedPreferences.getBoolean(PREFERENCE_KEY_DEBUG_ENABLED, false)

    fun setDebugEnabled(enabled: Boolean) =
        sharedPreferences.edit {
            putBoolean(PREFERENCE_KEY_DEBUG_ENABLED, enabled)
        }
}