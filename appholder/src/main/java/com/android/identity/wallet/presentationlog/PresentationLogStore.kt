package com.android.identity.wallet.presentationlog

import android.location.Location
import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborEncoder
import com.android.identity.internal.Util
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.storage.StorageEngine
import com.android.identity.wallet.util.EngagementType
import com.android.identity.wallet.util.log
import java.io.ByteArrayOutputStream
import java.security.PublicKey
import kotlin.random.Random.Default.nextInt

/**
 * A Store for logging transactions of mDL Presentations.
 *
 */
class PresentationLogStore(
    private val storageEngine: StorageEngine,
) {

    object StoreConst {
        // used for identifying entries belonging to PresentationLogStore when persisting logs in the StorageEngine
        val LOG_PREFIX = "IC_Log_"

        // retain a history of the last MAX_ENTRIES number of log entries
        val MAX_ENTRIES = 100

    }

    // builder responsible for adding the different parts of a PresentationLogEntry and persisting
    // logs upon a successful or terminated transaction.
    private var presentationLogEntryBuilder: PresentationLogEntry.Builder =
        PresentationLogEntry.Builder()

    private var metadataBuilder: PresentationLogMetadata.Builder = PresentationLogMetadata.Builder()

    /**
     * Add data of a specific LogComponent to [PresentationLogEntry] whose bytes are CBOR encoded
     */
    private fun logComponent(logComponent: PresentationLogComponent, data: ByteArray) {
        presentationLogEntryBuilder.addComponentLog(logComponent, data)
    }

    fun logRequestData(
        data: ByteArray,
        sessionTranscript: ByteArray?,
        engagementType: EngagementType
    ) {
        logComponent(PresentationLogComponent.Request, data)
        logMetadataData()
            .engagementType(engagementType)
            .sessionTranscript(sessionTranscript ?: byteArrayOf())

    }

    fun logResponseData(data: ByteArray) {
        logComponent(PresentationLogComponent.Response, data)
    }

    fun logMetadataData() = metadataBuilder

    fun logPresentationError(throwable: Throwable) {
        metadataBuilder.transactionError(throwable)
        persistCurrentLogEntry()
    }

    fun logPresentationComplete() {
        metadataBuilder.transactionComplete()
        persistCurrentLogEntry()
    }

    /**
     * User invoked cancellation
     */
    fun logPresentationCanceled() {
        metadataBuilder.transactionCanceled()
        persistCurrentLogEntry()
    }

    /**
     * Engagement that gets abruptly disconnected
     */
    fun logPresentationDisconnected() {
        metadataBuilder.transactionDisconnected()
        persistCurrentLogEntry()
    }


    private fun buildAndAddMetadataLog() {
        logComponent(
            PresentationLogComponent.Metadata,
            metadataBuilder.build().cborDataBytes
        )

        // ready a new PresentationLogData Builder
        metadataBuilder = PresentationLogMetadata.Builder()
    }

    private fun persistCurrentLogEntry() {
        buildAndAddMetadataLog()
        // Build the log entry to persist the log data
        val logEntry = presentationLogEntryBuilder.build()

        PresentationLogComponent.ALL.forEach { logComponent ->
            val storeKey = logComponent.getStoreKey(logEntry.id)
            storageEngine.put(storeKey, logEntry.getCborData(logComponent))
        }

        // ready a new PresentationLogEntry Builder
        presentationLogEntryBuilder = PresentationLogEntry.Builder()
    }
}


/**f
 * Provides a Log Store that can fetch previous log entries, delete a single entry, and delete all entries.
 */
class LogHistoryStore(
    private val storageEngine: StorageEngine,
) {

    fun fetchAllLogEntries(): List<PresentationLogEntry> {
        val persistedLogEntries = mutableListOf<PresentationLogEntry>()
        fetchAllLogEntryIds()
            .forEach { logEntryId ->
                val presentationLogEntry = PresentationLogEntry.Builder(logEntryId)
                PresentationLogComponent.ALL.forEach { logComponent ->
                    val componentStoreKey = logComponent.getStoreKey(logEntryId)
                    presentationLogEntry.addComponentLog(
                        logComponent,
                        storageEngine[componentStoreKey] ?: byteArrayOf()
                    )
                }
                persistedLogEntries.add(presentationLogEntry.build())
            }


        return persistedLogEntries
    }

    private fun fetchAllLogEntryIds(): List<Int> = storageEngine.enumerate()
        .filter { it.startsWith(PresentationLogStore.StoreConst.LOG_PREFIX) }
        .map { extractLogEntryId(it) }
        .distinct()

    private fun extractLogEntryId(storeKey: String) =
        storeKey
            .split(PresentationLogStore.StoreConst.LOG_PREFIX)[1]
            .split(PresentationLogComponent.COMPONENT_PREFIX)[0]
            .toInt()


    fun deleteLogEntry(entryId: Int) =
        PresentationLogComponent.ALL.forEach { logComponent ->
            storageEngine.delete(logComponent.getStoreKey(entryId))
        }

    fun deleteAllLogs() =
        fetchAllLogEntryIds()
            .forEach { logEntryId ->
                deleteLogEntry(logEntryId)
            }
}