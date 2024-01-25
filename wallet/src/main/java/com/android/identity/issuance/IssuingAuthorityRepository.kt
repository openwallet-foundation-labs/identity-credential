package com.android.identity.issuance

/**
 * A class that contains Issuing Authorities known to the application.
 */
class IssuingAuthorityRepository {
    private val issuingAuthorities: MutableList<IssuingAuthority> = mutableListOf()

    /**
     * Add a Issuing Authority to the repository.
     *
     * @param issuingAuthority the Issuing Authority to add.
     */
    fun add(issuingAuthority: IssuingAuthority) {
        issuingAuthorities.add(issuingAuthority)
    }

    /**
     * Get all the Issuing Authorities that are in the repository.
     *
     * @return all the [issuingAuthority] instances in the repository.
     */
    fun getIssuingAuthorities(): List<IssuingAuthority> {
        return issuingAuthorities
    }

    /**
     * Looks up an issuing authority by identifier.
     *
     * @return An [IssuingAuthority] or null if there is no issuer with the given identifier.
     */
    fun lookupIssuingAuthority(issuingAuthorityIdentifier: String): IssuingAuthority? {
        return issuingAuthorities.find {
            it.configuration.identifier.equals(issuingAuthorityIdentifier)
        }
    }
}
