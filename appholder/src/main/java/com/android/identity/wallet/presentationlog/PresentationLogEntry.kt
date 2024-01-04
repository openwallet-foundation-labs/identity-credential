package com.android.identity.wallet.presentationlog

import com.android.identity.mdoc.request.DeviceRequestParser
import com.android.identity.mdoc.response.DeviceResponseParser
import kotlin.random.Random.Default.nextInt

class PresentationLogEntry private constructor(
    val id: Int,
    private val componentLogs: MutableMap<PresentationLogComponent, ByteArray>,
) {

    fun getCborData(logComponent: PresentationLogComponent) =
        componentLogs[logComponent] ?: byteArrayOf()

    fun getRequest(): DeviceRequestParser.DeviceRequest? {
        val requestBytes = getCborData(PresentationLogComponent.Request)
//        if (requestBytes.isEmpty()) throw IllegalStateException("Presentation Log Request not present")
        if (requestBytes.isEmpty()) return null

        val metadata = getMetadata()
        val parser = DeviceRequestParser()
        parser.setSessionTranscript(metadata.sessionTranscript)
        parser.setDeviceRequest(requestBytes)
        return parser.parse()
    }

    fun getResponse(): DeviceResponseParser.DeviceResponse? {
        val responseBytes = getCborData(PresentationLogComponent.Response)
        if (responseBytes.isEmpty()) return null
        val metadata = getMetadata()
        val parser = DeviceResponseParser()
        parser.setDeviceResponse(responseBytes)
        parser.setSessionTranscript(metadata.sessionTranscript)
        return parser.parse()
    }

    fun getMetadata(): PresentationLogMetadata {
        val metadataBytes = getCborData(PresentationLogComponent.Metadata)
        if (metadataBytes.isEmpty()) throw IllegalStateException("Presentation Log Metadata not present!")
        return PresentationLogMetadata.fromCborData(metadataBytes)
    }

    fun hasDataFromAllComponents() = componentLogs.size == PresentationLogComponent.ALL.size

    data class Builder(
        var id: Int = nextInt(from = 10000000, until = 99999999),
        private val componentLogs: MutableMap<PresentationLogComponent, ByteArray> = mutableMapOf(),
    ) {
        fun addComponentLog(logComponent: PresentationLogComponent, data: ByteArray) = apply {
            componentLogs[logComponent] = data
        }

        fun build() = PresentationLogEntry(id, componentLogs)
    }
}