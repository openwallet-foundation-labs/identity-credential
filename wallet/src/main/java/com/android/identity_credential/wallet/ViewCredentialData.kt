package com.android.identity_credential.wallet

import com.android.identity.credential.Credential
import com.android.identity.credentialtype.CredentialAttributeType
import com.android.identity.credentialtype.CredentialTypeRepository
import com.android.identity.credentialtype.MdocCredentialType
import com.android.identity.credentialtype.MdocDataElement
import com.android.identity.credentialtype.knowntypes.DrivingLicense
import com.android.identity.internal.Util
import com.android.identity.mdoc.mso.MobileSecurityObjectParser
import com.android.identity.mdoc.mso.StaticAuthDataParser
import com.android.identity.util.CborUtil

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
    return when (mdocDataElement?.attribute?.type) {
        is CredentialAttributeType.STRING,
        is CredentialAttributeType.DATE,
        is CredentialAttributeType.DATE_TIME -> Util.cborDecodeString(value)

        is CredentialAttributeType.NUMBER -> Util.cborDecodeLong(value).toString()
        is CredentialAttributeType.PICTURE -> String.format("%d bytes", value.size)
        is CredentialAttributeType.BOOLEAN -> Util.cborDecodeBoolean(value).toString()
        is CredentialAttributeType.COMPLEX_TYPE -> CborUtil.toDiagnostics(value)
        is CredentialAttributeType.StringOptions -> {
            val key = Util.cborDecodeString(value)
            val options =
                (mdocDataElement.attribute.type as CredentialAttributeType.StringOptions).options
            return options.find { it.value.equals(key) }?.displayName?: key
        }

        is CredentialAttributeType.IntegerOptions -> {
            val key = Util.cborDecodeLong(value)
            val options =
                (mdocDataElement.attribute.type as CredentialAttributeType.IntegerOptions).options
            return options.find { it.value?.toLong() == key }?.displayName?: key.toString()
        }

        else -> CborUtil.toDiagnostics(value)
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
        val encodedIssuerSignedItem = Util.cborExtractTaggedCbor(encodedIssuerSignedItemBytes)
        val map = Util.cborDecode(encodedIssuerSignedItem)
        val elementIdentifier = Util.cborMapExtractString(map, "elementIdentifier")
        val elementValue = Util.cborMapExtract(map, "elementValue")
        val encodedElementValue = Util.cborEncode(elementValue)

        val mdocDataElement = mdocCredentialType?.namespaces?.get(namespaceName)?.dataElements?.get(elementIdentifier)

        if (mdocDataElement != null &&
            mdocDataElement.attribute.type == CredentialAttributeType.PICTURE &&
            namespaceName == DrivingLicense.MDL_NAMESPACE) {
            when (mdocDataElement.attribute.identifier) {
                "portrait" -> {
                    portrait = Util.cborDecodeByteString(encodedElementValue)
                    continue
                }
                "signature_usual_mark" -> {
                    signatureOrUsualMark = Util.cborDecodeByteString(encodedElementValue)
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
    val encodedMsoBytes = Util.cborDecode(Util.coseSign1GetData(Util.cborDecode(credentialData.issuerAuth)))
    val encodedMso = Util.cborEncode(Util.cborExtractTaggedAndEncodedCbor(encodedMsoBytes))
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
