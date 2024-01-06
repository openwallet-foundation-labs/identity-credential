package com.android.identity.wallet.presentationlog

import com.android.identity.storage.StorageEngine
import com.android.identity.wallet.util.EngagementType

/**
 * A Store for logging transactions of mDL Presentations.
 */
class PresentationLogStore(
    private val storageEngine: StorageEngine,
) {

    val presentationHistoryStore = PresentationHistoryStore(storageEngine)

    object StoreConst {
        // used for identifying entries belonging to PresentationLogStore when persisting logs in the StorageEngine
        const val LOG_PREFIX = "IC_Log_"

        // retain a history of the last MAX_ENTRIES number of log entries
        const val MAX_ENTRIES = 100
    }

    init {
        // referencing will instantiate all objects inside
        PresentationLogComponent.ALL
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
    private fun logComponent(logComponent: PresentationLogComponent, data: ByteArray) {
        logEntryBuilder.addComponentLog(logComponent, data)
    }

    fun logRequestData(
        data: ByteArray,
        sessionTranscript: ByteArray?,
        engagementType: EngagementType
    ) {
        logComponent(PresentationLogComponent.Request, data)
        logMetaData()
            .engagementType(engagementType)
            .sessionTranscript(sessionTranscript ?: byteArrayOf())

    }

    fun logResponseData(data: ByteArray) {
        logComponent(PresentationLogComponent.Response, data)
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
        logComponent(
            PresentationLogComponent.Metadata,
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

        PresentationLogComponent.ALL.forEach { logComponent ->
            // generate the key to use for storing the byte array of every log component
            val storeKey = logComponent.getStoreKey(logEntry.id)
            // create a new entry record in secure persistent storage for every log component's bytes in PresentationLogEntry
            storageEngine.put(storeKey, logEntry.getLogComponentBytes(logComponent))
        }

        // ready a new PresentationLogEntry Builder
        logEntryBuilder = PresentationLogEntry.Builder()

        // if we stored 1 more log entry than the defined MAX_ENTRIES, remove the oldest entry
        ensureMaxLogEntries()
    }

    /**
     * Ensure there are no more than SoreConst.MAX_ENTRIES entries saved in [StorageEngine], else,
     * delete the oldest entry.
     *
     * This function is always called after every persisting of PresentationLogEntry.
     */
    private fun ensureMaxLogEntries() {
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
                PresentationLogComponent.ALL.forEach { logComponent ->
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
                .split(PresentationLogComponent.COMPONENT_PREFIX)[0]
                .toLong()


        /**
         * Delete all saved records (log components) from [StorageEngine] associated with the specified entryID.
         */
        fun deleteLogEntry(entryId: Long) =
            PresentationLogComponent.ALL.forEach { logComponent ->
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
