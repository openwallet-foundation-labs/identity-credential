package org.multipaz.wallet.provisioning

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.document.AbstractDocumentMetadata
import org.multipaz.wallet.provisioning.remote.WalletServerProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.isEmpty
import org.multipaz.provisioning.DocumentConfiguration
import org.multipaz.provisioning.DocumentState
import org.multipaz.provisioning.IssuingAuthorityConfiguration

/**
 * [AbstractDocumentMetadata] implementation for the Wallet app.
 */
class WalletDocumentMetadata private constructor(
    serializedData: ByteString?,
    private val saveFn: suspend (data: ByteString) -> Unit
) : AbstractDocumentMetadata {
    private val lock = Mutex()
    private val data: Data = if (serializedData == null || serializedData.isEmpty()) {
        Data()
    } else {
        Data.fromCbor(serializedData.toByteArray())
    }

    override val provisioned get() = data.provisioned

    override suspend fun markAsProvisioned() = lock.withLock {
        check(!data.provisioned)
        data.provisioned = true
    }

    override suspend fun setMetadata(
        displayName: String?,
        typeDisplayName: String?,
        cardArt: ByteString?,
        issuerLogo: ByteString?,
        other: ByteString?
    ) {
        throw IllegalStateException("Shouldn't be reached")
    }

    override val displayName get() = data.documentConfiguration!!.displayName
    override val typeDisplayName get() = data.documentConfiguration!!.typeDisplayName
    override val cardArt get() = ByteString(data.documentConfiguration!!.cardArt)
    override val issuerLogo get() = ByteString(data.issuingAuthorityConfiguration!!.issuingAuthorityLogo)
    override val other get() = null

    /** The identifier for the [IssuingAuthority] the credential belongs to */
    val issuingAuthorityIdentifier: String? get() = data.issuingAuthorityIdentifier

    /**
     * The identifier for the document, as assigned by the issuer
     *
     * This is the _documentId_ value used in [IssuingAuthority] when communicating
     * with the issuing authority.
     */
    val documentIdentifier: String get() = data.documentIdentifier!!

    /**
     * The number of times a [DocumentConfiguration] has been downloaded from the issuer.
     */
    val numDocumentConfigurationsDownloaded: Long get() = data.numDocumentConfigurationsDownloaded

    suspend fun incrementNumDocumentConfigurationsDownloaded() = lock.withLock {
        data.numDocumentConfigurationsDownloaded++
        save()
    }

    /** The most recent [DocumentConfiguration] received from the issuer */
    val documentConfiguration: DocumentConfiguration get() = data.documentConfiguration!!

    suspend fun setDocumentConfiguration(documentConfiguration: DocumentConfiguration) =
        lock.withLock {
            data.documentConfiguration = documentConfiguration
            save()
        }

    /** The most recent [IssuingAuthorityConfiguration] received from the issuer */
    val issuingAuthorityConfiguration: IssuingAuthorityConfiguration
        get() = data.issuingAuthorityConfiguration!!

    suspend fun setIssuingAuthorityConfiguration(
        issuingAuthorityConfiguration: IssuingAuthorityConfiguration
    ) = lock.withLock {
            data.issuingAuthorityConfiguration = issuingAuthorityConfiguration
            save()
        }

    /**
     * The most recent [DocumentState] received from the issuer.
     *
     * This doesn't ping the issuer so the information may be stale. Applications can consult
     * the [DocumentState.timestamp] field to figure out the age of the state and use
     * [refreshState] to refresh it directly from the issuer server.
     */
    val state: DocumentState? get() = data.state

    /**
     * Gets the document state from the Issuer and updates the [.state] property with the value.
     *
     * Unlike reading from the [state] property, this performs network I/O to communicate
     * with the issuer.
     *
     * If the document doesn't exist (for example it could have been deleted recently) the
     * condition in [state] is set to [DocumentCondition.NO_SUCH_DOCUMENT].
     *
     * @param walletServerProvider the wallet server provider.
     */
    suspend fun refreshState(walletServerProvider: WalletServerProvider) {
        val issuerId = issuingAuthorityIdentifier ?: return
        val issuer = walletServerProvider.getIssuingAuthority(issuerId)
        val newState = issuer.getState(documentIdentifier)
        lock.withLock {
            data.state = newState
            save()
        }
    }

    suspend fun setDocumentSlot(documentSlot: Int) =
        lock.withLock {
            data.documentSlot = documentSlot
            save()
        }

    var directAccessDocumentSlot: Int = -1
        get() : Int {
            return if (data.documentSlot != null) {
                data.documentSlot!!
            } else {
                -1
            }
        }

    suspend fun initialize(
        issuingAuthorityIdentifier: String,
        documentIdentifier: String,
        documentConfiguration: DocumentConfiguration,
        issuingAuthorityConfiguration: IssuingAuthorityConfiguration
    ) {
        lock.withLock {
            data.issuingAuthorityIdentifier = issuingAuthorityIdentifier
            data.documentIdentifier = documentIdentifier
            data.documentConfiguration = documentConfiguration
            data.issuingAuthorityConfiguration = issuingAuthorityConfiguration
            save()
        }
    }

    private suspend fun save() {
        check(lock.isLocked)
        saveFn(ByteString(data.toCbor()))
    }

    @CborSerializable
    data class Data(
        @Volatile var provisioned: Boolean = false,
        @Volatile var issuingAuthorityIdentifier: String? = null,
        @Volatile var documentIdentifier: String? = null,
        @Volatile var numDocumentConfigurationsDownloaded: Long = 0,
        @Volatile var documentConfiguration: DocumentConfiguration? = null,
        @Volatile var issuingAuthorityConfiguration: IssuingAuthorityConfiguration? = null,
        @Volatile var state: DocumentState? = null,
        @Volatile var documentSlot: Int? = null,
        @Volatile var other: ByteString? = null
    ) {
        companion object
    }

    companion object {
        suspend fun create(
            documentId: String,
            serializedData: ByteString?,
            saveFn: suspend (data: ByteString) -> Unit
        ): WalletDocumentMetadata {
            return WalletDocumentMetadata(serializedData, saveFn)
        }
    }
}
