package com.android.identity_credential.wallet

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.RawCbor
import com.android.identity.credential.Credential
import com.android.identity.credentialtype.CredentialAttributeType
import com.android.identity.credentialtype.CredentialTypeRepository
import com.android.identity.credentialtype.MdocCredentialType
import com.android.identity.credentialtype.MdocDataElement
import com.android.identity.credentialtype.knowntypes.DrivingLicense
import com.android.identity.mdoc.mso.MobileSecurityObjectParser
import com.android.identity.mdoc.mso.StaticAuthDataParser
import com.android.identity.util.Logger

const val TAG = "ViewCredentialData"

/**
 * A class containing information (mainly PII) about a credential.
 *
 * This data is intended to be display to the user, not used in presentations
 * or sent to external parties.
 */
data class ViewCredentialData(
    val portrait: ByteArray?,
    val signatureOrUsualMark: ByteArray?,
    val sections: List<ViewCredentialDataSection>
)

data class ViewCredentialDataSection(
    val keyValuePairs: Map<String, String>
)

// TODO: Maybe move to MdocDataElement
private fun renderMdocDataElement(mdocDataElement: MdocDataElement?, value: ByteArray): String {
    val item = Cbor.decode(value)
    try {
        return when (mdocDataElement?.attribute?.type) {
            is CredentialAttributeType.STRING -> item.asTstr
            /* is CredentialAttributeType.DATE -> TODO */
            is CredentialAttributeType.DATE_TIME -> item.asDateTimeString.toString()

            is CredentialAttributeType.NUMBER -> item.asNumber.toString()
            is CredentialAttributeType.PICTURE -> "${value.size} bytes"
            is CredentialAttributeType.BOOLEAN -> item.asBoolean.toString()
            is CredentialAttributeType.COMPLEX_TYPE -> Cbor.toDiagnostics(value)
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
            mdocDataElement.attribute.type == CredentialAttributeType.PICTURE &&
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

fun Credential.getViewCredentialData(
    credentialTypeRepository: CredentialTypeRepository
): ViewCredentialData {
    if (authenticationKeys.size == 0) {
        return ViewCredentialData(null, null, listOf())
    }
    val authKey = authenticationKeys[0]

    // TODO: add support for other credential formats than just MDOC.

    val sections = mutableListOf<ViewCredentialDataSection>()
    var portrait: ByteArray? = null
    var signatureOrUsualMark: ByteArray? = null

    val credentialData = StaticAuthDataParser(authKey.issuerProvidedData).parse()
    val issuerAuthCoseSign1 = Cbor.decode(credentialData.issuerAuth).asCoseSign1
    val encodedMsoBytes = Cbor.decode(issuerAuthCoseSign1.payload!!)
    val encodedMso = Cbor.encode(encodedMsoBytes.asTaggedEncodedCbor)

    val mso = MobileSecurityObjectParser().setMobileSecurityObject(encodedMso).parse()

    var mdocCredentialType = credentialTypeRepository.getMdocCredentialType(mso.docType)
    for (namespaceName in mso.valueDigestNamespaces) {
        val digestIdMapping = credentialData.digestIdMapping[namespaceName] ?: continue
        val result = visitNamespace(mdocCredentialType, namespaceName, digestIdMapping)
        if (result.portrait != null) {
            portrait = result.portrait
        }
        if (result.signatureOrUsualMark != null) {
            signatureOrUsualMark = result.signatureOrUsualMark
        }
        sections.add(ViewCredentialDataSection(result.keysAndValues))
    }

    return ViewCredentialData(portrait, signatureOrUsualMark, sections)
}
