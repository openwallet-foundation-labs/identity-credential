package org.multipaz.provision

/**
 * Describes a format of a credential.
 */
sealed class CredentialFormat {
    data class Mdoc(val docType: String) : CredentialFormat()
    data class SdJwt(val vct: String) : CredentialFormat()
}