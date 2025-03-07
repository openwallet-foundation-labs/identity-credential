/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.multipaz.mdoc.credential

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborBuilder
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.MapBuilder
import org.multipaz.credential.SecureAreaBoundCredential
import org.multipaz.document.Document
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.issuersigned.IssuerNamespaces
import org.multipaz.claim.Claim
import org.multipaz.claim.MdocClaim
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.SecureArea
import org.multipaz.util.Logger
import kotlinx.datetime.Instant

/**
 * An mdoc credential, according to [ISO/IEC 18013-5:2021](https://www.iso.org/standard/69084.html).
 *
 * In this type, the key in [SecureAreaBoundCredential] plays the role of `DeviceKey` and the
 * issuer-signed data includes the `Mobile Security Object` which includes the authentication
 * key and is signed by the issuer. This is used for anti-cloning and to return data signed
 * by the device.
 *
 * The [issuerProvidedData] for a [MdocCredential] must be the bytes of `IssuerSigned` according
 * to ISO/IEC 18013-5:2021:
 * ```
 * IssuerSigned = {
 *   ? "nameSpaces" : IssuerNameSpaces,
 *   "issuerAuth" : IssuerAuth
 * }
 * ```
 */
class MdocCredential : SecureAreaBoundCredential {
    companion object {
        private const val TAG = "MdocCredential"

        suspend fun create(
            document: Document,
            asReplacementForIdentifier: String?,
            domain: String,
            secureArea: SecureArea,
            docType: String,
            createKeySettings: CreateKeySettings
        ): MdocCredential {
            return MdocCredential(
                document,
                asReplacementForIdentifier,
                domain,
                secureArea,
                docType
            ).apply {
                generateKey(createKeySettings)
            }
        }
    }

    /**
     * Constructs a new [MdocCredential].
     *
     * [SecureAreaBoundCredential.generateKey] providing [CreateKeySettings] must be called before using
     * this object.
     *
     * @param document the document to add the credential to.
     * @param asReplacementFor the credential this credential will replace, if not null
     * @param domain the domain of the credential
     * @param secureArea the secure area for the authentication key associated with this credential.
     * @param docType the docType of the credential
     *
     * [SecureAreaBoundCredential.generateKey] must be called before using this object.
     */
    private constructor(
        document: Document,
        asReplacementForIdentifier: String?,
        domain: String,
        secureArea: SecureArea,
        docType: String
    ) : super(document, asReplacementForIdentifier, domain, secureArea) {
        this.docType = docType
    }

    /**
     * Constructs a Credential from serialized data.
     *
     * [generateKey] providing actual serialized data must be called before using this object.
     *
     * @param document the [Document] that the credential belongs to.
     * @param dataItem the serialized data.
     */
    constructor(
        document: Document
    ) : super(document) {}

    override suspend fun deserialize(dataItem: DataItem) {
        super.deserialize(dataItem)
        docType = dataItem["docType"].asTstr
    }

    /**
     * The docType of the credential as defined in
     * [ISO/IEC 18013-5:2021](https://www.iso.org/standard/69084.html).
     */
    lateinit var docType: String
        private set

    override fun addSerializedData(builder: MapBuilder<CborBuilder>) {
        super.addSerializedData(builder)
        builder.put("docType", docType)
    }

    // Override certify() to check that the issuerProvidedData is of the right form.
    //
    override suspend fun certify(
        issuerProvidedAuthenticationData: ByteArray,
        validFrom: Instant,
        validUntil: Instant
    ) {
        val issuerSigned = Cbor.decode(issuerProvidedAuthenticationData)
        if (!issuerSigned.hasKey("nameSpaces")) {
            Logger.w(TAG, "issuerProvidedData doesn't have 'nameSpaces' - presentment will not work as expected")
        }
        if (!issuerSigned.hasKey("issuerAuth")) {
            Logger.w(TAG, "issuerProvidedData doesn't have 'issuerAuth' - presentment will not work as expected")
        }
        super.certify(issuerProvidedAuthenticationData, validFrom, validUntil)
    }

    override fun getClaims(
        documentTypeRepository: DocumentTypeRepository?
    ): List<MdocClaim> {
        val dt = documentTypeRepository?.getDocumentTypeForMdoc(docType)
        val issuerSigned = Cbor.decode(issuerProvidedData)
        val namespaces = issuerSigned.getOrNull("nameSpaces")
            ?: return emptyList()
        val ret = mutableListOf<MdocClaim>()
        for ((namespaceName, innerMap) in IssuerNamespaces.fromDataItem(namespaces).data) {
            for ((dataElementName, issuerSignedItem) in innerMap) {
                val mdocAttr = dt?.mdocDocumentType?.namespaces?.get(namespaceName)?.dataElements?.get(dataElementName)
                val claim = MdocClaim(
                    displayName = mdocAttr?.attribute?.displayName ?: dataElementName,
                    attribute = mdocAttr?.attribute,
                    namespaceName = namespaceName,
                    dataElementName = dataElementName,
                    value = issuerSignedItem.dataElementValue
                )
                ret.add(claim)
            }
        }
        return ret
    }
}
