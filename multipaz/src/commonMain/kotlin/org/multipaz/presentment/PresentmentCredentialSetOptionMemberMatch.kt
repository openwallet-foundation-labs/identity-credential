package org.multipaz.presentment

import org.multipaz.claim.Claim
import org.multipaz.credential.Credential
import org.multipaz.request.RequestedClaim

/**
 * A presentment of a particular credential.
 *
 * @property credential the [Credential] to present.
 * @property claims the values to present.
 */
interface PresentmentCredentialSetOptionMemberMatch {
    val credential: Credential
    val claims: Map<RequestedClaim, Claim>
}
