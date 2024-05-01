package com.android.identity.issuance

/**
 * An enumeration of Credential Formats that an issuer may support.
 */
enum class CredentialFormat {
    /**
     * This CredentialFormat for mdoc as defined in
     * [ISO/IEC 18013-5:2021](https://www.iso.org/standard/69084.html).
     *
     * For this format, the [CredentialData.data]
     * contains CBOR conforming to the follow CDDL:
     * ```
     * StaticAuthData = {
     *   "digestIdMapping": DigestIdMapping,
     *   "issuerAuth" : IssuerAuth
     * }
     *
     * DigestIdMapping = {
     *   NameSpace =&gt; [ + IssuerSignedItemMetadataBytes ]
     * }
     *
     * IssuerSignedItemMetadataBytes = #6.24(bstr .cbor IssuerSignedItemMetadata)
     *
     * IssuerSignedItemMetadata = {
     *   "digestID" : uint,                           ; Digest ID for issuer data auth
     *   "random" : bstr,                             ; Random value for issuer data auth
     *   "elementIdentifier" : DataElementIdentifier, ; Data element identifier
     *   "elementValue" : DataElementValueOrNull      ; Placeholder for Data element value
     * }
     *
     * ; Set to null to use value previously provisioned or non-null
     * ; to use a per-MSO value
     * ;
     * DataElementValueOrNull = null // DataElementValue   ; "//" means or in CDDL
     *
     * ; Defined in ISO 18013-5
     * ;
     * NameSpace = String
     * DataElementIdentifier = String
     * DataElementValue = any
     * DigestID = uint
     * IssuerAuth = COSE_Sign1 ; The payload is MobileSecurityObjectBytes
     * ```
     */
    MDOC_MSO,
}
