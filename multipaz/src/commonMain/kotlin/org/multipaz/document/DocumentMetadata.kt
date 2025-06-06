package org.multipaz.document

import org.multipaz.cbor.annotation.CborSerializable
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.isEmpty
import kotlin.concurrent.Volatile

/**
 * Implementation of [AbstractDocumentMetadata] suitable for most use-cases.
 */
class DocumentMetadata private constructor(
    data: ByteString?,
    private val saveFn: suspend (data: ByteString) -> Unit
) : AbstractDocumentMetadata {
    @Volatile
    private var data: Data = if (data == null || data.isEmpty()) {
        Data()  // new document or SimpleDocumentMetadata never saved
    } else {
        Data.fromCbor(data.toByteArray())
    }

    override val provisioned get() = data.provisioned
    override val displayName get() = data.displayName
    override val typeDisplayName get() = data.typeDisplayName
    override val cardArt get() = data.cardArt
    override val issuerLogo get() = data.issuerLogo
    override val other get() = data.other

    override suspend fun markAsProvisioned() {
        val lastData = data
        val newData = Data(
            provisioned = true,
            displayName = lastData.displayName,
            typeDisplayName = lastData.typeDisplayName,
            cardArt = lastData.cardArt,
            issuerLogo = lastData.issuerLogo,
            other = lastData.other
        )
        data = newData
        saveFn(ByteString(data.toCbor()))
    }

    override suspend fun setMetadata(
        displayName: String?,
        typeDisplayName: String?,
        cardArt: ByteString?,
        issuerLogo: ByteString?,
        other: ByteString?
    ) {
        val lastData = data
        data = Data(
            provisioned = lastData.provisioned,
            displayName = displayName,
            typeDisplayName = typeDisplayName,
            cardArt = cardArt,
            issuerLogo = issuerLogo,
            other = other
        )
        saveFn(ByteString(data.toCbor()))
    }

    @CborSerializable
    internal data class Data(
        val provisioned: Boolean = false,
        val displayName: String? = null,
        val typeDisplayName: String? = null,
        val cardArt: ByteString? = null,
        val issuerLogo: ByteString? = null,
        val other: ByteString? = null
    ) {
        companion object
    }

    companion object {
        /**
         * The factory for [DocumentMetadata].
         *
         * @param documentId the document to create metadata for.
         * @param serializedData the serialized metadata.
         * @param saveFn a function to serialize the instance into serialized metadata.
         * @return the created [DocumentMetadata]
         */
        suspend fun create(
            documentId: String,
            serializedData: ByteString?,
            saveFn: suspend (data: ByteString) -> Unit
        ): DocumentMetadata {
            return DocumentMetadata(serializedData, saveFn)
        }
    }
}