package org.multipaz_credential.wallet

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.identity.android.mdoc.engagement.QrEngagementHelper
import com.android.identity.android.mdoc.transport.DataTransport
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.util.Logger
import kotlinx.coroutines.launch

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

        viewModelScope.launch {
            try {
                eDeviceKey = Crypto.createEcPrivateKey(EcCurve.P256)
                val qrEngagementListener = object : QrEngagementHelper.Listener {

                    override fun onDeviceConnecting() {
                        Logger.i(TAG, "onDeviceConnecting")
                        PresentationActivity.engagementDetected(context)
                    }

                    override fun onDeviceConnected(transport: DataTransport) {
                        Logger.i(TAG, "OnDeviceConnected via QR: qrEngagement=$qrEngagementHelper")

                        state = State.CONNECTED
                        PresentationActivity.startPresentation(
                            context, transport,
                            qrEngagementHelper!!.handover, eDeviceKey!!,
                            qrEngagementHelper!!.deviceEngagement
                        )

                        qrEngagementHelper?.close()
                        qrEngagementHelper = null
                    }

                    override fun onError(error: Throwable) {
                        Logger.i(TAG, "QR onError: ${error.message}")
                        stopQrConnection()
                        state = State.ERROR
                    }
                }

                val walletApplication = context as WalletApplication
                val (connectionMethods, options) = walletApplication.settingsModel
                    .createConnectionMethodsAndOptions()
                qrEngagementHelper = QrEngagementHelper.Builder(
                    context,
                    eDeviceKey!!.publicKey,
                    options,
                    qrEngagementListener,
                    ContextCompat.getMainExecutor(context)
                ).setConnectionMethods(connectionMethods).build()
                state = State.LISTENING
            } catch (e: Throwable) {
                Logger.e(TAG, "Caught exception generating QR code", e)
                stopQrConnection()
                state = State.ERROR
            }
        }
    }

    fun stopQrConnection() {
        qrEngagementHelper?.close()
        qrEngagementHelper = null
        state = State.IDLE
    }
}