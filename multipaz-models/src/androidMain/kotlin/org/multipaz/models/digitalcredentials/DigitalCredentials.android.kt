package org.multipaz.models.digitalcredentials

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.DataItem
import org.multipaz.claim.organizeByNamespace
import org.multipaz.context.applicationContext
import org.multipaz.document.Document
import org.multipaz.document.DocumentStore
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.sdjwt.credential.SdJwtVcCredential
import org.multipaz.util.Logger
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.putCborArray
import org.multipaz.cbor.putCborMap
import org.multipaz.documenttype.DocumentAttribute
import kotlin.time.Duration.Companion.seconds
import java.io.ByteArrayOutputStream
import kotlin.collections.iterator

private const val TAG = "DigitalCredentials"

private class RegistrationData (
    val documentStore: DocumentStore,
    val documentTypeRepository: DocumentTypeRepository,
    val listeningJob: Job,
)

private val exportedStores = mutableMapOf<DocumentStore, RegistrationData>()

private fun getAttributeForJsonClaim(
    documentTypeRepository: DocumentTypeRepository,
    vct: String,
    path: JsonArray,
): DocumentAttribute? {
    val documentType = documentTypeRepository.getDocumentTypeForJson(vct)
    if (documentType != null) {
        val flattenedPath = path.joinToString(".") { it.jsonPrimitive.content }
        return documentType.jsonDocumentType?.claims?.get(flattenedPath)
    }
    return null
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

    val credentialDatabase = calculateCredentialDatabase(
        appName = appName,
        selectedProtocols = selectedProtocols,
        stores = exportedStores.values.map { Pair(it.documentStore, it.documentTypeRepository) }
    )

    val credentialDatabaseCbor = Cbor.encode(credentialDatabase)
    //Logger.iCbor(TAG, "credentialDatabaseCbor", credentialDatabaseCbor)
    val client = IdentityCredentialManager.getClient(applicationContext)
    client.registerCredentials(
        RegistrationRequest(
            credentials = credentialDatabaseCbor,
            matcher = loadMatcher(applicationContext),
            type = "com.credman.IdentityCredential",
            requestType = "",
            protocolTypes = emptyList(),
        )
    )
        .addOnSuccessListener { Logger.i(TAG, "CredMan registry succeeded (old)") }
        .addOnFailureListener { Logger.i(TAG, "CredMan registry failed  (old) $it") }
    client.registerCredentials(
        RegistrationRequest(
            credentials = credentialDatabaseCbor,
            matcher = loadMatcher(applicationContext),
            type = "androidx.credentials.TYPE_DIGITAL_CREDENTIAL",
            requestType = "",
            protocolTypes = emptyList(),
        )
    )
        .addOnSuccessListener { Logger.i(TAG, "CredMan registry succeeded") }
        .addOnFailureListener { Logger.i(TAG, "CredMan registry failed $it") }
}

internal suspend fun calculateCredentialDatabase(
    appName: String,
    selectedProtocols: Set<String>,
    stores: List<Pair<DocumentStore, DocumentTypeRepository>>
): DataItem {
    val credentialsBuilder = CborArray.builder()
    for ((documentStore, documentTypeRepository) in stores) {
        for (documentId in documentStore.listDocuments()) {
            val document = documentStore.lookupDocument(documentId) ?: continue

            val mdocCredential = document.getCertifiedCredentials().find { it is MdocCredential }
            if (mdocCredential != null) {
                credentialsBuilder.add(
                    exportMdocCredential(
                        appName = appName,
                        document = document,
                        credential = mdocCredential as MdocCredential,
                        documentTypeRepository = documentTypeRepository
                    )
                )
            }

            val sdJwtVcCredential = document.getCertifiedCredentials().find { it is SdJwtVcCredential }
            if (sdJwtVcCredential != null) {
                credentialsBuilder.add(
                    exportSdJwtVcCredential(
                        appName = appName,
                        document = document,
                        credential = sdJwtVcCredential as SdJwtVcCredential,
                        documentTypeRepository = documentTypeRepository
                    )
                )
            }
        }
    }

    val credentialDatabase = buildCborMap {
        putCborArray("protocols") { selectedProtocols.forEach { add(it) } }
        put("credentials", credentialsBuilder.end().build())
    }
    return credentialDatabase
}

private suspend fun exportMdocCredential(
    appName: String,
    document: Document,
    credential: MdocCredential,
    documentTypeRepository: DocumentTypeRepository
): DataItem {
    val credentialType = documentTypeRepository.getDocumentTypeForMdoc(credential.docType)

    val documentMetadata = document.metadata
    val cardArt = documentMetadata.cardArt?.toByteArray()
    val displayName = documentMetadata.displayName ?: "Unnamed Credential"

    val cardArtResized = cardArt?.let {
        val options = BitmapFactory.Options()
        options.inMutable = true
        val credBitmap = BitmapFactory.decodeByteArray(
            cardArt,
            0,
            cardArt.size,
            options
        )
        val scaledIcon = Bitmap.createScaledBitmap(credBitmap, 48, 48, true)
        val stream = ByteArrayOutputStream()
        scaledIcon.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val cardArtResized = stream.toByteArray()
        Logger.i(TAG, "Resized cardart to 48x48, ${cardArt.size} bytes to ${cardArtResized.size} bytes")
        cardArtResized
    }

    return buildCborMap {
        put("title", displayName)
        put("subtitle", appName)
        put("bitmap", cardArtResized ?: byteArrayOf())
        putCborMap("mdoc") {
            put("documentId", document.identifier)
            put("docType", credential.docType)
            putCborMap("namespaces") {
                val claims = credential.getClaims(documentTypeRepository)
                for ((namespace, claimsInNamespace) in claims.organizeByNamespace()) {
                    putCborMap(namespace) {
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
                            putCborArray(claim.dataElementName) {
                                add(dataElementDisplayName)
                                add(valueString)
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun exportSdJwtVcCredential(
    appName: String,
    document: Document,
    credential: SdJwtVcCredential,
    documentTypeRepository: DocumentTypeRepository
): DataItem {
    val documentMetadata = document.metadata
    val cardArt = documentMetadata.cardArt?.toByteArray()
    val displayName = documentMetadata.displayName ?: "Unnamed Credential"

    val cardArtResized = cardArt?.let {
        val options = BitmapFactory.Options()
        options.inMutable = true
        val credBitmap = BitmapFactory.decodeByteArray(
            cardArt,
            0,
            cardArt.size,
            options
        )
        val scaledIcon = Bitmap.createScaledBitmap(credBitmap, 48, 48, true)
        val stream = ByteArrayOutputStream()
        scaledIcon.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val cardArtResized = stream.toByteArray()
        Logger.i(TAG, "Resized cardart to 48x48, ${cardArt.size} bytes to ${cardArtResized.size} bytes")
        cardArtResized
    }

    return buildCborMap {
        put("title", displayName)
        put("subtitle", appName)
        put("bitmap", cardArtResized ?: byteArrayOf())
        putCborMap("sdjwt") {
            put("documentId", document.identifier)
            put("vct", credential.vct)
            putCborMap("claims") {
                val claims = credential.getClaimsImpl(documentTypeRepository)
                for (claim in claims) {
                    val claimName = claim.claimPath[0].jsonPrimitive.content
                    val claimDisplayName = getAttributeForJsonClaim(
                        documentTypeRepository,
                        credential.vct,
                        claim.claimPath,
                    )?.displayName ?: claimName
                    putCborArray(claimName) {
                        add(claimDisplayName)
                        add(claim.render())
                    }
                    // Our matcher currently combines paths to a single string, using `.` as separator. So do
                    // the same here for all subclaims... yes, we only support a single level of subclaims
                    // right now. In the future we'll modify the matcher to be smarter about things.
                    //
                    if (claim.value is JsonObject) {
                        for ((subClaimName, subClaimValue) in claim.value) {
                            val subClaimDisplayName = getAttributeForJsonClaim(
                                documentTypeRepository,
                                credential.vct,
                                JsonArray(listOf(JsonPrimitive(claimName), JsonPrimitive(subClaimName))),
                            )?.displayName ?: subClaimName
                            putCborArray("$claimName.$subClaimName") {
                                add(subClaimDisplayName)
                                add(subClaimValue.toString())
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun loadMatcher(context: Context): ByteArray {
    val stream = context.assets.open("identitycredentialmatcher.wasm");
    val matcher = ByteArray(stream.available())
    stream.read(matcher)
    stream.close()
    return matcher
}

internal actual val defaultAvailable = true

internal actual val defaultSupportedProtocols: Set<String>
    get() = supportedProtocols

private val supportedProtocols = setOf(
    "openid4vp-v1-signed",
    "openid4vp-v1-unsigned",
    "org-iso-mdoc",
    "openid4vp",
    "preview",
    "austroads-request-forwarding-v2",
)

internal actual val defaultSelectedProtocols: Set<String>
    get() = selectedProtocols

private var selectedProtocols = supportedProtocols

internal actual suspend fun defaultSetSelectedProtocols(
    protocols: Set<String>
) {
    selectedProtocols = protocols.mapNotNull {
        if (supportedProtocols.contains(it)) {
            it
        } else {
            Logger.w(TAG, "Protocol $it is not supported")
            null
        }
    }.toSet()
    updateCredman()
}

@OptIn(FlowPreview::class)
internal actual suspend fun defaultStartExportingCredentials(
    documentStore: DocumentStore,
    documentTypeRepository: DocumentTypeRepository
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

