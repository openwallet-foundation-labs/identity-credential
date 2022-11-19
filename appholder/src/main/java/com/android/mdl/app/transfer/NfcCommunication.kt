package com.android.mdl.app.transfer

import android.annotation.SuppressLint
import android.content.Context
import com.android.identity.DeviceRequestParser
import com.android.identity.PresentationHelper
import com.android.mdl.app.util.log
import com.android.mdl.app.util.mainExecutor

class NfcCommunication private constructor(
    private val context: Context,
) {

    private var request: DeviceRequest? = null
    private lateinit var presentation: PresentationHelper

    fun setupPresentation(presentationHelper: PresentationHelper) {
        this.presentation = presentationHelper
    }

    fun setDeviceRequest(deviceRequest: ByteArray) {
        this.request = DeviceRequest(deviceRequest)
    }

    fun getDeviceRequest(): DeviceRequestParser.DeviceRequest {
        request?.let { requestBytes ->
            val parser = DeviceRequestParser()
            parser.setSessionTranscript(presentation.sessionTranscript)
            parser.setDeviceRequest(requestBytes.value)
            return parser.parse()
        } ?: throw IllegalStateException("Request not received")
    }

    fun sendResponse(deviceResponse: ByteArray) {
        val progressListener: (Long, Long) -> Unit = { progress, max ->
            log("Progress: $progress of $max")
            if (progress == max) {
                log("Completed...")
            }
        }
        presentation.sendDeviceResponse(deviceResponse, progressListener, context.mainExecutor())
    }

    fun stopPresentation(
        sendSessionTerminationMessage: Boolean,
        useTransportSpecificSessionTermination: Boolean
    ) {
        presentation.setSendSessionTerminationMessage(sendSessionTerminationMessage)
        try {
            if (presentation.isTransportSpecificTerminationSupported && useTransportSpecificSessionTermination) {
                presentation.setUseTransportSpecificSessionTermination(true)
            }
        } catch (e: IllegalStateException) {
            log("Error ignored.", e)
        }
        disconnect()
    }

    fun disconnect() {
        request = null
        presentation.disconnect()
    }

    companion object {

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: NfcCommunication? = null

        fun getInstance(context: Context): NfcCommunication {
            return instance ?: synchronized(this) {
                instance ?: NfcCommunication(context).also { instance = it }
            }
        }
    }

    @JvmInline
    value class DeviceRequest(val value: ByteArray)
}