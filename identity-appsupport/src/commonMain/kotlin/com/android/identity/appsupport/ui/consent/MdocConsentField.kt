package com.android.identity.appsupport.ui.consent

import com.android.identity.cbor.Cbor
import com.android.identity.documenttype.DocumentAttribute
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.mdoc.mso.StaticAuthDataParser
import com.android.identity.mdoc.request.DeviceRequestParser

/**
 * Consent field for mdoc credentials.
 *
 * @param displayName the name to display in the consent prompt.
 * @param namespaceName the mdoc namespace.
 * @param dataElementName the data element name.
 * @param intentToRetain the intentToRetain value.
 * @param attribute a [DocumentAttribute], if the data element is well-known.
 */
data class MdocConsentField(
    override val displayName: String,
    override val attribute: DocumentAttribute?,
    val namespaceName: String,
    val dataElementName: String,
    val intentToRetain: Boolean
) : ConsentField(displayName, attribute) {

    companion object {

        /**
         * Helper function to generate a list of entries for the consent prompt for mdoc.
         *
         * @param mdocDocRequest a [DeviceRequestParser.DocRequest] instance.
         * @param documentTypeRepository a [DocumentTypeRepository] used to determine the display name
         * @param mdocCredential if set, the returned list is filtered so it only references data
         * elements available in the credential.
         */
        fun generateConsentFields(
            mdocDocRequest: DeviceRequestParser.DocRequest,
            documentTypeRepository: DocumentTypeRepository,
            mdocCredential: MdocCredential?,
        ): List<MdocConsentField> {
            val requestedData = mutableMapOf<String, MutableList<Pair<String, Boolean>>>()
            for (namespaceName in mdocDocRequest.namespaces) {
                for (dataElementName in mdocDocRequest.getEntryNames(namespaceName)) {
                    val intentToRetain = mdocDocRequest.getIntentToRetain(namespaceName, dataElementName)
                    requestedData.getOrPut(namespaceName) { mutableListOf() }
                        .add(Pair(dataElementName, intentToRetain))
                }
            }
            return generateConsentFields(
                mdocDocRequest.docType,
                requestedData,
                documentTypeRepository,
                mdocCredential
            )
        }

        /**
         * Helper function to generate a list of entries for the consent prompt for mdoc.
         *
         * @param docType the mdoc document type.
         * @param requestedData a map from namespace into a list of data elements where each
         * pair is the data element name and whether the data element will be retained.
         * @param documentTypeRepository a [DocumentTypeRepository] used to determine the display name
         * @param mdocCredential if set, the returned list is filtered so it only references data
         * elements available in the credential.
         */
        fun generateConsentFields(
            docType: String,
            requestedData: Map<String, List<Pair<String, Boolean>>>,
            documentTypeRepository: DocumentTypeRepository,
            mdocCredential: MdocCredential?,
        ): List<MdocConsentField> {
            val mdocDocumentType = documentTypeRepository.getDocumentTypeForMdoc(docType)?.mdocDocumentType
            val ret = mutableListOf<MdocConsentField>()
            for ((namespaceName, listOfDe) in requestedData) {
                for ((dataElementName, intentToRetain) in listOfDe) {
                    val attribute =
                        mdocDocumentType?.namespaces
                            ?.get(namespaceName)
                            ?.dataElements
                            ?.get(dataElementName)
                            ?.attribute
                    ret.add(
                        MdocConsentField(
                            attribute?.displayName ?: dataElementName,
                            attribute,
                            namespaceName,
                            dataElementName,
                            intentToRetain
                        )
                    )
                }
            }
            return filterConsentFields(ret, mdocCredential)
        }

        private fun calcAvailableDataElements(
            issuerNameSpaces: Map<String, List<ByteArray>>
        ): Map<String, Set<String>> {
            val ret = mutableMapOf<String, Set<String>>()
            for (nameSpaceName in issuerNameSpaces.keys) {
                val innerSet = mutableSetOf<String>()
                for (encodedIssuerSignedItemBytes in issuerNameSpaces[nameSpaceName]!!) {
                    val issuerSignedItem = Cbor.decode(encodedIssuerSignedItemBytes).asTaggedEncodedCbor
                    val elementIdentifier = issuerSignedItem["elementIdentifier"].asTstr
                    innerSet.add(elementIdentifier)
                }
                ret[nameSpaceName] = innerSet
            }
            return ret
        }

        private fun filterConsentFields(
            list: List<MdocConsentField>,
            credential: MdocCredential?
        ): List<MdocConsentField> {
            if (credential == null) {
                return list
            }
            val staticAuthData = StaticAuthDataParser(credential.issuerProvidedData).parse()
            val availableDataElements = calcAvailableDataElements(staticAuthData.digestIdMapping)
            return list.filter { mdocConsentField ->
                availableDataElements[mdocConsentField.namespaceName]
                    ?.contains(mdocConsentField.dataElementName) != null
            }
        }
    }
}