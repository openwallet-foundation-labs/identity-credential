package com.android.identity.appsupport.ui.digitalcredentials

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.DataItem
import com.android.identity.claim.organizeByNamespace
import com.android.identity.context.applicationContext
import com.android.identity.document.Document
import com.android.identity.document.DocumentStore
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.sdjwt.credential.SdJwtVcCredential
import com.android.identity.util.Logger
import com.google.android.gms.identitycredentials.IdentityCredentialManager
import com.google.android.gms.identitycredentials.RegistrationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import java.io.ByteArrayOutputStream

private const val TAG = "DigitalCredentials"

private class RegistrationData (
    val documentStore: DocumentStore,
    val documentTypeRepository: DocumentTypeRepository,
    val listeningJob: Job,
)

private val exportedStores = mutableMapOf<DocumentStore, RegistrationData>()

private fun getClaimDisplayName(
    documentTypeRepository: DocumentTypeRepository,
    vct: String,
    claimName: String
): String {
    val documentType = documentTypeRepository.getDocumentTypeForVc(vct)
    if (documentType != null) {
        val attr = documentType.vcDocumentType?.claims?.get(claimName)
        if (attr != null) {
            return attr.displayName
        }
    }
    return claimName
}

private fun getDataElementDisplayName(
    documentTypeRepository: DocumentTypeRepository,
    docTypeName: String,
    nameSpaceName: String,
    dataElementName: String
): String {
    val documentType = documentTypeRepository.getDocumentTypeForMdoc(docTypeName)
    if (documentType != null) {
        val mdocDataElement = documentType.mdocDocumentType!!
            .namespaces[nameSpaceName]?.dataElements?.get(dataElementName)
        if (mdocDataElement != null) {
            return mdocDataElement.attribute.displayName
        }
    }
    return dataElementName
}

private suspend fun updateCredman() {
    val appInfo = applicationContext.applicationInfo
    val appName = if (appInfo.labelRes != 0) {
        applicationContext.getString(appInfo.labelRes)
    } else {
        appInfo.nonLocalizedLabel.toString()
    }

    val docsBuilder = CborArray.builder()

    for ((_, regData) in exportedStores) {
        for (documentId in regData.documentStore.listDocuments()) {
            val document = regData.documentStore.lookupDocument(documentId) ?: continue

            val mdocCredential = document.getCertifiedCredentials().find { it is MdocCredential }
            if (mdocCredential != null) {
                docsBuilder.add(
                    exportMdocCredential(
                        appName = appName,
                        document = document,
                        credential = mdocCredential as MdocCredential,
                        documentTypeRepository = regData.documentTypeRepository
                    )
                )
            }

            val sdJwtVcCredential = document.getCertifiedCredentials().find { it is SdJwtVcCredential }
            if (sdJwtVcCredential != null) {
                docsBuilder.add(
                    exportSdJwtVcCredential(
                        appName = appName,
                        document = document,
                        credential = sdJwtVcCredential as SdJwtVcCredential,
                        documentTypeRepository = regData.documentTypeRepository
                    )
                )
            }
        }
    }

    val credentialsCbor = Cbor.encode(docsBuilder.end().build())
    //Logger.iCbor(TAG, "credentialsCbor", credentialsCbor)
    val client = IdentityCredentialManager.getClient(applicationContext)
    client.registerCredentials(
        RegistrationRequest(
            credentials = credentialsCbor,
            matcher = loadMatcher(applicationContext),
            type = "com.credman.IdentityCredential",
            requestType = "",
            protocolTypes = emptyList(),
        )
    )
        .addOnSuccessListener { Logger.i(TAG, "CredMan registry succeeded") }
        .addOnFailureListener { Logger.i(TAG, "CredMan registry failed $it") }
}

private suspend fun exportMdocCredential(
    appName: String,
    document: Document,
    credential: MdocCredential,
    documentTypeRepository: DocumentTypeRepository,
): DataItem {
    val credentialType = documentTypeRepository.getDocumentTypeForMdoc(credential.docType)

    val documentMetadata = document.metadata
    val cardArt = documentMetadata.cardArt!!.toByteArray()
    val displayName = documentMetadata.displayName!!

    val options = BitmapFactory.Options()
    options.inMutable = true
    val credBitmap = BitmapFactory.decodeByteArray(
        cardArt,
        0,
        cardArt.size,
        options
    )
    val scaledIcon = Bitmap.createScaledBitmap(credBitmap, 128, 128, true)
    val stream = ByteArrayOutputStream()
    scaledIcon.compress(Bitmap.CompressFormat.PNG, 100, stream)
    val cardArtResized = stream.toByteArray()
    Logger.i(TAG, "Resized cardart to 128x128, ${cardArt.size} bytes to ${cardArtResized.size} bytes")

    val docBuilder = CborMap.builder()
    docBuilder.put("title", displayName)
    docBuilder.put("subtitle", appName)
    docBuilder.put("bitmap", cardArtResized)
    val mdocBuilder = CborMap.builder()
    mdocBuilder.put("id", document.identifier)
    mdocBuilder.put("docType", credential.docType)

    val mdocNsMapBuilder = mdocBuilder.putMap("namespaces")

    val claims = credential.getClaims(documentTypeRepository)
    for ((namespace, claimsInNamespace) in claims.organizeByNamespace()) {
        val mdocNsBuilder = mdocNsMapBuilder.putMap(namespace)
        for (claim in claimsInNamespace) {
            val mdocDataElement = credentialType?.mdocDocumentType?.namespaces
                ?.get(namespace)?.dataElements?.get(claim.dataElementName)
            val valueString = mdocDataElement
                ?.renderValue(claim.value)
                ?: Cbor.toDiagnostics(claim.value)

            val dataElementDisplayName = getDataElementDisplayName(
                documentTypeRepository,
                credential.docType,
                claim.namespaceName,
                claim.dataElementName
            )

            val dataElementBuilder = mdocNsBuilder.putArray(claim.dataElementName)
            dataElementBuilder.add(dataElementDisplayName)
            dataElementBuilder.add(valueString)
        }
        mdocNsBuilder.end()
    }
    docBuilder.put("mdoc", mdocBuilder.end().build())
    return docBuilder.end().build()
}

private suspend fun exportSdJwtVcCredential(
    appName: String,
    document: Document,
    credential: SdJwtVcCredential,
    documentTypeRepository: DocumentTypeRepository,
): DataItem {
    val credentialType = documentTypeRepository.getDocumentTypeForVc(credential.vct)

    val documentMetadata = document.metadata
    val cardArt = documentMetadata.cardArt!!.toByteArray()
    val displayName = documentMetadata.displayName!!

    val options = BitmapFactory.Options()
    options.inMutable = true
    val credBitmap = BitmapFactory.decodeByteArray(
        cardArt,
        0,
        cardArt.size,
        options
    )
    val scaledIcon = Bitmap.createScaledBitmap(credBitmap, 128, 128, true)
    val stream = ByteArrayOutputStream()
    scaledIcon.compress(Bitmap.CompressFormat.PNG, 100, stream)
    val cardArtResized = stream.toByteArray()
    Logger.i(TAG, "Resized cardart to 128x128, ${cardArt.size} bytes to ${cardArtResized.size} bytes")

    val docBuilder = CborMap.builder()
    docBuilder.put("title", displayName)
    docBuilder.put("subtitle", appName)
    docBuilder.put("bitmap", cardArtResized)
    val vcBuilder = CborMap.builder()
    vcBuilder.put("id", document.identifier)
    vcBuilder.put("vct", credential.vct)

    val claimsBuilder = vcBuilder.putMap("claims")
    val claims = credential.getClaimsImpl(documentTypeRepository)
    for (claim in claims) {
        val valueString = claim.render()

        val claimDisplayName = getClaimDisplayName(
            documentTypeRepository,
            credential.vct,
            claim.claimName,
        )

        val arrayBuilder = claimsBuilder.putArray(claim.claimName)
        arrayBuilder.add(claimDisplayName)
        arrayBuilder.add(valueString)
    }
    docBuilder.put("sdjwt", vcBuilder.end().build())
    return docBuilder.end().build()
}

private fun loadMatcher(context: Context): ByteArray {
    val stream = context.assets.open("identitycredentialmatcher.wasm");
    val matcher = ByteArray(stream.available())
    stream.read(matcher)
    stream.close()
    return matcher
}

internal actual val defaultAvailable = true

@OptIn(FlowPreview::class)
internal actual suspend fun defaultStartExportingCredentials(
    documentStore: DocumentStore,
    documentTypeRepository: DocumentTypeRepository,
) {
    val listeningJob = CoroutineScope(Dispatchers.IO).launch {
        documentStore.eventFlow
            .onEach { event ->
                Logger.i(TAG, "DocumentStore event ${event::class.simpleName} ${event.documentId}")
                try {
                    updateCredman()
                } catch (e: Throwable) {
                    currentCoroutineContext().ensureActive()
                    Logger.w(TAG, "Exception while updating Credman", e)
                    e.printStackTrace()
                }
            }
    }
    exportedStores.put(documentStore, RegistrationData(
        documentStore = documentStore,
        documentTypeRepository = documentTypeRepository,
        listeningJob = listeningJob,
    ))
    updateCredman()

    // To avoid continually updating Credman when documents are added one after the other, sample
    // only every 10 seconds.
    documentStore.eventFlow
        .sample(10.seconds)
        .onEach { event ->
            Logger.i(TAG, "DocumentStore event ${event::class.simpleName} ${event.documentId}")
            updateCredman()
        }
        .launchIn(CoroutineScope(Dispatchers.IO))
}

internal actual suspend fun defaultStopExportingCredentials(
    documentStore: DocumentStore,
) {
    val registrationData = exportedStores.remove(documentStore)
    if (registrationData == null) {
        return
    }
    registrationData.listeningJob.cancel()
    updateCredman()
}

suspend fun DocumentStore.lookupForCredmanId(credManId: String): Document? {
    return lookupDocument(credManId)
}

