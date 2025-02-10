package com.android.identity.testapp

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.document.Document
import com.android.identity.document.DocumentMetadata
import com.android.identity.document.NameSpacedData
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.isEmpty
import kotlin.concurrent.Volatile

class TestAppDocumentMetadata private constructor(
    serializedData: ByteString?,
    private val saveFn: suspend (data: ByteString) -> Unit
) : DocumentMetadata {
    @Volatile
    private var data: TestData

    override val provisioned: Boolean
        get() = true
    override val displayName: String
        get() = data.displayName
    override val typeDisplayName: String
        get() = data.typeDisplayName
    override val cardArt: ByteString
        get() = data.cardArt
    override val issuerLogo: ByteString?
        get() = null

    override val nameSpacedData: NameSpacedData
        get() = data.nameSpacedData

    init {
        data = if (serializedData == null || serializedData.isEmpty()) {
            TestData()
        } else {
            TestData.fromCbor(serializedData.toByteArray())
        }
    }

    suspend fun initialize(
        displayName: String,
        typeDisplayName: String,
        cardArt: ByteString,
        nameSpacedData: NameSpacedData
    ) {
        val data = TestData(displayName, typeDisplayName, cardArt, nameSpacedData)
        this.data = data
        saveFn(ByteString(data.toCbor()))
    }

    override suspend fun documentDeleted() {
    }

    companion object {
        suspend fun create(
            documentId: String,
            serializedData: ByteString?,
            saveFn: suspend (data: ByteString) -> Unit
        ): TestAppDocumentMetadata {
            return TestAppDocumentMetadata(serializedData, saveFn)
        }
    }

    @CborSerializable
    data class TestData(
        val displayName: String = "",
        val typeDisplayName: String = "",
        val cardArt: ByteString = ByteString(),
        val nameSpacedData: NameSpacedData = NameSpacedData.Builder().build()
    ) {
        companion object
    }
}

val Document.testMetadata: TestAppDocumentMetadata
    get() = metadata as TestAppDocumentMetadata