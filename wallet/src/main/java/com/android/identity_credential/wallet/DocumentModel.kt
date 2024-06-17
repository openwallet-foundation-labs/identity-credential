package com.android.identity_credential.wallet

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateListOf
import com.android.identity.securearea.SecureArea
import com.android.identity.android.securearea.AndroidKeystoreCreateKeySettings
import com.android.identity.android.securearea.AndroidKeystoreKeyInfo
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.cbor.Cbor
import com.android.identity.credential.Credential
import com.android.identity.credential.SecureAreaBoundCredential
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.document.Document
import com.android.identity.document.DocumentStore
import com.android.identity.document.DocumentUtil
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.issuance.DocumentCondition
import com.android.identity.issuance.DocumentExtensions.documentConfiguration
import com.android.identity.issuance.DocumentExtensions.documentIdentifier
import com.android.identity.issuance.DocumentExtensions.issuingAuthorityIdentifier
import com.android.identity.issuance.DocumentExtensions.numDocumentConfigurationsDownloaded
import com.android.identity.issuance.DocumentExtensions.refreshState
import com.android.identity.issuance.DocumentExtensions.state
import com.android.identity.issuance.CredentialFormat
import com.android.identity.issuance.CredentialRequest
import com.android.identity.issuance.DocumentExtensions.issuingAuthorityConfiguration
import com.android.identity.issuance.IssuingAuthority
import com.android.identity.issuance.IssuingAuthorityConfiguration
import com.android.identity.issuance.IssuingAuthorityNotification
import com.android.identity.issuance.remote.WalletServerProvider
import com.android.identity.mdoc.mso.MobileSecurityObjectParser
import com.android.identity.mdoc.mso.StaticAuthDataParser
import com.android.identity.sdjwt.SdJwtVerifiableCredential
import com.android.identity.sdjwt.credential.SdJwtVcCredential
import com.android.identity.sdjwt.vc.JwtBody
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.KeyInvalidatedException
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.securearea.software.SoftwareCreateKeySettings
import com.android.identity.securearea.software.SoftwareKeyInfo
import com.android.identity.securearea.software.SoftwareSecureArea
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.credman.CredmanRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
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
    private val secureAreaRepository: SecureAreaRepository,
    private val documentTypeRepository: DocumentTypeRepository,
    private val walletServerProvider: WalletServerProvider,
    private val walletApplication: WalletApplication
) {
    companion object {
        private const val TAG = "DocumentModel"
    }

    val documentInfos = mutableStateListOf<DocumentInfo>()

    /**
     * Given the [documentId] of a stored [Document], find & return the corresponding [DocumentInfo]
     * or [null] if none can be found.
     * @param documentId the document name that is used by the [DocumentStore].
     * @return the [DocumentInfo] that matches the specified [documentId] or [null] for not matches.
     */
    fun getDocumentInfo(documentId: String): DocumentInfo? =
        documentInfos.firstOrNull { it.documentId == documentId }


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
        syncDocumentWithIssuer(document)
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
        val issuer = walletServerProvider.getIssuingAuthority(document.issuingAuthorityIdentifier)
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

        val issuerConfiguration = document.issuingAuthorityConfiguration
        val issuerLogo = BitmapFactory.decodeByteArray(
            issuerConfiguration.issuingAuthorityLogo,
            0,
            issuerConfiguration.issuingAuthorityLogo.size,
            options
        )

        val data = document.renderDocumentDetails(context, documentTypeRepository)

        val keyInfos = mutableStateListOf<CredentialInfo>()
        for (credential in document.certifiedCredentials) {
            when (credential) {
                is MdocCredential -> {
                    keyInfos.add(createCardInfoForMdocCredential(credential))
                }

                is SdJwtVcCredential -> {
                    keyInfos.add(createCardInfoForSdJwtVcCredential(credential))
                }

                else -> {
                    Logger.w(TAG, "No CardInfo support for ${credential::class}")
                }
            }
        }

        var attentionNeeded = false
        val statusString = when (document.state.condition) {
            DocumentCondition.NO_SUCH_DOCUMENT -> getStr(R.string.document_model_status_no_such_document)
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
                val usableCredentials = document.countUsableCredentials(Clock.System.now())
                if (usableCredentials.numCredentials == 0) {
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
            issuerName = issuerConfiguration.issuingAuthorityName,
            issuerDocumentDescription = issuerConfiguration.issuingAuthorityDescription,
            typeName = documentConfiguration.typeDisplayName,
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

    private fun addSecureAreaBoundCredentialInfo(
        credential: SecureAreaBoundCredential,
        kvPairs: MutableMap<String, String>
    ) {
        try {
            val deviceKeyInfo = credential.secureArea.getKeyInfo(credential.alias)
            kvPairs.put("Device Key Curve", deviceKeyInfo.publicKey.curve.name)
            kvPairs.put("Device Key Purposes", deviceKeyInfo.keyPurposes.toString())
            kvPairs.put("Secure Area", credential.secureArea.displayName)

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
    }

    private fun createCardInfoForMdocCredential(mdocCredential: MdocCredential): CredentialInfo {

        val credentialData = StaticAuthDataParser(mdocCredential.issuerProvidedData).parse()
        val issuerAuthCoseSign1 = Cbor.decode(credentialData.issuerAuth).asCoseSign1
        val encodedMsoBytes = Cbor.decode(issuerAuthCoseSign1.payload!!)
        val encodedMso = Cbor.encode(encodedMsoBytes.asTaggedEncodedCbor)
        val mso = MobileSecurityObjectParser(encodedMso).parse()


        val kvPairs = mutableMapOf<String, String>()
        kvPairs.put("Document Type", mso.docType)
        kvPairs.put("MSO Version", mso.version)
        kvPairs.put("Issuer Data Digest Algorithm", mso.digestAlgorithm)

        addSecureAreaBoundCredentialInfo(mdocCredential, kvPairs)

        kvPairs.put("Issuer Provided Data", "${mdocCredential.issuerProvidedData.size} bytes")

        return CredentialInfo(
            format = CredentialFormat.MDOC_MSO,
            description = "ISO/IEC 18013-5:2021 mdoc MSO",
            usageCount = mdocCredential.usageCount,
            signedAt = mso.signed,
            validFrom = mso.validFrom,
            validUntil = mso.validUntil,
            expectedUpdate = mso.expectedUpdate,
            replacementPending = mdocCredential.replacement != null,
            details = kvPairs
        )
    }

    private fun createCardInfoForSdJwtVcCredential(sdJwtVcCredential: SdJwtVcCredential): CredentialInfo {

        val kvPairs = mutableMapOf<String, String>()

        val sdJwt = SdJwtVerifiableCredential.fromString(
            String(sdJwtVcCredential.issuerProvidedData, Charsets.US_ASCII)
        )
        val body = JwtBody.fromString(sdJwt.body)

        kvPairs.put("Issuer", body.issuer)
        kvPairs.put("Document Type", body.docType)
        kvPairs.put("Digest Hash Algorithm", body.sdHashAlg.toString())

        addSecureAreaBoundCredentialInfo(sdJwtVcCredential, kvPairs)

        kvPairs.put("Issuer Provided Data", "${sdJwtVcCredential.issuerProvidedData.size} bytes")

        return CredentialInfo(
            format = CredentialFormat.SD_JWT_VC,
            description = "IETF SD-JWT Verifiable Credential",
            usageCount = sdJwtVcCredential.usageCount,
            signedAt = body.timeSigned,
            validFrom = body.timeValidityBegin,
            validUntil = body.timeValidityEnd,
            expectedUpdate = null,
            replacementPending = sdJwtVcCredential.replacement != null,
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
            val issuingAuthorityIdSet = documentStore.listDocuments().mapNotNull { documentId ->
                documentStore.lookupDocument(documentId)?.issuingAuthorityIdentifier
            }.toMutableSet()
            for (issuingAuthorityId in issuingAuthorityIdSet) {
                startListeningForNotifications(issuingAuthorityId)
            }

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
                                if (!issuingAuthorityIdSet.contains(document.issuingAuthorityIdentifier)) {
                                    issuingAuthorityIdSet.add(document.issuingAuthorityIdentifier)
                                    startListeningForNotifications(document.issuingAuthorityIdentifier)
                                }
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
        for (documentId in documentStore.listDocuments()) {
            documentStore.lookupDocument(documentId)?.let {
                addDocument(it)
            }
        }
        updateCredman()
    }

    // This processes events from an Issuing Authority which is a stream of events
    // for when an issuer notifies that one of their documents has an update.
    //
    // For every event, we  initiate a sequence of calls into the issuer to get the latest.
    private fun startListeningForNotifications(issuingAuthorityId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val issuingAuthority = walletServerProvider.getIssuingAuthority(issuingAuthorityId)
            Logger.i(TAG, "collecting notifications for $issuingAuthorityId...")
            issuingAuthority.notifications.collect { notification ->
                Logger.i(
                    TAG,
                    "received notification $issuingAuthorityId.${notification.documentId}"
                )
                // Find the local [Document] instance, if any
                for (id in documentStore.listDocuments()) {
                    val document = documentStore.lookupDocument(id)
                    if (document?.issuingAuthorityIdentifier == issuingAuthorityId &&
                        document.documentIdentifier == notification.documentId
                    ) {
                        Logger.i(TAG, "Handling issuer update on ${notification.documentId}")
                        try {
                            syncDocumentWithIssuer(document)
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
     * requesting credentials, checking for PII updates, and so on.
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
     * @throws IllegalArgumentException if the issuer isn't known.
     */
    private suspend fun syncDocumentWithIssuer(
        document: Document
    ) {
        Logger.i(TAG, "syncDocumentWithIssuer: Refreshing ${document.name}")

        val issuer = walletServerProvider.getIssuingAuthority(document.issuingAuthorityIdentifier)

        // Download latest issuer configuration.
        document.issuingAuthorityConfiguration = issuer.getConfiguration()

        // OK, let's see what's new...
        document.refreshState(walletServerProvider)

        // It's possible the document was remote deleted...
        if (document.state.condition == DocumentCondition.NO_SUCH_DOCUMENT) {
            Logger.i(TAG, "syncDocumentWithIssuer: ${document.name} was deleted")
            walletApplication.postNotificationForDocument(
                document,
                walletApplication.applicationContext.getString(
                    R.string.notifications_document_deleted_by_issuer
                )
            )
            documentStore.deleteDocument(document.name)
            return
        }

        // It's possible a new configuration is available...
        if (document.state.condition == DocumentCondition.CONFIGURATION_AVAILABLE) {
            Logger.i(TAG, "syncDocumentWithIssuer: ${document.name} has a new configuration")

            // New configuration (= PII) is available, nuke all existing credentials
            //
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

            document.refreshState(walletServerProvider)
        }

        // If the document is in the READY state, we can request credentials. See if we need
        // to do that.
        if (document.state.condition == DocumentCondition.READY) {
            val docConf = document.documentConfiguration

            if (docConf.mdocConfiguration != null) {
                refreshCredentials(
                    issuer,
                    document,
                    WalletApplication.CREDENTIAL_DOMAIN_MDOC,
                    CredentialFormat.MDOC_MSO
                ) { credentialToReplace, credentialDomain, secureArea, createKeySettings ->
                    MdocCredential(
                        document,
                        credentialToReplace,
                        credentialDomain,
                        secureArea,
                        createKeySettings,
                        docConf.mdocConfiguration!!.docType,
                    )
                }
            }

            if (docConf.sdJwtVcDocumentConfiguration != null) {
                refreshCredentials(
                    issuer,
                    document,
                    WalletApplication.CREDENTIAL_DOMAIN_SD_JWT_VC,
                    CredentialFormat.SD_JWT_VC
                ) { credentialToReplace, credentialDomain, secureArea, createKeySettings ->
                    SdJwtVcCredential(
                        document,
                        credentialToReplace,
                        credentialDomain,
                        secureArea,
                        createKeySettings,
                        docConf.sdJwtVcDocumentConfiguration!!.vct,
                    )
                }
            }
        }

        // It's possible the request credentials have already been minted..
        document.refreshState(walletServerProvider)

        // ... if so, download them.
        var numCredentialsRefreshed = 0
        if (document.state.numAvailableCredentials > 0) {
            for (credentialData in issuer.getCredentials(document.documentIdentifier)) {
                val pendingCredential = document.pendingCredentials.find {
                    (it as SecureAreaBoundCredential).attestation.certificates.first()
                        .publicKey.equals(credentialData.secureAreaBoundKey)
                }
                if (pendingCredential == null) {
                    Logger.w(
                        TAG,
                        "No pending Credential for pubkey ${credentialData.secureAreaBoundKey}"
                    )
                    continue
                }
                pendingCredential.certify(
                    credentialData.data,
                    credentialData.validFrom,
                    credentialData.validUntil
                )
                numCredentialsRefreshed += 1
            }
            document.refreshState(walletServerProvider)
        }
    }

    private suspend fun refreshCredentials(
        issuer: IssuingAuthority,
        document: Document,
        credentialDomain: String,
        credentialFormat: CredentialFormat,
        createCredential: ((
            credentialToReplace: Credential?,
            credentialDomain: String,
            secureArea: SecureArea,
            createKeySettings: CreateKeySettings,
        ) -> Credential)
    ) {
        // TODO: this should all come from issuer configuration
        val numCreds = 3
        val minValidTimeMillis = 30 * 24 * 3600L

        val now = Clock.System.now()
        // First do a dry-run to see how many pending credentials will be created
        val numPendingCredentialsToCreate = DocumentUtil.managedCredentialHelper(
            document,
            credentialDomain,
            null,
            now,
            numCreds,
            1,
            minValidTimeMillis,
            true
        )
        if (numPendingCredentialsToCreate > 0) {
            val requestCredentialsFlow = issuer.requestCredentials(document.documentIdentifier)
            val credConfig =
                requestCredentialsFlow.getCredentialConfiguration(credentialFormat.toString())

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
                credentialDomain,
                { credentialToReplace ->
                    createCredential(
                        credentialToReplace,
                        credentialDomain,
                        secureArea,
                        authKeySettings,
                    )
                },
                now,
                numCreds,
                1,
                minValidTimeMillis,
                false
            )
            val credentialRequests = mutableListOf<CredentialRequest>()
            for (pendingCredential in document.pendingCredentials) {
                credentialRequests.add(
                    CredentialRequest(
                        (pendingCredential as SecureAreaBoundCredential).attestation
                    )
                )
            }
            requestCredentialsFlow.sendCredentials(credentialRequests)
            requestCredentialsFlow.complete()  // noop for local, but important for server-side IA
        }
    }
}

private class TimedChunkFlow<T>(sourceFlow: Flow<T>, period: Duration) {
    private val chunkLock = ReentrantLock()
    private var chunk = mutableListOf<T>()

    @OptIn(FlowPreview::class)
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

fun <T> Flow<T>.timedChunk(periodMs: Duration): Flow<List<T>> =
    TimedChunkFlow(this, periodMs).resultFlow
