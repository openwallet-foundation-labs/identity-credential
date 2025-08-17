package org.multipaz.models.openid.dcql

import org.multipaz.presentment.CredentialPresentmentSet


/**
 * This corresponds to a DCQL credential set requested as part of a query.
 */
data class DcqlResponseCredentialSet(
    override val optional: Boolean,
    override val options: List<DcqlResponseCredentialSetOption>,
): CredentialPresentmentSet {

    internal fun consolidateSingleMemberOptions(): DcqlResponseCredentialSet {
        val nonSingleMemberOptions = mutableListOf<DcqlResponseCredentialSetOption>()
        val singleMemberMatches = mutableListOf<DcqlResponseCredentialSetOptionMemberMatch>()
        var numSingleMemberOptions = 0
        for (option in options) {
            if (option.members.size == 1) {
                singleMemberMatches.addAll(option.members[0].matches)
                numSingleMemberOptions += 1
            } else {
                nonSingleMemberOptions.add(option)
            }
        }

        if (numSingleMemberOptions > 1) {
            return DcqlResponseCredentialSet(
                optional = optional,
                options = listOf(
                    DcqlResponseCredentialSetOption(
                        members = listOf(DcqlResponseCredentialSetOptionMember(
                            matches = singleMemberMatches
                        ))
                    )) + nonSingleMemberOptions
            )
        } else {
            return this
        }
    }
}