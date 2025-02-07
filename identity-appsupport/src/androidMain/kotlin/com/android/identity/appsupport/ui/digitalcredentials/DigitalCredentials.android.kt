package com.android.identity.appsupport.ui.digitalcredentials

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborMap
import com.android.identity.document.Document
import com.android.identity.document.DocumentStore
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.util.AndroidContexts
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

private fun getDataElementDisplayName(
    documentTypeRepository: DocumentTypeRepository,
    docTypeName: String,
    nameSpaceName: String,
    dataElementName: String
): String {
    val credType = documentTypeRepository.getDocumentTypeForMdoc(docTypeName)
    if (credType != null) {
        val mdocDataElement = credType.mdocDocumentType!!
            .namespaces[nameSpaceName]?.dataElements?.get(dataElementName)
        if (mdocDataElement != null) {
            return mdocDataElement.attribute.displayName
        }
    }
    return dataElementName
}

private suspend fun updateCredman() {
    val context = AndroidContexts.applicationContext
    val appInfo = context.applicationInfo
    val appName = if (appInfo.labelRes != 0) {
        context.getString(appInfo.labelRes)
    } else {
        appInfo.nonLocalizedLabel.toString()
    }

    val docsBuilder = CborArray.builder()

    for ((_, regData) in exportedStores) {
        for (documentId in regData.documentStore.listDocuments()) {
            val document = regData.documentStore.lookupDocument(documentId) ?: continue
            val mdocCredential = (document.getCertifiedCredentials().find { it is MdocCredential }
                ?: continue) as MdocCredential
            val credentialType =
                regData.documentTypeRepository.getDocumentTypeForMdoc(mdocCredential.docType)

            val documentMetadata = document.metadata
            val cardArt = documentMetadata.cardArt!!.toByteArray()
            val nameSpacedData = documentMetadata.nameSpacedData
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
            // NOTE: the contents of "id" is what we get back when Credman intents into to us on
            // credential selection, see CredmanPresentmentActivity.kt in samples/testapp which calls
            // DocumentStore.lookupForCredmanId() below...
            //
            mdocBuilder.put("id", documentId)
            mdocBuilder.put("docType", mdocCredential.docType)

            Logger.i(TAG, "exporting $displayName as $documentId")

            val mdocNsMapBuilder = mdocBuilder.putMap("namespaces")

            nameSpacedData.nameSpaceNames.map { nameSpaceName ->
                val mdocNsBuilder = mdocNsMapBuilder.putMap(nameSpaceName)
                nameSpacedData.getDataElementNames(nameSpaceName).map { dataElementName ->
                    val valueCbor = nameSpacedData.getDataElement(nameSpaceName, dataElementName)

                    val mdocDataElement = credentialType?.mdocDocumentType?.namespaces
                        ?.get(nameSpaceName)?.dataElements?.get(dataElementName)
                    val valueString = mdocDataElement
                        ?.renderValue(Cbor.decode(valueCbor))
                        ?: Cbor.toDiagnostics(valueCbor)

                    val dataElementDisplayName = getDataElementDisplayName(
                        regData.documentTypeRepository,
                        mdocCredential.docType,
                        nameSpaceName,
                        dataElementName
                    )

                    val dataElementBuilder = mdocNsBuilder.putArray(dataElementName)
                    dataElementBuilder.add(dataElementDisplayName)
                    dataElementBuilder.add(valueString)
                }
                mdocNsBuilder.end()
            }
            docBuilder.put("mdoc", mdocBuilder.end().build())
            docsBuilder.add(docBuilder.end().build())
        }
    }

    val credentialsCbor = Cbor.encode(docsBuilder.end().build())
    val client = IdentityCredentialManager.Companion.getClient(context)
    client.registerCredentials(
        RegistrationRequest(
            credentials = credentialsCbor,
            matcher = loadMatcher(context),
            type = "com.credman.IdentityCredential",
            requestType = "",
            protocolTypes = emptyList(),
        )
    )
        .addOnSuccessListener { Logger.i(TAG, "CredMan registry succeeded") }
        .addOnFailureListener { Logger.i(TAG, "CredMan registry failed $it") }
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

