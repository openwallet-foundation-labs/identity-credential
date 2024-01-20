package com.android.identity.presentationlog

import androidx.annotation.VisibleForTesting
import com.android.identity.storage.StorageEngine
import com.android.identity.util.EngagementTypeDef

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
    @VisibleForTesting
    object StoreConst {
        // used for identifying entries belonging to PresentationLogStore when persisting logs in the StorageEngine
        const val LOG_PREFIX = "IC_Log_"

        // whether or not to enforce a limit to the number of log entries that are persisted to [StorageEngine]
        const val MAX_ENTRIES_ENFORCEMENT = true

        // retain a history of the last MAX_ENTRIES number of log entries
        const val MAX_ENTRIES_COUNT = 100
    }

    private val currentEntries = mutableListOf<PresentationLogEntry.Builder>()

    private fun getCurrentLogEntryBuilder(
        newInstance: Boolean = false,
        havingId: Long? = null
    ): PresentationLogEntry.Builder {
        // immediately create a new PresentationLogEntry.Builder instance and return it
        if (newInstance) {
            val newEntryBuilder = PresentationLogEntry.Builder()
            currentEntries.add(newEntryBuilder)
            return newEntryBuilder
        }

        // iterate through all log entry builder instances
        currentEntries.forEach { currentEntryBuilder ->
            // return the entry instance that has not been persisted yet
            if (currentEntryBuilder.id == havingId // and matches specified ID
                // or entry has not been built (is actively being populated)
                || currentEntryBuilder.wasNotBuilt()
            ) {
                return currentEntryBuilder
            }
        }

        // reaching here means we were asked to get instance with specified ID but were unable to find it
        if (havingId != null) {
            throw IllegalArgumentException("Could not find PresentationLogEntry instance with specified id $havingId")
        }

        // reaching here means either all log entry instances are actively being persisted or
        // there are is no log active/new entry being populated
        val newEntryBuilder = PresentationLogEntry.Builder() // new entry (builder)
        currentEntries.add(newEntryBuilder)
        return newEntryBuilder
    }

    /**
     * Delete the passed instance from ephemeral storage.
     */
    private fun deleteLogEntryBuilderInstance(entryBuilder: PresentationLogEntry.Builder) {
        currentEntries.remove(entryBuilder)
    }

    /**
     * Create a new PresentationLogEntry.Builder instance and add the request data bytes, session transcript
     * bytes and engagement type. This instance will be populated with other log components and
     * persisted whenever finishing the log entry.
     *
     * Returns the new Builder instance.
     */
    fun newLogEntryWithRequest(
        data: ByteArray,
        sessionTranscript: ByteArray?,
        engagementType: EngagementTypeDef
    ): PresentationLogEntry.Builder {
        val entryBuilder = getCurrentLogEntryBuilder(newInstance = true)

        if (data.isNotEmpty()) {
            entryBuilder.addComponentLogBytes(LogComponent.Request, data)
        }

        entryBuilder.metadataBuilder
            .engagementType(engagementType)
            .sessionTranscript(sessionTranscript ?: byteArrayOf())

        return entryBuilder
    }

    /**
     * Set the data bytes for LogComponent.Response
     */
    fun logResponseData(data: ByteArray, currentEntryId: Long? = null) {
        val entryBuilder = getCurrentLogEntryBuilder(havingId = currentEntryId)
        if (data.isNotEmpty()) {
            entryBuilder.addComponentLogBytes(LogComponent.Response, data)
        }
    }

    /**
     * Return the metadata Builder for populating PresentationLogMetadata
     */
    fun getMetadataBuilder(currentEntryId: Long? = null): PresentationLogMetadata.Builder {
        val entryBuilder = getCurrentLogEntryBuilder(havingId = currentEntryId)
        return entryBuilder.metadataBuilder
    }

    /**
     * Persist log entry to StorageEngine after transaction was marked as complete.
     */
    fun persistLogEntryTransactionComplete(currentEntryId: Long? = null) {
        val entryBuilder = getCurrentLogEntryBuilder(havingId = currentEntryId)
        entryBuilder.metadataBuilder.transactionComplete()
        persistLogEntry(entryBuilder)
    }

    /**
     * Persist the log entry after user invoked cancellation of transaction.
     */
    fun persistLogEntryTransactionCanceled(currentEntryId: Long? = null) {
        val entryBuilder = getCurrentLogEntryBuilder(havingId = currentEntryId)
        entryBuilder.metadataBuilder.transactionCanceled()
        persistLogEntry(entryBuilder)
    }

    /**
     * Persist the log entry after there's a disconnect between sender and receiver.
     */
    fun persistLogEntryTransactionDisconnected(currentEntryId: Long? = null) {
        val entryBuilder = getCurrentLogEntryBuilder(havingId = currentEntryId)
        entryBuilder.metadataBuilder.transactionDisconnected()
        persistLogEntry(entryBuilder)
    }

    /**
     * Persist the log entry with the specified error that occurred during presentation.
     */
    fun persistLogEntryTransactionError(throwable: Throwable, currentEntryId: Long? = null) {
        val entryBuilder = getCurrentLogEntryBuilder(havingId = currentEntryId)
        entryBuilder.metadataBuilder.transactionError(throwable)
        persistLogEntry(entryBuilder)
    }

    /**
     * Persist all the data of log components that built the passed in PresentationLogEntry.Builder instance.
     */
    private fun persistLogEntry(entryBuilder: PresentationLogEntry.Builder) {
        // Build the log entry with data bytes for each LogComponent bytes that were added
        val logEntry = entryBuilder.build()

        // iterate through all possible LogComponents
        LogComponent.values().forEach { logComponent ->
            // generate the key to use for storing the byte array of every log component
            val storeKey = logComponent.getStoreKey(logEntry.id)
            // get the bytes of the log component (if any were passed)
            val storeValue = logEntry.getLogComponentBytes(logComponent)
            if (storeValue.isNotEmpty()) { // don't persist empty data of a log component
                // create a new entry record in secure persistent storage for every (non-empty) log component's bytes in PresentationLogEntry
                storageEngine.put(storeKey, storeValue)
            }
        }

        // remove entryBuilder instance
        deleteLogEntryBuilderInstance(entryBuilder)

        // enforce max entries
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
        var difference = StoreConst.MAX_ENTRIES_COUNT - allLogEntryIds.size
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
                        presentationLogEntry.addComponentLogBytes(
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
            .sortedDescending() // last entry shows up first

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