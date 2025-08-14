package org.multipaz.models.openid.dcql

import org.multipaz.claim.Claim
import org.multipaz.credential.Credential
import org.multipaz.presentment.CredentialPresentmentSetOptionMemberMatch
import org.multipaz.request.RequestedClaim

/**
 * A presentment of a particular [Credential] from [org.multipaz.document.DocumentStore].
 */
data class DcqlResponseCredentialSetOptionMemberMatch(
    override val credential: Credential,
    override val claims: Map<RequestedClaim, Claim>,

    /**
     * The [DcqlCredentialQuery] which was used for this match.
     */
    val credentialQuery: DcqlCredentialQuery
): CredentialPresentmentSetOptionMemberMatch