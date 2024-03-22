package com.android.identity_credential.wallet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.DiagnosticOption
import com.android.identity.credential.Credential
import com.android.identity.credentialtype.CredentialAttributeType
import com.android.identity.credentialtype.CredentialTypeRepository
import com.android.identity.credentialtype.MdocCredentialType
import com.android.identity.credentialtype.knowntypes.DrivingLicense
import com.android.identity.mdoc.mso.MobileSecurityObjectParser
import com.android.identity.mdoc.mso.StaticAuthDataParser

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

private data class VisitNamespaceResult(
    val portrait: ByteArray?,
    val signatureOrUsualMark: ByteArray?,
    val keysAndValues: Map<String, String>
)

private fun visitNamespace(
    context: Context,
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

        var elementValueAsString: String? = null

        if (mdocDataElement != null) {
            elementValueAsString = mdocDataElement.renderValue(
                value = Cbor.decode(encodedElementValue),
                trueFalseStrings = Pair(
                    context.resources.getString(R.string.card_details_boolean_false_value),
                    context.resources.getString(R.string.card_details_boolean_true_value),
                )
            )

            if (mdocDataElement.attribute.type == CredentialAttributeType.Picture &&
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
        }

        if (elementValueAsString == null) {
            elementValueAsString = Cbor.toDiagnostics(
                encodedElementValue,
                setOf(DiagnosticOption.BSTR_PRINT_LENGTH)
            )
        }

        val elementName = mdocDataElement?.attribute?.displayName ?: elementIdentifier
        keysAndValues[elementName] = elementValueAsString
    }
    return VisitNamespaceResult(portrait, signatureOrUsualMark, keysAndValues)
}

fun Credential.renderDocumentDetails(
    context: Context,
    credentialTypeRepository: CredentialTypeRepository
): DocumentDetails {
    if (certifiedAuthenticationKeys.size == 0) {
        return DocumentDetails("Unknown", null, null, mapOf())
    }
    val authKey = certifiedAuthenticationKeys[0]

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
        val result = visitNamespace(
            context,
            credentialType?.mdocCredentialType,
            namespaceName,
            digestIdMapping
        )
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
