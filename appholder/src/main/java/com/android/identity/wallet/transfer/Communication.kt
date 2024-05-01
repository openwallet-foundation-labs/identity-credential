package com.android.identity.wallet.transfer

import android.annotation.SuppressLint
import android.content.Context
import com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper
import com.android.identity.mdoc.request.DeviceRequestParser
import com.android.identity.util.Constants
import com.android.identity.wallet.util.log

class Communication private constructor(
    private val context: Context,
) {
    private var request: DeviceRequest? = null
    var deviceRetrievalHelper: DeviceRetrievalHelper? = null

    fun setDeviceRequest(deviceRequest: ByteArray) {
        this.request = DeviceRequest(deviceRequest)
    }

    fun getDeviceRequest(): DeviceRequestParser.DeviceRequest =
        request?.let { requestBytes ->
            deviceRetrievalHelper?.let { presentation ->
                DeviceRequestParser(
                    requestBytes.value,
                    presentation.sessionTranscript,
                ).run { parse() }
            } ?: throw IllegalStateException("Presentation not set")
        } ?: throw IllegalStateException("Request not received")

    fun getSessionTranscript(): ByteArray? = deviceRetrievalHelper?.sessionTranscript

    fun sendResponse(
        deviceResponse: ByteArray,
        closeAfterSending: Boolean,
    ) {
        if (closeAfterSending) {
            deviceRetrievalHelper?.sendDeviceResponse(
                deviceResponse,
                Constants.SESSION_DATA_STATUS_SESSION_TERMINATION,
            )
            deviceRetrievalHelper?.disconnect()
        } else {
            deviceRetrievalHelper?.sendDeviceResponse(deviceResponse, null)
        }
    }

    fun stopPresentation(
        sendSessionTerminationMessage: Boolean,
        useTransportSpecificSessionTermination: Boolean,
    ) {
        if (sendSessionTerminationMessage) {
            if (useTransportSpecificSessionTermination) {
                deviceRetrievalHelper?.sendTransportSpecificTermination()
            } else {
                deviceRetrievalHelper?.sendDeviceResponse(
                    null,
                    Constants.SESSION_DATA_STATUS_SESSION_TERMINATION,
                )
            }
        }
        disconnect()
    }

    fun disconnect() =
        try {
            request = null
            deviceRetrievalHelper?.disconnect()
        } catch (e: RuntimeException) {
            log("Error ignored closing presentation", e)
        }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: Communication? = null

        fun getInstance(context: Context): Communication {
            return instance ?: synchronized(this) {
                instance ?: Communication(context).also { instance = it }
            }
        }
    }

    @JvmInline
    value class DeviceRequest(val value: ByteArray)
}
