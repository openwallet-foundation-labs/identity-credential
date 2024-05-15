package com.android.identity_credential.wallet

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import com.android.identity.android.securearea.AndroidKeystoreCreateKeySettings
import com.android.identity.android.securearea.AndroidKeystoreKeyInfo
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.cbor.Cbor
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.document.Document
import com.android.identity.document.DocumentStore
import com.android.identity.document.DocumentUtil
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.documenttype.knowntypes.DrivingLicense
import com.android.identity.issuance.DocumentCondition
import com.android.identity.issuance.DocumentExtensions.documentConfiguration
import com.android.identity.issuance.DocumentExtensions.documentIdentifier
import com.android.identity.issuance.DocumentExtensions.isDeleted
import com.android.identity.issuance.DocumentExtensions.issuingAuthorityIdentifier
import com.android.identity.issuance.DocumentExtensions.numDocumentConfigurationsDownloaded
import com.android.identity.issuance.DocumentExtensions.refreshState
import com.android.identity.issuance.DocumentExtensions.state
import com.android.identity.issuance.CredentialFormat
import com.android.identity.issuance.CredentialRequest
import com.android.identity.issuance.IssuingAuthority
import com.android.identity.issuance.IssuingAuthorityRepository
import com.android.identity.mdoc.mso.MobileSecurityObjectParser
import com.android.identity.mdoc.mso.StaticAuthDataParser
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.KeyInvalidatedException
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.securearea.software.SoftwareCreateKeySettings
import com.android.identity.securearea.software.SoftwareKeyInfo
import com.android.identity.securearea.software.SoftwareSecureArea
import com.android.identity.util.Logger
import com.android.identity.util.Timestamp
import com.android.identity_credential.wallet.credman.CredmanRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class DocumentModel(
    private val context: Context,
    private val settingsModel: SettingsModel,
    private val documentStore: DocumentStore,
    private val issuingAuthorityRepository: IssuingAuthorityRepository,
    private val secureAreaRepository: SecureAreaRepository,
    private val documentTypeRepository: DocumentTypeRepository,
    private val walletApplication: WalletApplication
) {
    companion object {
        private const val TAG = "DocumentModel"
    }

    val issuerDisplayData = mutableStateListOf<IssuerDisplayData>()

    val documentInfos = mutableStateListOf<DocumentInfo>()

    fun getDocumentInfo(cardId: String): DocumentInfo? {
        for (card in documentInfos) {
            if (card.documentId == cardId) {
                return card
            }
        }
        return null
    }

    fun getCardIndex(cardId: String): Int? {
        for (n in documentInfos.indices) {
            if (documentInfos[n].documentId == cardId) {
                return n
            }
        }
        return null
    }

    /**
     * Refreshes a card.
     *
     * This may throw e.g. [ScreenLockRequiredException] if for example the document
     * is set up to use auth-backed Android Keystore keys but the device has no LSKF. So
     * the caller should always try and catch exceptions.
     *
     * @param documentInfo the card to refresh
     * @throws Exception if an error occurs
     */
    suspend fun refreshCard(documentInfo: DocumentInfo) {
        val document = documentStore.lookupDocument(documentInfo.documentId)
        if (document == null) {
            Logger.w(TAG, "No document with id ${documentInfo.documentId}")
            return
        }
        val numCredentialsRefreshed = syncDocumentWithIssuer(document)
        if (document.isDeleted) {
            Logger.i(TAG, "Document ${document.name} was deleted, removing")
            documentStore.deleteDocument(document.name)
        }
        if (numCredentialsRefreshed >= 0) {
            val toastText =
                if (numCredentialsRefreshed == 0)
                    getStr(R.string.document_model_refresh_no_update_toast_text)
                else String.format(
                    context.resources.getQuantityString(
                        R.plurals.refreshed_cred,
                        numCredentialsRefreshed
                    ),
                    numCredentialsRefreshed
                )
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    toastText,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    suspend fun developerModeRequestUpdate(
        documentInfo: DocumentInfo,
        requestRemoteDeletion: Boolean,
        notifyApplicationOfUpdate: Boolean
    ) {
        val document = documentStore.lookupDocument(documentInfo.documentId)
        if (document == null) {
            Logger.w(TAG, "No document with id ${documentInfo.documentId}")
            return
        }
        val issuer =
            issuingAuthorityRepository.lookupIssuingAuthority(document.issuingAuthorityIdentifier)
                ?: throw IllegalArgumentException("No issuer with id ${document.issuingAuthorityIdentifier}")

        issuer.developerModeRequestUpdate(
            document.documentIdentifier,
            requestRemoteDeletion,
            notifyApplicationOfUpdate
        )
    }

    fun deleteCard(documentInfo: DocumentInfo) {
        val document = documentStore.lookupDocument(documentInfo.documentId)
        if (document == null) {
            Logger.w(TAG, "No document with id ${documentInfo.documentId}")
            return
        }
        documentStore.deleteDocument(document.name)
    }

    private fun getStr(getStrId: Int): String {
        return context.resources.getString(getStrId)
    }

    private fun createCardForDocument(document: Document): DocumentInfo? {
        val documentConfiguration = document.documentConfiguration
        val options = BitmapFactory.Options()
        options.inMutable = true
        val documentBitmap = BitmapFactory.decodeByteArray(
            documentConfiguration.cardArt,
            0,
            documentConfiguration.cardArt.size,
            options
        )

        val issuer =
            issuingAuthorityRepository.lookupIssuingAuthority(document.issuingAuthorityIdentifier)
        if (issuer == null) {
            Logger.w(
                TAG, "Unknown issuer ${document.issuingAuthorityIdentifier} for " +
                        "document ${document.name}"
            )
            return null
        }
        val issuerLogo = BitmapFactory.decodeByteArray(
            issuer.configuration.issuingAuthorityLogo,
            0,
            issuer.configuration.issuingAuthorityLogo.size,
            options
        )

        val data = document.renderDocumentDetails(context, documentTypeRepository)

        val keyInfos = mutableStateListOf<CredentialInfo>()
        for (mdocCred in document.certifiedCredentials.filter { it.domain == WalletApplication.CREDENTIAL_DOMAIN }) {
            keyInfos.add(createCardInfoForCredential(mdocCred as MdocCredential))
        }

        var attentionNeeded = false
        val statusString = when (document.state.condition) {
            DocumentCondition.PROOFING_REQUIRED -> getStr(R.string.document_model_status_proofing_required)
            DocumentCondition.PROOFING_PROCESSING -> getStr(R.string.document_model_status_proofing_processing)
            DocumentCondition.PROOFING_FAILED -> getStr(R.string.document_model_status_proofing_failed)
            DocumentCondition.CONFIGURATION_AVAILABLE -> {
                attentionNeeded = true
                getStr(R.string.document_model_status_configuration_available)
            }

            DocumentCondition.READY -> {
                // TODO: hmm, this can become stale as time progresses... maybe we need
                //  a timer to refresh the model to reflect this.
                if (!document.hasUsableCredential(Clock.System.now())) {
                    attentionNeeded = true
                    getStr(R.string.document_model_status_no_usable_credential)
                } else {
                    getStr(R.string.document_model_status_ready)
                }
            }

            DocumentCondition.DELETION_REQUESTED -> getStr(R.string.document_model_status_deletion_requested)
        }

        return DocumentInfo(
            documentId = document.name,
            attentionNeeded = attentionNeeded,
            requireUserAuthenticationToViewDocument = documentConfiguration.requireUserAuthenticationToViewDocument,
            name = documentConfiguration.displayName,
            issuerName = issuer.configuration.issuingAuthorityName,
            issuerDocumentDescription = issuer.configuration.description,
            typeName = data.typeName,
            issuerLogo = issuerLogo,
            documentArtwork = documentBitmap,
            lastRefresh = document.state.timestamp,
            status = statusString,
            attributes = data.attributes,
            attributePortrait = data.portrait,
            attributeSignatureOrUsualMark = data.signatureOrUsualMark,
            credentialInfos = keyInfos,
        )
    }

    private fun createCardInfoForCredential(mdocCredential: MdocCredential): CredentialInfo {

        val credentialData = StaticAuthDataParser(mdocCredential.issuerProvidedData).parse()
        val issuerAuthCoseSign1 = Cbor.decode(credentialData.issuerAuth).asCoseSign1
        val encodedMsoBytes = Cbor.decode(issuerAuthCoseSign1.payload!!)
        val encodedMso = Cbor.encode(encodedMsoBytes.asTaggedEncodedCbor)
        val mso = MobileSecurityObjectParser(encodedMso).parse()


        val kvPairs = mutableMapOf<String, String>()
        kvPairs.put("Document Type", mso.docType)
        kvPairs.put("MSO Version", mso.version)
        kvPairs.put("Issuer Data Digest Algorithm", mso.digestAlgorithm)

        try {
            val deviceKeyInfo = mdocCredential.secureArea.getKeyInfo(mdocCredential.alias)
            kvPairs.put("Device Key Curve", deviceKeyInfo.publicKey.curve.name)
            kvPairs.put("Device Key Purposes", deviceKeyInfo.keyPurposes.toString())
            kvPairs.put("Secure Area", mdocCredential.secureArea.displayName)

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
            } else if (deviceKeyInfo is SoftwareKeyInfo) {
                val passphraseProtected =
                    if (deviceKeyInfo.isPassphraseProtected) {
                        val constraints = deviceKeyInfo.passphraseConstraints
                        if (constraints == null) {
                            "Yes"
                        } else {
                            if (constraints.requireNumerical) {
                                if (constraints.maxLength == Int.MAX_VALUE) {
                                    "Numerical, at least ${constraints.minLength} digits"
                                } else if (constraints.minLength == constraints.maxLength) {
                                    "Numerical, ${constraints.minLength} digits"
                                } else {
                                    "Numerical, ${constraints.minLength}-${constraints.maxLength} digits"
                                }
                            } else {
                                if (constraints.maxLength == Int.MAX_VALUE) {
                                    "At least ${constraints.minLength} characters"
                                } else if (constraints.minLength == constraints.maxLength) {
                                    "${constraints.minLength} characters"
                                } else {
                                    "${constraints.minLength}-${constraints.maxLength} characters"
                                }
                            }
                        }
                    } else {
                        "None"
                    }
                kvPairs.put("Passphrase", passphraseProtected)
            }
        } catch (e: KeyInvalidatedException) {
            kvPairs.put("Device Key", "Invalidated")
        }


        kvPairs.put("Issuer Provided Data", "${mdocCredential.issuerProvidedData.size} bytes")


        return CredentialInfo(
            description = "ISO/IEC 18013-5:2021 mdoc MSO",
            usageCount = mdocCredential.usageCount,
            signedAt = Instant.fromEpochMilliseconds(mso.signed.toEpochMilli()),
            validFrom = Instant.fromEpochMilliseconds(mso.validFrom.toEpochMilli()),
            validUntil = Instant.fromEpochMilliseconds(mso.validUntil.toEpochMilli()),
            expectedUpdate = mso.expectedUpdate?.let {
                Instant.fromEpochMilliseconds(it.toEpochMilli())
            },
            replacementPending = mdocCredential.replacement != null,
            details = kvPairs
        )
    }

    private fun addDocument(document: Document) {
        createCardForDocument(document)?.let { documentInfos.add(it) }
    }

    private fun removeDocument(document: Document) {
        val cardIndex = documentInfos.indexOfFirst { it.documentId == document.name }
        if (cardIndex < 0) {
            Logger.w(TAG, "No card for document with id ${document.name}")
            return
        }
        documentInfos.removeAt(cardIndex)
    }

    private fun updateDocument(document: Document) {
        val cardIndex = documentInfos.indexOfFirst { it.documentId == document.name }
        if (cardIndex < 0) {
            Logger.w(TAG, "No card for document with id ${document.name}")
            return
        }
        createCardForDocument(document)?.let { documentInfos[cardIndex] = it }
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

    private fun updateCredman() {
        CredmanRegistry.registerCredentials(context, documentStore, documentTypeRepository)
    }

    init {
        // This keeps our `cards` property in sync with the contents of `documentStore`.
        //
        // Since DOCUMENT_UPDATED is very chatty (an event for every single property
        // change in the Document) we want to coalesce many calls in a short timeframe into
        // timed chunks and process those in batches (at most one per second).
        //
        val batchDuration = 1.seconds
        val batchedUpdateFlow = MutableSharedFlow<String>()
        CoroutineScope(Dispatchers.IO).launch {
            // This ensure we process both flows sequentially, that is, that calls modifying our
            // model (addDocument, removeDocument, updateDocument) doesn't happen at
            // the same time...
            //
            runBlocking {
                documentStore.eventFlow
                    .onEach { (eventType, document) ->
                        Logger.i(TAG, "DocumentStore event $eventType ${document.name}")
                        when (eventType) {
                            DocumentStore.EventType.DOCUMENT_ADDED -> {
                                addDocument(document)
                                updateCredman()
                            }

                            DocumentStore.EventType.DOCUMENT_DELETED -> {
                                removeDocument(document)
                                updateCredman()
                            }

                            DocumentStore.EventType.DOCUMENT_UPDATED -> {
                                // Store the name rather than instance to handle the case that the
                                // document may have been deleted between now and the time it's
                                // processed below...
                                batchedUpdateFlow.emit(document.name)
                            }
                        }
                    }
                    .launchIn(this)

                batchedUpdateFlow.timedChunk(batchDuration)
                    .onEach {
                        it.distinct().forEach {
                            documentStore.lookupDocument(it)?.let {
                                Logger.i(TAG, "Processing delayed update event ${it.name}")
                                updateDocument(it)
                                updateCredman()
                            }
                        }
                    }
                    .launchIn(this)
            }
        }

        // This processes events from `issuingAuthorityRepository` which is a stream of events
        // for when an issuer notifies that one of their documents has an update.
        //
        // For every event, we  initiate a sequence of calls into the issuer to get the latest.
        //
        CoroutineScope(Dispatchers.IO).launch {
            issuingAuthorityRepository.eventFlow.collect { (issuingAuthority, documentId) ->
                // Find the local [Document] instance, if any
                for (id in documentStore.listDocuments()) {
                    val document = documentStore.lookupDocument(id)
                    if (document?.issuingAuthorityIdentifier == issuingAuthority.configuration.identifier &&
                        document.documentIdentifier == documentId
                    ) {
                        Logger.i(TAG, "Handling DocumentStateChanged on $documentId")
                        try {
                            syncDocumentWithIssuer(document)
                            if (document.isDeleted) {
                                Logger.i(TAG, "Document ${document.name} was deleted, removing")
                                documentStore.deleteDocument(document.name)
                            }
                        } catch (e: Throwable) {
                            Logger.e(TAG, "Error when syncing with issuer", e)

                            // For example, this can happen if the user removed the LSKF and the
                            // issuer wants auth-bound keys when using Android Keystore. In this
                            // case [ScreenLockRequiredException] is thrown... it can also happen
                            // if e.g. there's intermittent network connectivity.
                            //
                            // There's no point in bubbling this up to the user and since we have
                            // a background service running [syncDocumentWithIssuer] _anyway_
                            // we'll recover at some point.
                            //
                        }
                    }
                }
            }
        }

        // If the LSKF is removed, make sure we delete all credentials with invalidated keys...
        //
        settingsModel.screenLockIsSetup.observeForever() {
            Logger.i(
                TAG, "screenLockIsSetup changed to " +
                        "${settingsModel.screenLockIsSetup.value}"
            )
            for (documentId in documentStore.listDocuments()) {
                val document = documentStore.lookupDocument(documentId)!!
                document.deleteInvalidatedCredentials()
            }
        }

        // Initial data population and export to Credman
        //
        for (issuer in issuingAuthorityRepository.getIssuingAuthorities()) {
            addIssuer(issuer)
        }
        for (documentId in documentStore.listDocuments()) {
            documentStore.lookupDocument(documentId)?.let {
                addDocument(it)
            }
        }
        updateCredman()
    }

    /**
     * Called periodically by [WalletApplication.SyncDocumentWithIssuerWorker].
     */
    fun periodicSyncForAllDocuments() {
        runBlocking {
            for (documentId in documentStore.listDocuments()) {
                documentStore.lookupDocument(documentId)?.let { document ->
                    Logger.i(TAG, "Periodic sync for ${document.name}")
                    try {
                        syncDocumentWithIssuer(document)
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Error when syncing with issuer", e)
                    }
                }
            }
        }
    }

    /**
     * This performs all the essential bits of housekeeping for a document, including
     * requesting CPOs, checking for PII updates, and so on.
     *
     * This will also post notifications to the user for key events, including
     * - when the document has been remote deleted
     * - when the document has been successfully proofed
     * - when there are PII updates
     *
     * The model itself isn't updated.
     *
     * This may throw e.g. [ScreenLockRequiredException] if for example the document
     * is set up to use auth-backed Android Keystore keys but the device has no LSKF. So
     * the caller should always try and catch exceptions.
     *
     * @param issuingAuthorityRepository a repository of issuing authorities.
     * @param secureAreaRepository a repository of Secure Area implementations available.
     * @param forceUpdate if true, throttling will be bypassed.
     * @return number of auth keys refreshed or -1 if the document was deleted
     * @throws IllegalArgumentException if the issuer isn't known.
     */
    private suspend fun syncDocumentWithIssuer(
        document: Document
    ): Int {
        val iaId = document.issuingAuthorityIdentifier
        val issuer = issuingAuthorityRepository.lookupIssuingAuthority(iaId)
            ?: throw IllegalArgumentException("No issuer with id $iaId")

        // TODO: this should all come from issuer configuration
        val numCreds = 3
        val minValidTimeMillis = 30 * 24 * 3600L
        val credDomain = WalletApplication.CREDENTIAL_DOMAIN
        val docType = DrivingLicense.MDL_DOCTYPE

        // OK, let's see if configuration is available
        if (!document.refreshState(issuingAuthorityRepository)) {
            walletApplication.postNotificationForDocument(
                document,
                walletApplication.applicationContext.getString(
                    R.string.notifications_document_deleted_by_issuer
                )
            )
        }

        if (document.state.condition == DocumentCondition.CONFIGURATION_AVAILABLE) {
            document.certifiedCredentials.forEach { it.delete() }
            document.pendingCredentials.forEach { it.delete() }
            document.documentConfiguration = issuer.getDocumentConfiguration(
                document.documentIdentifier
            )

            // Notify the user that the document is either ready or an update has been downloaded.
            if (document.numDocumentConfigurationsDownloaded == 0L) {
                walletApplication.postNotificationForDocument(
                    document,
                    walletApplication.applicationContext.getString(
                        R.string.notifications_document_application_approved_and_ready_to_use
                    )
                )
            } else {
                walletApplication.postNotificationForDocument(
                    document,
                    walletApplication.applicationContext.getString(
                        R.string.notifications_document_update_from_issuer
                    )
                )
            }
            document.numDocumentConfigurationsDownloaded += 1

            if (!document.refreshState(issuingAuthorityRepository)) {
                return -1
            }
        }

        // Request new CPOs if needed
        if (document.state.condition == DocumentCondition.READY) {
            val now = Timestamp.now()
            // First do a dry-run to see how many pending credentials will be created
            val numPendingCredentialsToCreate = DocumentUtil.managedCredentialHelper(
                document,
                credDomain,
                null,
                now,
                numCreds,
                1,
                minValidTimeMillis,
                true
            )
            if (numPendingCredentialsToCreate > 0) {
                val requestCpoFlow =
                    issuer.requestCredentials(document.documentIdentifier)
                val credConfig = requestCpoFlow.getCredentialConfiguration()

                val secureArea = secureAreaRepository.getImplementation(credConfig.secureAreaIdentifier)
                    ?: throw IllegalArgumentException("No SecureArea ${credConfig.secureAreaIdentifier}")
                val authKeySettings: CreateKeySettings = when (secureArea) {
                    is AndroidKeystoreSecureArea -> {
                        AndroidKeystoreCreateKeySettings.Builder(credConfig.challenge)
                            .applyConfiguration(Cbor.decode(credConfig.secureAreaConfiguration))
                            .build()
                    }

                    is SoftwareSecureArea -> {
                        SoftwareCreateKeySettings.Builder(credConfig.challenge)
                            .applyConfiguration(Cbor.decode(credConfig.secureAreaConfiguration))
                            .build()
                    }

                    else -> throw IllegalStateException("Unexpected SecureArea $secureArea")
                }
                DocumentUtil.managedCredentialHelper(
                    document,
                    credDomain,
                    {toBeReplaced -> MdocCredential(
                        document,
                        toBeReplaced,
                        credDomain,
                        secureArea,
                        authKeySettings,
                        docType
                    )},
                    now,
                    numCreds,
                    1,
                    minValidTimeMillis,
                    false
                )
                val credentialRequests = mutableListOf<CredentialRequest>()
                for (pendingMdocCredential in document.pendingCredentials) {
                    credentialRequests.add(
                        CredentialRequest(
                            CredentialFormat.MDOC_MSO,
                            (pendingMdocCredential as MdocCredential).attestation
                        )
                    )
                }
                requestCpoFlow.sendCredentials(credentialRequests)
                requestCpoFlow.complete()
                if (!document.refreshState(issuingAuthorityRepository)) {
                    return -1
                }
            }
        }

        var numCredentialsRefreshed = 0
        if (document.state.numAvailableCredentials > 0) {
            for (cpo in issuer.getCredentials(document.documentIdentifier)) {
                val pendingMdocCredential = document.pendingCredentials.find {
                    it.domain == WalletApplication.CREDENTIAL_DOMAIN &&
                    (it as MdocCredential).attestation.certificates.first().publicKey.equals(cpo.secureAreaBoundKey)
                }
                if (pendingMdocCredential == null) {
                    Logger.w(TAG, "No pending Credential for pubkey ${cpo.secureAreaBoundKey}")
                    continue
                }
                pendingMdocCredential.certify(
                    cpo.data,
                    Timestamp.ofEpochMilli(cpo.validFrom.toEpochMilliseconds()),
                    Timestamp.ofEpochMilli(cpo.validUntil.toEpochMilliseconds())
                )
                numCredentialsRefreshed += 1
            }
            if (!document.refreshState(issuingAuthorityRepository)) {
                return -1
            }
        }
        return numCredentialsRefreshed
    }
}

private class TimedChunkFlow<T>(sourceFlow: Flow<T>, period: Duration) {
    private val chunkLock = ReentrantLock()
    private var chunk = mutableListOf<T>()
    val resultFlow = flow {
        sourceFlow.collect {
            val localChunk = chunkLock.withLock {
                chunk.add(it)
                chunk
            }
            emit(localChunk)
        }
    }.sample(period).onEach {
        chunkLock.withLock {
            chunk = mutableListOf()
        }
    }
}

fun <T> Flow<T>.timedChunk(periodMs: Duration): Flow<List<T>> = TimedChunkFlow(this, periodMs).resultFlow
