package com.android.identity.wallet.presentationlog

/**
 * Defines the different components of an mDL Presentation that can be logged.
 */
sealed class PresentationLogComponent(private val componentStoreKey: String) {
    object Request : PresentationLogComponent("request")
    object Response : PresentationLogComponent("response")
    object Metadata : PresentationLogComponent("metadata")

    /**
     * Return the key to use for storing the bytes of a log component to StorageEngine
     */
    fun getStoreKey(logEntryId: Long) = PresentationLogStore.StoreConst.LOG_PREFIX +
            logEntryId +
            COMPONENT_PREFIX +
            componentStoreKey

    companion object {
        const val COMPONENT_PREFIX = "_CMPNT_"
        // enumeration of all loggable components
        val ALL = listOf(
            Request,
            Response,
            Metadata
        )
    }
}