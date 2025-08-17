package org.multipaz.presentment

import org.multipaz.claim.Claim
import org.multipaz.credential.Credential
import org.multipaz.request.RequestedClaim

/**
 * A presentment of a particular [Credential] from [org.multipaz.document.DocumentStore].
 *
 * @property credential the [Credential] to present.
 * @property claims the claims to present along with their request
 */
interface CredentialPresentmentSetOptionMemberMatch {
    val credential: Credential
    val claims: Map<RequestedClaim, Claim>
}
