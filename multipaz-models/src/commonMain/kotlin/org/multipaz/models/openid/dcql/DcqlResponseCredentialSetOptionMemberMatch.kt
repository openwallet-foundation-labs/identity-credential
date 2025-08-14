package org.multipaz.models.openid.dcql

import org.multipaz.claim.Claim
import org.multipaz.credential.Credential
import org.multipaz.presentment.PresentmentCredentialSetOptionMemberMatch
import org.multipaz.request.RequestedClaim

data class DcqlResponseCredentialSetOptionMemberMatch(
    override val credential: Credential,
    override val claims: Map<RequestedClaim, Claim>,
    val credentialQuery: DcqlCredentialQuery
): PresentmentCredentialSetOptionMemberMatch