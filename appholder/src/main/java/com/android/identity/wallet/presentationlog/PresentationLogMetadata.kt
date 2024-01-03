package com.android.identity.wallet.presentationlog;

import android.location.Location
import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborEncoder
import com.android.identity.internal.Util
import com.android.identity.util.Timestamp
import com.android.identity.wallet.util.EngagementType
import java.io.ByteArrayOutputStream

class PresentationLogMetadata private constructor(
    val presentationTransactionStatus: PresentationTransactionStatus,
    val engagementType: EngagementType,
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
                    .put("location_latitude", locationLatitude)
                    .put("location_longitude", locationLongitude)
                    .end()
                    .build()
            )
        }.toByteArray()


    companion object {
        /**
         * Return a new instance of PresentationLogMetadata extracted from the passed-in CBOR encoded data
         */
        fun fromCborData(cborData: ByteArray): PresentationLogMetadata {
            val metadataItem = Util.cborDecode(cborData)
            return PresentationLogMetadata(
                PresentationTransactionStatus.fromNumber(
                    Util.cborMapExtractNumber(metadataItem, "transaction_status"),
                ),
                EngagementType.fromString(
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
        private var presentationTransactionStatus: PresentationTransactionStatus = PresentationTransactionStatus.None,
        private var error: Throwable? = null,
        private var transactionStartTime: Long = 0L,
        private var transactionEndTime: Long = 0L,
        private var engagementType: EngagementType = EngagementType.NONE,
        private var sessionTranscript: ByteArray = byteArrayOf(),
        private var location: Location? = null
    ) {
        fun engagementType(engagementType: EngagementType) = apply {
            engagementStartTimestamp()
            this.engagementType = engagementType
        }

        fun engagementStartTimestamp() = apply {
            transactionStartTime = Timestamp.now().toEpochMilli()
        }

        fun transactionEndTimestamp() = apply {
            transactionEndTime = Timestamp.now().toEpochMilli()
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

        fun transactionStatus(status: PresentationTransactionStatus) = apply {
            this.presentationTransactionStatus = status
        }

        fun sessionTranscript(byteArray: ByteArray) = apply {
            sessionTranscript = byteArray
        }

        fun location(location: Location) = apply {
            this.location = location
        }

        fun build() =
            PresentationLogMetadata(
                engagementType = engagementType,
                presentationTransactionStatus = presentationTransactionStatus,
                error = error?.message ?: "",
                transactionStartTime = transactionStartTime,
                transactionEndTime = transactionEndTime,
                sessionTranscript = sessionTranscript,
                locationLatitude = location?.latitude ?: 0.0,
                locationLongitude = location?.longitude ?: 0.0
            )
    }
}
