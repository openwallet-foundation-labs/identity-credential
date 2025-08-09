package org.multipaz.models.openid.dcql

/**
 * DCQL credential set options.
 *
 * Reference: OpenID4VP 1.0 Section 6.4.2.
 *
 * @property credentialIds a list of credential IDs that can satisfy the request.
 */
data class DcqlCredentialSetOption(
    val credentialIds: List<String>
) {

    fun isSatisfied(responses: List<DcqlCredentialQueryResponse>): Boolean {
        for (id in credentialIds) {
            if (responses.find { it.credentialQuery.id == id && it.matches.isNotEmpty() } == null) {
                return false
            }
        }
        return true
    }
}
