package com.android.identity_credential.wallet.logging

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.storage.StorageEngine
import com.android.identity.util.Logger
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

    @CborSerializable
    sealed class Requester {
        class Anonymous : Requester() {
            override fun toString() = "Anonymous requester"
        }

        class Unknown : Requester() {
            override fun toString() = "Unknown requester"
        }

        class Named(val name: String) : Requester() {
            override fun toString() = name
        }
    }

    enum class ShareType(val displayName: String) {
        SHARED_IN_PERSON("Shared in-person"),
        SHARED_WITH_APPLICATION("Shared with application"),
        SHARED_WITH_WEBSITE("Shared with website"),
        UNKNOWN("Unknown");

        override fun toString(): String {
            return displayName
        }
    }

    @CborSerializable
    data class RequesterInfo(
        val requester: Requester,
        val shareType: ShareType
    )

    fun addMDocPresentationEntry(
        documentId: String,
        sessionTranscript: ByteArray,
        deviceRequestCbor: ByteArray,
        deviceResponseCbor: ByteArray,
        requesterType: Requester,
        shareType: ShareType
    ) {
        if (!eventLoggingEnabled) {
            Logger.i(TAG, "Logging is disabled")
            return // Exit immediately if logging is disabled
        }

        val uniqueId = Clock.System.now()
        val requesterInfo = RequesterInfo(
            requester = requesterType,
            shareType = shareType
        )
        val serializedEntry: ByteArray

        val event = MdocPresentationEvent(
            timestamp = uniqueId,
            documentId = documentId,
            sessionTranscript = sessionTranscript,
            deviceRequestCbor = deviceRequestCbor,
            deviceResponseCbor = deviceResponseCbor,
            requesterInfo = requesterInfo
        )
        serializedEntry = event.toCbor()

        val key = STORAGE_KEY_PREFIX + uniqueId
        storageEngine.put(key, serializedEntry)
    }

    fun getEntries(documentId: String): List<Event> {
        val entries = mutableListOf<Event>()
        for (key in storageEngine.enumerate()) {
            if (key.startsWith(STORAGE_KEY_PREFIX)) {
                val value = storageEngine[key]
                if (value != null) {
                    try {
                        val event = Event.fromCbor(value)
                        when {
                            event is MdocPresentationEvent && event.documentId == documentId -> entries.add(event)
                            event is DocumentUpdateCheckEvent && event.documentId == documentId -> entries.add(event)
                        }
                    } catch(e: IllegalStateException) {
                        Logger.w(TAG, "Failed to deserialize event for key: $key", e)
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