package com.android.identity_credential.wallet

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.identity.android.securearea.AndroidKeystoreKeyInfo
import com.android.identity.cbor.Cbor
import com.android.identity.document.AuthenticationKey
import com.android.identity.document.Document
import com.android.identity.document.DocumentStore
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.issuance.DocumentCondition
import com.android.identity.issuance.DocumentExtensions.documentConfiguration
import com.android.identity.issuance.DocumentExtensions.documentIdentifier
import com.android.identity.issuance.DocumentExtensions.housekeeping
import com.android.identity.issuance.DocumentExtensions.isDeleted
import com.android.identity.issuance.DocumentExtensions.issuingAuthorityIdentifier
import com.android.identity.issuance.DocumentExtensions.state
import com.android.identity.issuance.IssuingAuthority
import com.android.identity.issuance.IssuingAuthorityRepository
import com.android.identity.mdoc.mso.MobileSecurityObjectParser
import com.android.identity.mdoc.mso.StaticAuthDataParser
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.credman.CredmanRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import java.lang.IllegalArgumentException

class CardViewModel : ViewModel() {

    val issuerDisplayData = mutableStateListOf<IssuerDisplayData>()

    val cards = mutableStateListOf<Card>()

    private lateinit var context: Context
    private lateinit var walletApplication: WalletApplication
    private lateinit var documentStore: DocumentStore
    private lateinit var issuingAuthorityRepository: IssuingAuthorityRepository
    private lateinit var secureAreaRepository: SecureAreaRepository
    private lateinit var documentTypeRepository: DocumentTypeRepository

    private fun syncWithCredman() {
        CredmanRegistry.registerCredentials(context, documentStore, documentTypeRepository)
    }

    fun getCard(cardId: String): Card? {
        for (card in cards) {
            if (card.id == cardId) {
                return card
            }
        }
        return null
    }

    fun getCardIndex(cardId: String): Int? {
        for (n in cards.indices) {
            if (cards[n].id == cardId) {
                return n
            }
        }
        return null
    }

    fun refreshCard(card: Card) {
        val document = documentStore.lookupDocument(card.id)
        if (document == null) {
            Logger.w(TAG, "No document with id ${card.id}")
            return
        }
        refreshDocument(document, true, true)
    }

    fun developerModeRequestUpdate(
        card: Card,
        requestRemoteDeletion: Boolean,
        notifyApplicationOfUpdate: Boolean
    ) {
        val document = documentStore.lookupDocument(card.id)
        if (document == null) {
            Logger.w(TAG, "No document with id ${card.id}")
            return
        }
        val issuer = issuingAuthorityRepository.lookupIssuingAuthority(document.issuingAuthorityIdentifier)
            ?: throw IllegalArgumentException("No issuer with id ${document.issuingAuthorityIdentifier}")

        viewModelScope.launch(Dispatchers.IO) {
            issuer.documentDeveloperModeRequestUpdate(
                document.documentIdentifier,
                requestRemoteDeletion,
                notifyApplicationOfUpdate)
        }
    }

    private val refreshDocumentMutex = Mutex()

    private fun refreshDocument(
        document: Document,
        forceUpdate: Boolean,
        showFeedback: Boolean
    ) {
        // For now we run the entire housekeeping sequence in response to
        // receiving an update from the issuer... this could be an user
        // preference or for some events - such as new PII - we could pop
        // up a notification asking if the user would like to update their
        // document
        //
        viewModelScope.launch(Dispatchers.IO) {
            // Especially during provisioning it's not uncommon to receive multiple
            // onDocumentStageChanged after each other... this ensures that we're
            // only running housekeeping() on a single document at a time.
            //
            // TODO: this can be done more elegantly
            //
            refreshDocumentMutex.withLock {
                val numAuthKeysRefreshed = document.housekeeping(
                    walletApplication,
                    issuingAuthorityRepository,
                    secureAreaRepository,
                    forceUpdate,
                )
                if (document.isDeleted) {
                    Logger.i(TAG, "Document ${document.name} was deleted, removing")
                    documentStore.deleteDocument(document.name)
                    return@launch
                }

                if (showFeedback) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            context,
                            String.format(
                                context.resources.getQuantityString(R.plurals.refreshed_authkey, numAuthKeysRefreshed),
                                numAuthKeysRefreshed
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    fun deleteCard(card: Card) {
        val document = documentStore.lookupDocument(card.id)
        if (document == null) {
            Logger.w(TAG, "No document with id ${card.id}")
            return
        }
        documentStore.deleteDocument(document.name)
    }

    private val documentStoreObserver = object : DocumentStore.Observer {
        override fun onDocumentAdded(document: Document) {
            addDocument(document)
            syncWithCredman()
        }
        
        override fun onDocumentDeleted(document: Document) {
            removeDocument(document)
            syncWithCredman()
        }
        
        override fun onDocumentChanged(document: Document) {
            updateDocument(document)
            syncWithCredman()
        }
    }

    private val issuingAuthorityRepositoryObserver = object : IssuingAuthorityRepository.Observer {
        override fun onDocumentStateChanged(
            issuingAuthority: IssuingAuthority,
            documentId: String
        ) {
            // Find the local [Document] instance, if any
            for (id in documentStore.listDocuments()) {
                val document = documentStore.lookupDocument(id)
                if (document == null) {
                    continue
                }
                if (document.issuingAuthorityIdentifier == issuingAuthority.configuration.identifier &&
                            document.documentIdentifier == documentId
                ) {

                    Logger.i(TAG, "Handling DocumentStateChanged on $documentId")

                    refreshDocument(document, true, false)
                }
            }
        }
    }

    private fun getStr(getStrId: Int): String {
        return context.resources.getString(getStrId)
    }

    private fun createCardForDocument(document: Document): Card? {
        val documentConfiguration = document.documentConfiguration
        val options = BitmapFactory.Options()
        options.inMutable = true
        val documentBitmap = BitmapFactory.decodeByteArray(
            documentConfiguration.cardArt,
            0,
            documentConfiguration.cardArt.size,
            options
        )

        val issuer = issuingAuthorityRepository.lookupIssuingAuthority(document.issuingAuthorityIdentifier)
        if (issuer == null) {
            Logger.w(TAG, "Unknown issuer ${document.issuingAuthorityIdentifier} for " +
                "document ${document.name}")
            return null
        }
        val issuerLogo = BitmapFactory.decodeByteArray(
            issuer.configuration.issuingAuthorityLogo,
            0,
            issuer.configuration.issuingAuthorityLogo.size,
            options
        )

        val statusString =
            when (document.state.condition) {
                DocumentCondition.PROOFING_REQUIRED -> getStr(R.string.card_view_model_status_proofing_required)
                DocumentCondition.PROOFING_PROCESSING -> getStr(R.string.card_view_model_status_proofing_processing)
                DocumentCondition.PROOFING_FAILED -> getStr(R.string.card_view_model_status_proofing_failed)
                DocumentCondition.CONFIGURATION_AVAILABLE -> getStr(R.string.card_view_model_status_configuration_available)
                DocumentCondition.READY -> getStr(R.string.card_view_model_status_ready)
                DocumentCondition.DELETION_REQUESTED -> getStr(R.string.card_view_model_status_deletion_requested)
            }

        val data = document.renderDocumentDetails(context, documentTypeRepository)

        val keyInfos = mutableStateListOf<CardKeyInfo>()
        for (authKey in document.certifiedAuthenticationKeys) {
            keyInfos.add(createCardInfoForAuthKey(authKey))
        }

        return Card(
            id = document.name,
            name = documentConfiguration.displayName,
            issuerName = issuer.configuration.issuingAuthorityName,
            issuerCardDescription = issuer.configuration.description,
            typeName = data.typeName,
            issuerLogo = issuerLogo,
            cardArtwork = documentBitmap,
            lastRefresh = Instant.fromEpochMilliseconds(document.state.timestamp),
            status = statusString,
            attributes = data.attributes,
            attributePortrait = data.portrait,
            attributeSignatureOrUsualMark = data.signatureOrUsualMark,
            keyInfos = keyInfos,
        )
    }

    private fun createCardInfoForAuthKey(authKey: AuthenticationKey): CardKeyInfo {

        val documentData = StaticAuthDataParser(authKey.issuerProvidedData).parse()
        val issuerAuthCoseSign1 = Cbor.decode(documentData.issuerAuth).asCoseSign1
        val encodedMsoBytes = Cbor.decode(issuerAuthCoseSign1.payload!!)
        val encodedMso = Cbor.encode(encodedMsoBytes.asTaggedEncodedCbor)
        val mso = MobileSecurityObjectParser(encodedMso).parse()


        val kvPairs = mutableMapOf<String, String>()
        kvPairs.put("Document Type", mso.docType)
        kvPairs.put("MSO Version", mso.version)
        kvPairs.put("Issuer Data Digest Algorithm", mso.digestAlgorithm)

        val deviceKeyInfo = authKey.secureArea.getKeyInfo(authKey.alias)
        kvPairs.put("Device Key Curve", deviceKeyInfo.publicKey.curve.name)
        kvPairs.put("Secure Area", authKey.secureArea.displayName)

        if (deviceKeyInfo is AndroidKeystoreKeyInfo) {
            val userAuthString =
                if (!deviceKeyInfo.isUserAuthenticationRequired) {
                    "None"
                } else {
                    val authTimeoutString =
                        if (deviceKeyInfo.userAuthenticationTimeoutMillis > 0) {
                            String.format(
                                "Timeout %.1f Seconds",
                                deviceKeyInfo.userAuthenticationTimeoutMillis / 1000
                            )
                        } else {
                            "Every use"
                        }
                    deviceKeyInfo.userAuthenticationTypes.toString() + " ($authTimeoutString)"
                }
            kvPairs.put("User Authentication", userAuthString)
            val isStrongBoxBacked =
                if (deviceKeyInfo.isStrongBoxBacked) {
                    "Yes"
                } else {
                    "No"
                }
            kvPairs.put("In StrongBox", isStrongBoxBacked)
        }

        kvPairs.put("Issuer Provided Data", "${authKey.issuerProvidedData.size} bytes")


        return CardKeyInfo(
            description = "ISO/IEC 18013-5:2021 mdoc MSO",
            usageCount = authKey.usageCount,
            signedAt = Instant.fromEpochMilliseconds(mso.signed.toEpochMilli()),
            validFrom = Instant.fromEpochMilliseconds(mso.validFrom.toEpochMilli()),
            validUntil = Instant.fromEpochMilliseconds(mso.validUntil.toEpochMilli()),
            expectedUpdate = mso.expectedUpdate?.let {
                Instant.fromEpochMilliseconds(it.toEpochMilli())
            },
            replacementPending = authKey.replacement != null,
            details = kvPairs
        )
    }

    private fun addDocument(document: Document) {
        createCardForDocument(document)?.let { cards.add(it) }
    }

    private fun removeDocument(document: Document) {
        val cardIndex = cards.indexOfFirst { it.id == document.name }
        if (cardIndex < 0) {
            Logger.w(TAG, "No card for document with id ${document.name}")
            return
        }
        cards.removeAt(cardIndex)
    }

    private fun updateDocument(document: Document) {
        val cardIndex = cards.indexOfFirst { it.id == document.name }
        if (cardIndex < 0) {
            Logger.w(TAG, "No card for document with id ${document.name}")
            return
        }
        createCardForDocument(document)?.let { cards[cardIndex] = it }
    }

    private fun addIssuer(issuingAuthority: IssuingAuthority) {
        issuerDisplayData.add(createIssuerDisplayData(issuingAuthority))
    }

    private fun createIssuerDisplayData(issuingAuthority: IssuingAuthority): IssuerDisplayData {
        val options = BitmapFactory.Options()
        options.inMutable = true
        val issuerLogoBitmap = BitmapFactory.decodeByteArray(
            issuingAuthority.configuration.issuingAuthorityLogo,
            0,
            issuingAuthority.configuration.issuingAuthorityLogo.size,
            options
        )

        return IssuerDisplayData(
            configuration = issuingAuthority.configuration,
            issuerLogo = issuerLogoBitmap,
        )
    }

    fun setData(
        context: Context,
        walletApplication: WalletApplication,
        documentStore: DocumentStore,
        issuingAuthorityRepository: IssuingAuthorityRepository,
        secureAreaRepository: SecureAreaRepository,
        documentTypeRepository: DocumentTypeRepository
    ) {
        this.context = context
        this.walletApplication = walletApplication
        this.documentStore = documentStore
        this.issuingAuthorityRepository = issuingAuthorityRepository
        this.secureAreaRepository = secureAreaRepository
        this.documentTypeRepository = documentTypeRepository

        documentStore.startObserving(documentStoreObserver)
        issuingAuthorityRepository.startObserving(issuingAuthorityRepositoryObserver)

        for (documentId in documentStore.listDocuments()) {
            val document = documentStore.lookupDocument(documentId)!!
            addDocument(document)
        }

        for (issuer in issuingAuthorityRepository.getIssuingAuthorities()) {
            addIssuer(issuer)
        }

        syncWithCredman()
    }

    override fun onCleared() {
        super.onCleared()
        if (this::documentStore.isInitialized) {
            documentStore.stopObserving(documentStoreObserver)
            issuingAuthorityRepository.stopObserving(issuingAuthorityRepositoryObserver)
        }
    }


    companion object {
        const val TAG = "CardViewModel"
    }

}
