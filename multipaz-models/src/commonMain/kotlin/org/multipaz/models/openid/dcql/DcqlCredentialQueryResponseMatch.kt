package org.multipaz.models.openid.dcql

import org.multipaz.claim.Claim
import org.multipaz.credential.Credential

/**
 * A credential that can be returned to the requester.
 *
 * @property credential the credential to return.
 * @property claims the claim values in the credential to return.
 */
data class DcqlCredentialQueryResponseMatch(
    val credential: Credential,
    val claims: List<Claim>
)
