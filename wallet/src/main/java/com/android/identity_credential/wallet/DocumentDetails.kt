package com.android.identity_credential.wallet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.android.identity.cbor.Cbor
import com.android.identity.credential.Credential
import com.android.identity.credentialtype.CredentialAttributeType
import com.android.identity.credentialtype.CredentialTypeRepository
import com.android.identity.credentialtype.MdocCredentialType
import com.android.identity.credentialtype.MdocDataElement
import com.android.identity.credentialtype.knowntypes.DrivingLicense
import com.android.identity.mdoc.mso.MobileSecurityObjectParser
import com.android.identity.mdoc.mso.StaticAuthDataParser
import com.android.identity.util.Logger

private const val TAG = "ViewCredentialData"

/**
 * A class containing human-readable information (mainly PII) about a document.
 *
 * This data is intended to be display to the user, not used in presentations
 * or sent to external parties.
 *
 * @param typeName human readable type name of the document, e.g. "Driving License".
 * @param portrait portrait of the holder, if available
 * @param signatureOrUsualMark signature or usual mark of the holder, if available
 * @param attributes key/value pairs with data in the document
 */
data class DocumentDetails(
    val typeName: String,
    val portrait: Bitmap?,
    val signatureOrUsualMark: Bitmap?,
    val attributes: Map<String, String>
)

// TODO: Maybe move to MdocDataElement
private fun renderMdocDataElement(mdocDataElement: MdocDataElement?, value: ByteArray): String {
    val item = Cbor.decode(value)
    try {
        return when (mdocDataElement?.attribute?.type) {
            is CredentialAttributeType.String -> item.asTstr
            /* is CredentialAttributeType.DATE -> TODO */
            is CredentialAttributeType.DateTime -> item.asDateTimeString.toString()

            is CredentialAttributeType.Number -> item.asNumber.toString()
            is CredentialAttributeType.Picture -> "${value.size} bytes"
            is CredentialAttributeType.Boolean -> item.asBoolean.toString()
            is CredentialAttributeType.ComplexType -> Cbor.toDiagnostics(value)
            is CredentialAttributeType.StringOptions -> {
                val key = item.asTstr
                val options =
                    (mdocDataElement.attribute.type as CredentialAttributeType.StringOptions).options
                return options.find { it.value.equals(key) }?.displayName ?: key
            }

            is CredentialAttributeType.IntegerOptions -> {
                val key = item.asNumber
                val options =
                    (mdocDataElement.attribute.type as CredentialAttributeType.IntegerOptions).options
                return options.find { it.value?.toLong() == key }?.displayName ?: key.toString()
            }

            else -> Cbor.toDiagnostics(value)
        }
    } catch (e: Exception) {
        Logger.w(TAG, "Error rendering data element $item", e)
        return Cbor.toDiagnostics(item)
    }
}


private data class VisitNamespaceResult(
    val portrait: ByteArray?,
    val signatureOrUsualMark: ByteArray?,
    val keysAndValues: Map<String, String>
)

private fun visitNamespace(
    mdocCredentialType: MdocCredentialType?,
    namespaceName: String,
    listOfEncodedIssuerSignedItemBytes: List<ByteArray>,
): VisitNamespaceResult {
    var portrait: ByteArray? = null
    var signatureOrUsualMark: ByteArray? = null
    val keysAndValues: MutableMap<String, String> = LinkedHashMap()
    for (encodedIssuerSignedItemBytes in listOfEncodedIssuerSignedItemBytes) {
        val issuerSignedItemBytes = Cbor.decode(encodedIssuerSignedItemBytes)
        val issuerSignedItem = issuerSignedItemBytes.asTaggedEncodedCbor
        val elementIdentifier = issuerSignedItem["elementIdentifier"].asTstr
        val elementValue = issuerSignedItem["elementValue"]
        val encodedElementValue = Cbor.encode(elementValue)

        val mdocDataElement = mdocCredentialType?.namespaces?.get(namespaceName)?.dataElements?.get(elementIdentifier)

        if (mdocDataElement != null &&
            mdocDataElement.attribute.type == CredentialAttributeType.Picture &&
            namespaceName == DrivingLicense.MDL_NAMESPACE) {
            when (mdocDataElement.attribute.identifier) {
                "portrait" -> {
                    portrait = elementValue.asBstr
                    continue
                }
                "signature_usual_mark" -> {
                    signatureOrUsualMark = elementValue.asBstr
                    continue
                }
            }
        }

        val elementValueAsString = renderMdocDataElement(mdocDataElement, encodedElementValue)
        val elementName = mdocDataElement?.attribute?.displayName ?: elementIdentifier
        keysAndValues[elementName] = elementValueAsString
    }
    return VisitNamespaceResult(portrait, signatureOrUsualMark, keysAndValues)
}

fun Credential.getUserVisibleDetails(
    credentialTypeRepository: CredentialTypeRepository
): DocumentDetails {
    if (authenticationKeys.size == 0) {
        return DocumentDetails("Unknown", null, null, mapOf())
    }
    val authKey = authenticationKeys[0]

    var portrait: Bitmap? = null
    var signatureOrUsualMark: Bitmap? = null

    val credentialData = StaticAuthDataParser(authKey.issuerProvidedData).parse()
    val issuerAuthCoseSign1 = Cbor.decode(credentialData.issuerAuth).asCoseSign1
    val encodedMsoBytes = Cbor.decode(issuerAuthCoseSign1.payload!!)
    val encodedMso = Cbor.encode(encodedMsoBytes.asTaggedEncodedCbor)

    val mso = MobileSecurityObjectParser(encodedMso).parse()

    val credentialType = credentialTypeRepository.getCredentialTypeForMdoc(mso.docType)
    val kvPairs = mutableMapOf<String, String>()
    for (namespaceName in mso.valueDigestNamespaces) {
        val digestIdMapping = credentialData.digestIdMapping[namespaceName] ?: continue
        val result = visitNamespace(credentialType?.mdocCredentialType, namespaceName, digestIdMapping)
        if (result.portrait != null) {
            portrait = BitmapFactory.decodeByteArray(
                result.portrait,
                0,
                result.portrait.size
            )
        }
        if (result.signatureOrUsualMark != null) {
            signatureOrUsualMark = BitmapFactory.decodeByteArray(
                result.signatureOrUsualMark,
                0,
                result.signatureOrUsualMark.size
            )
        }
        kvPairs += result.keysAndValues
    }

    val typeName = credentialType?.displayName ?: mso.docType
    return DocumentDetails(typeName, portrait, signatureOrUsualMark, kvPairs)
}
