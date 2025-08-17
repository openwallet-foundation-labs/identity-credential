package org.multipaz.models.openid.dcql

import org.multipaz.presentment.CredentialPresentmentSetOption

/**
 * This corresponds to an option in a credential set.
 */
data class DcqlResponseCredentialSetOption(
    override val members: List<DcqlResponseCredentialSetOptionMember>
): CredentialPresentmentSetOption
