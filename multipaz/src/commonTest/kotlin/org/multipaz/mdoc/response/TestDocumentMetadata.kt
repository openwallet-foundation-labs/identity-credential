package org.multipaz.mdoc.response

import org.multipaz.document.Document
import org.multipaz.document.NameSpacedData
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.isEmpty
import org.multipaz.document.AbstractDocumentMetadata
import kotlin.concurrent.Volatile

class TestDocumentMetadata private constructor(
    data: ByteString?,
    private val saveFn: suspend (data: ByteString) -> Unit
) : AbstractDocumentMetadata {
    override val provisioned: Boolean
        get() = false
    override val displayName: String
        get() = "Test Doc"
    override val typeDisplayName: String
        get() = "Test"
    override val cardArt: ByteString?
        get() = null
    override val issuerLogo: ByteString?
        get() = null
    override val other: ByteString?
        get() = null

    @Volatile
    var nameSpacedData: NameSpacedData
        private set

    init {
        nameSpacedData = if (data == null || data.isEmpty()) {
            NameSpacedData.Builder().build()
        } else {
            NameSpacedData.fromEncodedCbor(data.toByteArray())
        }
    }

    override suspend fun markAsProvisioned() {
    }

    override suspend fun setMetadata(
        displayName: String?,
        typeDisplayName: String?,
        cardArt: ByteString?,
        issuerLogo: ByteString?,
        other: ByteString?
    ) {
    }

    suspend fun setNameSpacedData(nameSpacedData: NameSpacedData) {
        this.nameSpacedData = nameSpacedData
        saveFn(ByteString(nameSpacedData.encodeAsCbor()))
    }

    companion object {
        suspend fun create(
            documentId: String,
            serializedData: ByteString?,
            saveFn: suspend (data: ByteString) -> Unit
        ): TestDocumentMetadata {
            return TestDocumentMetadata(serializedData, saveFn)
        }
    }
}

val Document.testMetadata: TestDocumentMetadata
    get() = metadata as TestDocumentMetadata
