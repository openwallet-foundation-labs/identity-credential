package com.android.identity_credential.wallet

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.identity.android.mdoc.engagement.QrEngagementHelper
import com.android.identity.android.mdoc.transport.DataTransport
import com.android.identity.android.mdoc.transport.DataTransportOptions
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import com.android.identity.mdoc.connectionmethod.ConnectionMethodBle
import com.android.identity.util.Logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import java.util.UUID

class QrEngagementViewModel(val context: Application) : AndroidViewModel(context)  {
    companion object {
        private const val TAG = "QrEngagementViewModel"
    }

    enum class State {
        IDLE,
        STARTING,
        LISTENING,
        CONNECTED,
        ERROR
    }

    var state by mutableStateOf(State.IDLE)
        private set

    val qrCode: String
        get() = qrEngagementHelper!!.deviceEngagementUriEncoded

    private var qrEngagementHelper: QrEngagementHelper? = null
    private var eDeviceKey: EcPrivateKey? = null

    fun startQrEngagement() {
        state = State.STARTING

        viewModelScope.launch(
            CoroutineExceptionHandler{ _, exception ->
                Logger.e(TAG, "CoroutineExceptionHandler got $exception")
                stopQrConnection()
                state = State.ERROR
            }
        ) {
            eDeviceKey = Crypto.createEcPrivateKey(EcCurve.P256)
            val qrEngagementListener = object : QrEngagementHelper.Listener {

                override fun onDeviceConnecting() {
                    Logger.i(TAG, "onDeviceConnecting")
                }

                override fun onDeviceConnected(transport: DataTransport) {
                    Logger.i(TAG, "OnDeviceConnected via QR: qrEngagement=$qrEngagementHelper")

                    state = State.CONNECTED
                    PresentationActivity.startPresentation(context, transport,
                        qrEngagementHelper!!.handover, eDeviceKey!!,
                        qrEngagementHelper!!.deviceEngagement)

                    qrEngagementHelper?.close()
                    qrEngagementHelper = null
                }

                override fun onError(error: Throwable) {
                    Logger.i(TAG, "QR onError: ${error.message}")
                    stopQrConnection()
                    state = State.ERROR
                }
            }

            val options = DataTransportOptions.Builder().build()
            val connectionMethods = mutableListOf<ConnectionMethod>()
            val bleUuid = UUID.randomUUID()
            connectionMethods.add(
                ConnectionMethodBle(
                    false,
                    true,
                    null,
                    bleUuid
                )
            )
            qrEngagementHelper = QrEngagementHelper.Builder(
                context = context,
                scope = viewModelScope,
                eDeviceKey = eDeviceKey!!.publicKey,
                options  = options,
                listener = qrEngagementListener,

            ).setConnectionMethods(connectionMethods).build()
            state = State.LISTENING
        }
    }

    fun stopQrConnection() {
        qrEngagementHelper?.close()
        qrEngagementHelper = null
        state = State.IDLE
    }
}