package com.android.identity_credential.wallet.logging

import com.android.identity.storage.StorageEngine
import com.android.identity.util.Logger
import com.android.identity.util.UUID
import kotlinx.datetime.Clock

/**
 * Event Logging facility.
 *
 * This saves events in Cbor format using storageEngine.
 */

private const val TAG = "EventLogger"

private var eventLoggingEnabled = false

class EventLogger(private val storageEngine: StorageEngine) {
    companion object {
        const val STORAGE_KEY_PREFIX = "event_log_"
    }

    fun startLoggingEvents() {
        eventLoggingEnabled = true
        Logger.i(TAG, "Enabling event logging")
    }

    fun stopLoggingEvents() {
        eventLoggingEnabled = false
        Logger.i(TAG, "Disabling event logging")
    }

    fun isLoggingEnabled(): Boolean {
        return eventLoggingEnabled
    }

    fun addMDocPresentationEntry(
        documentId: String,
        sessionTranscript: ByteArray,
        deviceRequestCbor: ByteArray,
        deviceResponseCbor: ByteArray
    ) {
        if (!eventLoggingEnabled) {
            Logger.i(TAG, "Logging is disabled")
            return // Exit immediately if logging is disabled
        }

        val uniqueId = Clock.System.now()
        val serializedEntry: ByteArray

        val event = MdocPresentationEvent(
            timestamp = uniqueId,
            documentId = documentId,
            sessionTranscript = sessionTranscript,
            deviceRequestCbor = deviceRequestCbor,
            deviceResponseCbor = deviceResponseCbor,
        )
        serializedEntry = event.toCbor()//Cbor.encode(event.toDataItem())

        val key = STORAGE_KEY_PREFIX + uniqueId
        storageEngine.put(key, serializedEntry)
    }

    fun getEntries(documentId: String): List<Event> {
        val entries = mutableListOf<Event>()
        for (key in storageEngine.enumerate()) {
            if (key.startsWith(STORAGE_KEY_PREFIX)) {
                val value = storageEngine[key]
                if (value != null) {
                    when (val event = Event.fromCbor(value)) {
                        is MdocPresentationEvent -> if (event.documentId == documentId) entries.add(event)
                        is DocumentUpdateCheckEvent -> if (event.documentId == documentId) entries.add(event)
                        // No need for an else branch here, as other Event subclasses don't have documentId
                    }
                }
            }
        }
        return entries
    }

    fun deleteEntries(entries: List<Event>) {
        for (entry in entries) {
            val key = STORAGE_KEY_PREFIX + entry.timestamp
            storageEngine.delete(key)
            Logger.i(TAG, "Deleted entry with key: $key")
        }
    }

    fun deleteEntriesForDocument(documentId: String) {
        val entriesToDelete = getEntries(documentId)
        deleteEntries(entriesToDelete)
        Logger.i(TAG, "Deleted all entries for documentId: $documentId")
    }
}

