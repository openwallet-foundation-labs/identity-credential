package com.android.identity.presentationlog

import androidx.annotation.VisibleForTesting
import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborEncoder
import com.android.identity.internal.Util
import com.android.identity.util.EngagementTypeDef
import com.android.identity.util.Timestamp
import java.io.ByteArrayOutputStream

class PresentationLogMetadata private constructor(
    val presentationTransactionStatus: PresentationTransactionStatus,
    val engagementType: EngagementTypeDef,
    val error: String = "",
    val transactionStartTime: Long,
    val transactionEndTime: Long,
    val sessionTranscript: ByteArray,
    val locationLatitude: Double = 0.0,
    val locationLongitude: Double = 0.0
) {

    // return instance data in CBOR encoded format
    val cborDataBytes: ByteArray
        get() = ByteArrayOutputStream().apply {
            CborEncoder(this).encode(
                CborBuilder().addMap()
                    .put("transaction_status", presentationTransactionStatus.code)
                    .put("engagement_type", engagementType.name)
                    .put("error", error)
                    .put("transaction_start", transactionStartTime)
                    .put("transaction_end", transactionEndTime)
                    .put("session_transcript", sessionTranscript)
                    .put("location_latitude", locationLatitude.toString())
                    .put("location_longitude", locationLongitude.toString())
                    .end()
                    .build()
            )
        }.toByteArray()

    companion object {
        /**
         * Return a new instance of PresentationLogMetadata extracted from the passed-in CBOR encoded bytes
         */
        fun fromCborBytes(cborData: ByteArray): PresentationLogMetadata {
            val metadataItem = Util.cborDecode(cborData)
            return PresentationLogMetadata(
                PresentationTransactionStatus.fromNumber(
                    Util.cborMapExtractNumber(metadataItem, "transaction_status"),
                ),
                EngagementTypeDef.fromString(
                    Util.cborMapExtractString(metadataItem, "engagement_type")
                ),
                Util.cborMapExtractString(metadataItem, "error"),
                Util.cborMapExtractNumber(metadataItem, "transaction_start"),
                Util.cborMapExtractNumber(metadataItem, "transaction_end"),
                Util.cborMapExtractByteString(metadataItem, "session_transcript"),
                Util.cborMapExtractString(metadataItem, "location_latitude").toDouble(),
                Util.cborMapExtractString(metadataItem, "location_longitude").toDouble(),
            )
        }
    }

    /**
     * Defines the different types of statuses the end result of an mDL Presentation can be in
     */
    enum class PresentationTransactionStatus(val code: Long) {
        Complete(10), // everything went to plan

        Canceled(100), // user invoked cancellation

        Disconnected(200), // abrupt disconnection

        Error(300), // there was an error completing th presentation

        None(0) // default, uninitialized/unused
        ;

        companion object {
            /**
             * Given a status code integer, return the corresponding matching enum, or return None
             * if the specified code doesn't match any of the existing status codes
             */
            fun fromNumber(code: Long) =
                PresentationTransactionStatus.values().firstOrNull {
                    it.code == code
                } ?: None
        }
    }

    data class Builder(
        // provided from new instance of PresentationLogEntry.Builder
        private var transactionStartTime: Long,
        private var engagementType: EngagementTypeDef = EngagementTypeDef.NOT_ENGAGED,
        private var presentationTransactionStatus: PresentationTransactionStatus = PresentationTransactionStatus.None,
        private var error: Throwable? = null,
        private var transactionEndTime: Long = 0L,
        private var sessionTranscript: ByteArray = byteArrayOf(),
        private var locationLatitude: Double = 0.0,
        private var locationLongitude: Double = 0.0
    ) {
        /**
         * This function should not be accessible publicly since it's set when a PresentationLogEntry is instantiated
         */
        @VisibleForTesting
        fun transactionStartTimestamp(startMillis: Long) = apply {
            transactionStartTime = startMillis
        }

        fun engagementType(engagementType: EngagementTypeDef) = apply {
            this.engagementType = engagementType
        }

        fun transactionStatus(status: PresentationTransactionStatus) = apply {
            this.presentationTransactionStatus = status
        }

        fun transactionEndTimestamp(endMillis: Long? = null) = apply {
            if (transactionEndTime == 0L) { // if it's been previously set, don't change it
                transactionEndTime = endMillis ?: Timestamp.now().toEpochMilli()
            }
        }

        fun transactionComplete() = apply {
            transactionStatus(PresentationTransactionStatus.Complete)
            transactionEndTimestamp()
        }

        fun transactionCanceled() = apply {
            transactionStatus(PresentationTransactionStatus.Canceled)
            transactionEndTimestamp()
        }

        fun transactionDisconnected() = apply {
            transactionStatus(PresentationTransactionStatus.Disconnected)
            transactionEndTimestamp()
        }

        fun transactionError(throwable: Throwable? = null) = apply {
            transactionStatus(PresentationTransactionStatus.Error)
            throwable?.let {
                error = throwable
            }
        }

        fun sessionTranscript(byteArray: ByteArray) = apply {
            sessionTranscript = byteArray
        }

        fun location(latitude: Double, longitude: Double) = apply {
            locationLatitude = latitude
            locationLongitude = longitude
        }

        fun build() =
            PresentationLogMetadata(
                engagementType = engagementType,
                presentationTransactionStatus = presentationTransactionStatus,
                error = error?.message ?: "",
                transactionStartTime = transactionStartTime,
                transactionEndTime = transactionEndTime,
                sessionTranscript = sessionTranscript,
                locationLatitude = locationLatitude,
                locationLongitude = locationLongitude
            )
    }
}