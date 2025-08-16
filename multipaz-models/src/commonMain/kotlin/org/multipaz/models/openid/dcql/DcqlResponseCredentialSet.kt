package org.multipaz.models.openid.dcql

import org.multipaz.presentment.PresentmentCredentialSet


/**
 * This corresponds to a DCQL credential set requested as part of a query.
 *
 * The application must return exactly one of the elements from [options]. If [optional]
 * is `false` the application may choose to not return anything from this credential set.
 *
 * Note that [options] include both the possible options declared in the DCQL `credential_set`
 * but each combination appear multiple times if e.g. multiple credentials satisfy the condition.
 *
 * @property optional whether it's optional to return data from this credential set.
 * @property options available options to return.
 */
data class DcqlResponseCredentialSet(
    override val optional: Boolean,
    override val options: List<DcqlResponseCredentialSetOption>,
): PresentmentCredentialSet {

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