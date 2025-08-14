package org.multipaz.models.openid.dcql

import org.multipaz.presentment.PresentmentCredentialSetOption

/**
 * This corresponds to an option in a credential set.
 *
 * The application must return all members in the credential set option.
 *
 * @property members the members in a credential set, at least one.
 */
data class DcqlResponseCredentialSetOption(
    override val members: List<DcqlResponseCredentialSetOptionMember>
): PresentmentCredentialSetOption
