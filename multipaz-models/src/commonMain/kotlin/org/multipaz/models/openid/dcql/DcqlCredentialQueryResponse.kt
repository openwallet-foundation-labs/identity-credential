package org.multipaz.models.openid.dcql

/**
 * A response to a [DcqlCredentialQuery].
 *
 * Because each Credential Query may be satisfied in multiple ways (since the user might have multiple
 * credentials of the same type), the [matches] list contains these options and the application should
 * return exactly one of the credentials in this list.
 *
 * An application may use this to present the user with a choice of which credential to return.
 *
 * @property credentialQuery the Credential Query that this response satisfies.
 * @property credentialSetQuery the Credential Set that was chosen to satisfy the response or `null`.
 * @property matches a list of different credentials that can be returned.
 */
data class DcqlCredentialQueryResponse(
    val credentialQuery: DcqlCredentialQuery,
    val credentialSetQuery: DcqlCredentialSetQuery?,

    val matches: List<DcqlCredentialQueryResponseMatch>
)
