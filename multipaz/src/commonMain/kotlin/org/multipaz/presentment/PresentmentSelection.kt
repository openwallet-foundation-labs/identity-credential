package org.multipaz.presentment

/**
 * The result of selecting from a list of [PresentmentCredentialSet].
 *
 */
data class PresentmentSelection(
    val matches: List<PresentmentCredentialSetOptionMemberMatch>
)