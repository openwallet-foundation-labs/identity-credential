package org.multipaz.models.openid.dcql

import org.multipaz.presentment.PresentmentCredentialSetOptionMember

/**
 * A member of a credential set which can be returned.
 *
 * @property credentialQuery the query that was matched.
 * @property matches the set of distinct credentials which matched the query, at least one.
 */
class DcqlResponseCredentialSetOptionMember(
    override val matches: List<DcqlResponseCredentialSetOptionMemberMatch>,
): PresentmentCredentialSetOptionMember
