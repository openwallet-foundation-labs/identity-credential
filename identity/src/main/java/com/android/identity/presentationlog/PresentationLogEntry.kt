package com.android.identity.presentationlog

import androidx.annotation.VisibleForTesting
import com.android.identity.mdoc.request.DeviceRequestParser
import com.android.identity.mdoc.response.DeviceResponseParser
import com.android.identity.util.Timestamp

/**
 * PresentationLogEntry is a log entry captures logs from all PresentationLogComponent components through a Builder
 * which populates
 */
class PresentationLogEntry private constructor(
    val id: Long,
    @get:VisibleForTesting
    val componentLogs: MutableMap<PresentationLogStore.LogComponent, ByteArray>,
) {
    // simple ephemeral cache for optimizing composable calls
    private var requestCached: DeviceRequestParser.DeviceRequest? = null
    private var responseCached: DeviceResponseParser.DeviceResponse? = null
    private var metadataCached: PresentationLogMetadata? = null

    /**
     * Return the DeviceRequest object extracted from the passed-in bytes.
     */
    fun getRequest(): DeviceRequestParser.DeviceRequest? {
        val requestBytes = getLogComponentBytes(LogComponent.Request)
        val metadata = getMetadata()
        if (requestBytes.isEmpty() || metadata == null) return null

        if (requestCached == null) {
            val requestParser = DeviceRequestParser()
            requestParser.setSessionTranscript(metadata.sessionTranscript)
            requestParser.setDeviceRequest(requestBytes)
            requestCached = requestParser.parse()
        }

        return requestCached
    }

    /**
     * Return the DeviceResponse object extracted from the passed-in bytes.
     */
    fun getResponse(): DeviceResponseParser.DeviceResponse? {
        val responseBytes = getLogComponentBytes(LogComponent.Response)
        val metadata = getMetadata()
        if (responseBytes.isEmpty() || metadata == null) return null
        if (responseCached == null) {
            val responseParser = DeviceResponseParser()
            responseParser.setDeviceResponse(responseBytes)
            responseParser.setSessionTranscript(metadata.sessionTranscript)
            responseCached = responseParser.parse()
        }
        return responseCached
    }

    /**
     * Return the PresentationLogMetadata object extracted from the passed-in bytes.
     */
    fun getMetadata(): PresentationLogMetadata? {
        if (metadataCached == null) {
            val metadataBytes = getLogComponentBytes(LogComponent.Metadata)
            if (metadataBytes.isEmpty()) return null
            metadataCached = PresentationLogMetadata.fromCborBytes(metadataBytes)
        }

        return metadataCached
    }

    /**
     * Return the (CBOR encoded) bytes of the specified PresentationLogComponent
     */
    fun getLogComponentBytes(logComponent: LogComponent) =
        componentLogs[logComponent] ?: byteArrayOf()

    /**
     * Builder of PresentationLogEntry - which is comprised of bytes for every PresentationLogComponent.
     * The ID of each log entry is the timestamp in milliseconds of when the Builder() was instantiated.
     */
    data class Builder(
        private val _id: Long = Timestamp.now().toEpochMilli(),
        private val componentLogs: MutableMap<LogComponent, ByteArray> = mutableMapOf(),
        private var wasBuilt: Boolean = false,
        // internal instance with public getter
        private val _metadataBuilder: PresentationLogMetadata.Builder =
            PresentationLogMetadata.Builder(transactionStartTime = _id)
    ) {
        val id: Long
            get() = _id
        val metadataBuilder: PresentationLogMetadata.Builder
            get() = _metadataBuilder

        /**
         * Add a LogComponent to the log entry
         */
        fun addComponentLogBytes(logComponent: LogComponent, data: ByteArray) = apply {
            componentLogs[logComponent] = data
        }

        fun wasNotBuilt() = !wasBuilt

        fun build(): PresentationLogEntry {
            // mark as was/being built
            wasBuilt = true

            // either use bytes read from StorageEngine or extract from metadata builder
            ensureMetadataIsLogged()

            // return a new instance of PresentationLogEntry
            return PresentationLogEntry(id, componentLogs)
        }

        /**
         * Ensure the correct metadata bytes are populated in the new instance of PresentationLogEntry
         */
        private fun ensureMetadataIsLogged() {
            // if we don't already have the bytes of metadata, extract from current metadata builder
            if (componentLogs[PresentationLogStore.LogComponent.Metadata] == null) {
                // add metadata component to componentLogs
                componentLogs[PresentationLogStore.LogComponent.Metadata] =
                    _metadataBuilder.build().cborDataBytes
            }
        }
    }
}