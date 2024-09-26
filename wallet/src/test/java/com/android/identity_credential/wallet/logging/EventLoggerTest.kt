package com.android.identity_credential.wallet.logging

import com.android.identity.storage.EphemeralStorageEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

class EventLoggerTest {

    private lateinit var mockStorage: EphemeralStorageEngine
    private lateinit var activityLogger: EventLogger // Create an instance

    @Before
    fun setUp() {
        mockStorage = EphemeralStorageEngine()
        activityLogger = EventLogger(mockStorage) // Initialize the instance
    }

    @Test
    fun testLoggingEnabled() {
        activityLogger.startLoggingEvents()
        assertTrue(activityLogger.isLoggingEnabled())
    }

    @Test
    fun testLoggingDisabled() {
        activityLogger.stopLoggingEvents()
        assertFalse(activityLogger.isLoggingEnabled())
    }

    @Test
    fun testDeleteEntries() {
        // Start logging events
        activityLogger.startLoggingEvents()

        // Add an MdocPresentationEntry to the logger
        activityLogger.addMDocPresentationEntry(
            documentId = "doc123",
            sessionTranscript = "sessionTranscript".toByteArray(),
            deviceRequestCbor = "request data".toByteArray(),
            deviceResponseCbor = "response data".toByteArray()
        )

        // Retrieve the entries for the given documentId
        val entries = activityLogger.getEntries("doc123")

        // Ensure that entries were added correctly
        assertTrue(entries.isNotEmpty(), "Entries should not be empty after adding an event")

        // Delete the entries retrieved
        activityLogger.deleteEntries(entries)

        // Check that the storage is empty after deletion
        assertTrue(mockStorage.enumerate().isEmpty(), "Storage should be empty after deleting all entries")
    }
}
