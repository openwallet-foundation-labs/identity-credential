package com.android.identity.crypto

/**
 * Options for [Crypto.createX509v3Certificate] function.
 */
enum class CreateCertificateOption {
    /**
     * Include the Subject Key Identifier extension as per RFC 5280 section 4.2.1.2.
     *
     * The extension will be marked as non-critical.
     */
    INCLUDE_SUBJECT_KEY_IDENTIFIER,

    /**
     * Set the Authority Key Identifier with keyIdentifier set to the same value as the
     * Subject Key Identifier.
     *
     * This option is only meaningful when creating a self-signed certificate.
     *
     * The extension will be marked as non-critical.
     */
    INCLUDE_AUTHORITY_KEY_IDENTIFIER_AS_SUBJECT_KEY_IDENTIFIER,

    /**
     * Set the Authority Key Identifier with keyIdentifier set to the same value as the
     * Subject Key Identifier in the given `sigingKeyCertificate`.
     */
    INCLUDE_AUTHORITY_KEY_IDENTIFIER_FROM_SIGNING_KEY_CERTIFICATE,
}