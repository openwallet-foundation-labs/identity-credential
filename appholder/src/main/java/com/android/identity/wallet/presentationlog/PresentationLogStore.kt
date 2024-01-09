package com.android.identity.wallet.presentationlog

import com.android.identity.storage.StorageEngine
import com.android.identity.wallet.util.EngagementType

// for readability
typealias LogComponent = PresentationLogStore.LogComponent

/**
 * A Log Store that is used to form and persist all components of a PresentationLogEntry.
 * It also provides a History Store for obtaining previously persisted log entries (list of PresentationLogEntry)
 */
class PresentationLogStore(
    private val storageEngine: StorageEngine,
) {
    val presentationHistoryStore = PresentationHistoryStore(storageEngine)
    enum class LogComponent {
        Request,
        Response,
        Metadata,
        ;

        /**
         * Differentiate const fields from enum names.
         */
        object Const {
            const val COMPONENT_PREFIX = "_CMPNT_"
        }

        /**
         * Return the store key for storing/retrieving bytes of a LogComponent in [StorageEngine].
         */
        fun getStoreKey(logEntryId: Long) = StoreConst.LOG_PREFIX +
                logEntryId +
                Const.COMPONENT_PREFIX +
                name
    }

    /**
     * Const object (rather than companion) dedicated to providing constants
     */
    protected object StoreConst {
        // used for identifying entries belonging to PresentationLogStore when persisting logs in the StorageEngine
        const val LOG_PREFIX = "IC_Log_"

        // whether or not to enforce a limit to the number of log entries that are persisted to [StorageEngine]
        const val MAX_ENTRIES_ENFORCEMENT = true

        // retain a history of the last MAX_ENTRIES number of log entries
        const val MAX_ENTRIES = 100
    }

    // builder responsible for adding the different parts of a PresentationLogEntry and persisting
    // logs upon a successful or terminated transaction.
    private var logEntryBuilder: PresentationLogEntry.Builder =
        PresentationLogEntry.Builder()

    // builder responsible for populating a PresentationLogMetadata with type-values
    private var metadataBuilder: PresentationLogMetadata.Builder = PresentationLogMetadata.Builder()

    /**
     * Add the CBOR data bytes of a specific LogComponent to [PresentationLogEntry].
     */
    private fun addLogComponentData(logComponent: LogComponent, data: ByteArray) {
        logEntryBuilder.addComponentLog(logComponent, data)
    }

    fun logRequestData(
        data: ByteArray,
        sessionTranscript: ByteArray?,
        engagementType: EngagementType
    ) {
        addLogComponentData(LogComponent.Request, data)
        logMetaData()
            .engagementType(engagementType)
            .sessionTranscript(sessionTranscript ?: byteArrayOf())

    }

    fun logResponseData(data: ByteArray) {
        addLogComponentData(LogComponent.Response, data)
    }

    fun logMetaData() = metadataBuilder

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

    fun logPresentationError(throwable: Throwable) {
        metadataBuilder.transactionError(throwable)
        persistCurrentLogEntry()
    }

    /**
     *
     */
    private fun addMetadataLogs() {
        addLogComponentData(
            LogComponent.Metadata,
            metadataBuilder.build().cborDataBytes
        )

        // ready a new PresentationLogData Builder
        metadataBuilder = PresentationLogMetadata.Builder()
    }

    /**
     * Persist all the data of log components that built the current PresentationLogEntry instance.
     * Creates a new instance of [PresentationLogEntry.Builder] ready to persist yet another entry
     */
    private fun persistCurrentLogEntry() {
        // add the Metadata log component bytes to the builder
        addMetadataLogs()
        // Build the log entry to persist the log data
        val logEntry = logEntryBuilder.build()

        LogComponent.values().forEach { logComponent ->
            // generate the key to use for storing the byte array of every log component
            val storeKey = logComponent.getStoreKey(logEntry.id)
            // create a new entry record in secure persistent storage for every log component's bytes in PresentationLogEntry
            storageEngine.put(storeKey, logEntry.getLogComponentBytes(logComponent))
        }

        // ready a new PresentationLogEntry Builder
        logEntryBuilder = PresentationLogEntry.Builder()

        if (StoreConst.MAX_ENTRIES_ENFORCEMENT) {
            // if we stored 1 more log entry than the defined MAX_ENTRIES, remove the oldest entry
            enforceMaxLogEntries()
        }
    }

    /**
     * Ensure there are no more than SoreConst.MAX_ENTRIES entries saved in [StorageEngine], else,
     * delete as many oldest entries as necessary to satisfy requirement.
     *
     * This function is always called after every persisting of PresentationLogEntry and only
     * if MAX_ENTRIES_ENFORCEMENT = true.
     */
    private fun enforceMaxLogEntries() {
        val allLogEntryIds = presentationHistoryStore.fetchAllLogEntryIds().sorted()
        var difference = StoreConst.MAX_ENTRIES - allLogEntryIds.size
        var oldestIndex = 0
        while (difference < 0) { // there's at least 1 more entry over the defined MAX_ENTRIES
            // delete the oldest entries to bring count to MAX_ENTRIES
            val oldestId = allLogEntryIds[oldestIndex]
            presentationHistoryStore.deleteLogEntry(oldestId)
            difference++ // 1 entry was purged
            oldestIndex++ // go to next oldest entry
        }
    }

    /**
     * Provides a Public/External History Log Store that can fetch previous log entries,
     * delete a single entry, and delete all entries.
     */
    class PresentationHistoryStore(
        private val storageEngine: StorageEngine,
    ) {
        /**
         * Retrieve the bytes of all persisted log entries and return a list of PresentationLogEntry of those entries.
         */
        fun fetchAllLogEntries(): List<PresentationLogEntry> {
            // list to populate with all persisted PresentationLogEntry objects
            val persistedLogEntries = mutableListOf<PresentationLogEntry>()
            // get all the unique IDs that are embedded in the store key of every component tied to a PresentationLogEntry
            fetchAllLogEntryIds().forEach { logEntryId ->
                // Creating a new PresentationLogEntry for every encountered ID
                val presentationLogEntry = PresentationLogEntry.Builder(logEntryId)
                // try get saved bytes of every log component to build the PresentationLogEntry with bytes of every found component
                LogComponent.values().forEach { logComponent ->
                    // look for bytes of a component (for every entry ID) at this key
                    val componentStoreKey = logComponent.getStoreKey(logEntryId)
                    val componentValueBytes = storageEngine[componentStoreKey]
                    // add components that have data persisted
                    if (componentValueBytes != null) {
                        presentationLogEntry.addComponentLog(
                            logComponent,
                            componentValueBytes
                        )
                    }
                }
                // build the entry now that we've extracted all possible bytes of every log component for a given entry ID
                persistedLogEntries.add(presentationLogEntry.build())
            }
            return persistedLogEntries
        }

        /**
         * Enumerate all persisted entries of [StorageEngine] and filter for (Presentation) log entries,
         * return only unique log entry IDs - where each log entry ID can have 1 or more key/value pairs
         * stored in the secure persistent storage table.
         */
        fun fetchAllLogEntryIds(): List<Long> = storageEngine.enumerate()
            .filter { it.startsWith(StoreConst.LOG_PREFIX) }
            .map { extractLogEntryId(it) }
            .distinct()

        /**
         * Given a key found in [StorageEngine], extract the ID of the log entry that was stored.
         */
        private fun extractLogEntryId(storeKey: String) =
            storeKey
                .split(StoreConst.LOG_PREFIX)[1]
                .split(LogComponent.Const.COMPONENT_PREFIX)[0]
                .toLong()


        /**
         * Delete all saved records (log components) from [StorageEngine] associated with the specified entryID.
         */
        fun deleteLogEntry(entryId: Long) =
            LogComponent.values().forEach { logComponent ->
                storageEngine.delete(logComponent.getStoreKey(entryId))
            }

        /**
         * Delete all logs - iterate through all persisted unique entry IDs and delete all log components
         * tied for each entry ID.
         */
        fun deleteAllLogs() =
            fetchAllLogEntryIds().forEach { logEntryId -> deleteLogEntry(logEntryId) }
    }
}
