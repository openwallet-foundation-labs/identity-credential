package com.android.mdl.app.transfer

import android.annotation.SuppressLint
import android.content.Context
import com.android.identity.DeviceRequestParser
import com.android.identity.PresentationHelper
import com.android.mdl.app.util.log
import com.android.mdl.app.util.mainExecutor

class Communication private constructor(
    private val context: Context,
) {

    private var request: DeviceRequest? = null
    private var presentation: PresentationHelper? = null

    fun setupPresentation(presentationHelper: PresentationHelper) {
        this.presentation = presentationHelper
    }

    fun setDeviceRequest(deviceRequest: ByteArray) {
        this.request = DeviceRequest(deviceRequest)
    }

    fun getDeviceRequest(): DeviceRequestParser.DeviceRequest {
        request?.let { requestBytes ->
            presentation?.let { presentation ->
                val parser = DeviceRequestParser()
                parser.setSessionTranscript(presentation.sessionTranscript)
                parser.setDeviceRequest(requestBytes.value)
                return parser.parse()
            } ?: throw IllegalStateException("Presentation not set")
        } ?: throw IllegalStateException("Request not received")
    }

    fun sendResponse(deviceResponse: ByteArray) {
        val progressListener: (Long, Long) -> Unit = { progress, max ->
            log("Progress: $progress of $max")
            if (progress == max) {
                log("Completed...")
            }
        }
        presentation?.sendDeviceResponse(deviceResponse, progressListener, context.mainExecutor())
    }

    fun stopPresentation(
        sendSessionTerminationMessage: Boolean,
        useTransportSpecificSessionTermination: Boolean
    ) {
        presentation?.setSendSessionTerminationMessage(sendSessionTerminationMessage)
        try {
            if (presentation?.isTransportSpecificTerminationSupported == true && useTransportSpecificSessionTermination) {
                presentation?.setUseTransportSpecificSessionTermination(true)
            }
        } catch (e: IllegalStateException) {
            log("Error ignored.", e)
        }
        disconnect()
    }

    fun disconnect() {
        request = null
        try {
            presentation?.disconnect()
        } catch (e: RuntimeException) {
            log("Error ignored closing presentation", e)
        }
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