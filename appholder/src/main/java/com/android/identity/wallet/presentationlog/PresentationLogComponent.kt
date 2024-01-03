package com.android.identity.wallet.presentationlog

/**
 * Defines the different components of an mDL Presentation that can be logged.
 */
sealed class PresentationLogComponent constructor(val componentStoreKey: String) {

    object Request : PresentationLogComponent("request") {
        fun cborDecodeRequestBytes(requestData: ByteArray) {

        }
    }

    object Response : PresentationLogComponent("response") {
        fun cborDecodeResponseBytes(responseData: ByteArray) {

        }
    }

    object Metadata : PresentationLogComponent("metadata") {
        fun cborDecodeMetadataBytes(metadataData: ByteArray) {

        }
    }

    fun getStoreKey(logEntryId: Int) =
        PresentationLogStore.StoreConst.LOG_PREFIX + logEntryId + COMPONENT_PREFIX + componentStoreKey

    companion object {
        val COMPONENT_PREFIX = "_CMPNT_"

        val ALL = listOf(
            Request,
            Response,
            Metadata
        )
    }

}